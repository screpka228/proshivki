# SPDX-License-Identifier: AGPL-3.0-or-later
"""Источники фактов: только открытые списки устройств на GitHub, без браузера.

Каждый источник — функция fetch(limit, cache) -> list[Draft]. Draft — словарь в формате data/schema.json
(только то, что источник подтверждает; остальное unknown/null).
"""
import json
import re
import time
import urllib.request
from datetime import date
from pathlib import Path

import yaml

UA = "proshivki-collector/0.1 (open-source firmware database)"
LINEAGE_API = "https://api.github.com/repos/LineageOS/lineage_wiki/contents/_data/devices"
CODENAME_RE = re.compile(r"^[a-z0-9_]+$")


def http_get(url: str, cache: Path, pause: float = 0.25) -> str:
    """GET с кешем на диске и вежливой паузой."""
    cache.mkdir(parents=True, exist_ok=True)
    f = cache / re.sub(r"[^A-Za-z0-9._-]", "_", url)[-180:]
    if f.exists():
        return f.read_text(encoding="utf-8")
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as r:
        body = r.read().decode("utf-8")
    f.write_text(body, encoding="utf-8")
    time.sleep(pause)
    return body


def _names(vendor: str, name: str, models) -> list[str]:
    out = []
    full = name if name.lower().startswith(vendor.lower()) else f"{vendor} {name}"
    out.append(full.strip())
    for m in models or []:
        m = str(m).strip()
        if m and m not in out:
            out.append(m)
    return out


def lineage(limit: int, cache: Path) -> list[dict]:
    """LineageOS wiki: _data/devices/*.yml. Берём кодовое имя, названия, SoC, версии, ссылку на вики."""
    listing = json.loads(http_get(LINEAGE_API, cache))
    files = [x for x in listing if x["name"].endswith(".yml")]
    # Для повторяемости и чтобы лимит не вырезал одни и те же буквы — сортировка по имени.
    files.sort(key=lambda x: x["name"].lower())
    today = date.today().isoformat()
    by_code: dict[str, dict] = {}
    for x in files:
        if len(by_code) >= limit:
            break
        try:
            y = yaml.safe_load(http_get(x["download_url"], cache))
        except Exception as e:  # битый файл не должен валить весь запуск
            print(f"! {x['name']}: {e}")
            continue
        if not isinstance(y, dict) or "codename" not in y:
            continue
        raw_code = str(y["codename"])
        code = raw_code.lower().replace("-", "_")
        if not CODENAME_RE.match(code):
            continue
        vendor = str(y.get("vendor") or "").strip() or "unknown"
        names = _names(vendor, str(y.get("name", code)), y.get("models"))
        wiki = f"https://wiki.lineageos.org/devices/{raw_code}/"
        active = bool(y.get("current_branch"))
        bl = {"status": "unknown", "wait_days": None, "method": None, "guide_url": None, "notes": None}
        if y.get("is_unlockable") is False:
            bl["status"] = "impossible"
            bl["notes"] = "По данным вики LineageOS, загрузчик не разблокируется."
        elif y.get("install_method") == "fastboot_xiaomi":
            bl["status"] = "wait"
            bl["notes"] = "Способ установки в вики LineageOS — через Mi Unlock; срок ожидания не указан."
        d = by_code.get(code)
        if d:  # другой вариант той же модели — только дополняем названия
            d["names"] += [n for n in names if n not in d["names"]]
            continue
        by_code[code] = {
            "codename": code,
            "names": names,
            "brand": vendor,
            "soc": (str(y["soc"]) if y.get("soc") else None),
            "android_range": None,
            "bootloader": bl,
            "recovery": [{"name": "Lineage Recovery", "official": True, "url": wiki}] if active else [],
            "roms": [{"name": "LineageOS", "android": None, "status": "active" if active else "abandoned",
                      "official": True, "url": wiki}],
            "treble_gsi": None,
            "difficulty": None,
            "links": {"4pda": None, "xda": None},
            "image": None,
            "updated": today,
            "sources": [wiki],
            "manual": False,
        }
    return list(by_code.values())


SOURCES = {"lineage": lineage}
