# SPDX-License-Identifier: AGPL-3.0-or-later
"""Подбирает фото телефонов на Wikimedia Commons (только CC BY, CC BY-SA, CC0, общественное достояние).

Для каждой модели без фото: поиск на Commons -> проверка лицензии -> миниатюра -> data/images/<codename>.webp (до 600 px по высоте)
-> поле image { url, author, license, source } в data/devices/<codename>.json.
Файлы с manual != false (правленные людьми) трогаются только в поле image, и только если оно пустое.
Запуск из корня репозитория: python tools/images/fetch_images.py [--only code1,code2] [--force]
"""
import argparse
import html
import io
import json
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
API = "https://commons.wikimedia.org/w/api.php"
UA = "proshivki-images/0.1 (https://github.com/screpka228/proshivki)"
OK_LICENSE = re.compile(r"^(CC BY(-SA)? \d|CC0|Public domain|PD)", re.I)
EXT = (".jpg", ".jpeg", ".png", ".webp")
SUFFIX = {"pro", "xl", "a", "plus", "ultra", "lite", "max", "mini", "fold", "5g", "t", "se", "fe", "edge", "neo", "s", "x", "e"}
BAD = ("logo", "box", "unboxing", "screenshot", "wallpaper", "comparison", "size", "charger", "case")


def get(params: dict) -> dict:
    url = API + "?" + urllib.parse.urlencode({**params, "format": "json"})
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def model_ok(title: str, name: str) -> bool:
    """В названии файла должна быть именно эта модель, а не соседняя (Pixel 6 vs Pixel 6 Pro)."""
    t = re.sub(r"[_\-]", " ", title.lower())
    n = re.sub(r"\s+", " ", name.lower())
    i = t.find(n)
    if i < 0:
        return False
    rest = t[i + len(n):].lstrip().split(" ")
    nxt = re.sub(r"[^a-z0-9]", "", rest[0]) if rest else ""
    name_tokens = set(n.split())
    return not (nxt in SUFFIX and nxt not in name_tokens)


def find(name: str):
    q = get({"action": "query", "list": "search", "srsearch": name, "srnamespace": 6, "srlimit": 15})
    titles = [x["title"] for x in q["query"]["search"]
              if x["title"].lower().endswith(EXT) and model_ok(x["title"], name)
              and not any(b in x["title"].lower() for b in BAD)]
    for title in titles[:6]:
        d = get({"action": "query", "titles": title, "prop": "imageinfo", "iiprop": "url|extmetadata|size",
                 "iiurlheight": 600})
        page = next(iter(d["query"]["pages"].values()))
        info = (page.get("imageinfo") or [None])[0]
        if not info:
            continue
        meta = info.get("extmetadata", {})
        lic = meta.get("LicenseShortName", {}).get("value", "")
        if not OK_LICENSE.match(lic):
            continue
        # вытянутые вертикальные фото лучше всего подходят под карточку; горизонтальные тоже берём, но реже
        author = re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", "", meta.get("Artist", {}).get("value", "")))).strip()
        if not author:
            continue
        norm_lic = re.sub(r" \d(\.\d)?$", "", lic).replace("Public domain", "PD").strip()
        norm_lic = {"CC BY-SA": "CC BY-SA", "CC BY": "CC BY"}.get(norm_lic, norm_lic)
        if norm_lic not in ("CC BY", "CC BY-SA", "CC0", "PD"):
            continue
        return {"thumb": info.get("thumburl") or info["url"], "author": author[:120], "license": norm_lic,
                "source": info["descriptionurl"]}
    return None


def download(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only")
    ap.add_argument("--force", action="store_true")
    a = ap.parse_args()
    only = set(a.only.split(",")) if a.only else None
    (ROOT / "data" / "images").mkdir(exist_ok=True)
    found = missing = 0
    for f in sorted((ROOT / "data" / "devices").glob("*.json")):
        d = json.loads(f.read_text(encoding="utf-8"))
        c = d["codename"]
        if (only and c not in only) or (d.get("image") and not a.force):
            continue
        name = d["names"][0]
        if d["brand"].lower() not in name.lower():
            name = f"{d['brand']} {name}"
        try:
            r = find(name)
            time.sleep(0.5)
            if not r:
                print(f"- {c}: нет подходящего свободного фото ({name})")
                missing += 1
                continue
            img = Image.open(io.BytesIO(download(r["thumb"]))).convert("RGBA" if False else "RGB")
            img.thumbnail((600, 600))
            img.save(ROOT / "data" / "images" / f"{c}.webp", "WEBP", quality=80)
            d["image"] = {"url": f"images/{c}.webp", "author": r["author"], "license": r["license"], "source": r["source"]}
            f.write_text(json.dumps(d, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
            print(f"+ {c}: {r['license']} · {r['author'][:40]}")
            found += 1
            time.sleep(0.5)
        except Exception as e:
            print(f"! {c}: {e}")
    print(f"\nнайдено {found}, без фото {missing}")


if __name__ == "__main__":
    sys.exit(main())
