# SPDX-License-Identifier: AGPL-3.0-or-later
"""Сборщик базы: python -m collector --sources all --limit 200 [--dry-run]"""
import argparse
import difflib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

from . import llm
from .sources import SOURCES

ROOT = Path(__file__).resolve().parents[3]
DEVICES = ROOT / "data" / "devices"
TOOL = ROOT / "tools" / "collector"
CACHE = TOOL / ".cache"
FAILED = TOOL / "failed"


def dump(d: dict) -> str:
    return json.dumps(d, ensure_ascii=False, indent=2) + "\n"


def load_env():
    """Ключ из .env (если есть) — только в переменные окружения процесса, никуда не пишется."""
    f = ROOT / ".env"
    if f.exists():
        for line in f.read_text(encoding="utf-8").splitlines():
            m = re.match(r"\s*([A-Z_][A-Z0-9_]*)\s*=\s*(.*)", line)
            if m and m.group(1) not in os.environ:
                os.environ[m.group(1)] = m.group(2).strip().strip("\"'")


def merge(existing: dict, draft: dict) -> dict | None:
    """Обновляет только файлы, созданные сборщиком (manual: false). Остальные — не трогаем."""
    if existing.get("manual", True) is not False:
        return None
    out = dict(existing)
    out["names"] = existing["names"] + [n for n in draft["names"] if n not in existing["names"]]
    for k in ("soc", "recovery", "roms", "sources"):
        out[k] = draft[k] if draft[k] else existing.get(k)
    if existing["bootloader"]["status"] == "unknown":
        out["bootloader"] = draft["bootloader"]
    if out != existing:
        out["updated"] = draft["updated"]
    return out


def validate_files(candidates: dict[str, dict]) -> set[str]:
    """Проверяет по data/schema.json тем же скриптом, что и CI. Возвращает codename невалидных."""
    tmp = Path(tempfile.mkdtemp())
    try:
        for c, d in candidates.items():
            (tmp / f"{c}.json").write_text(dump(d), encoding="utf-8")
        r = subprocess.run(["node", "scripts/validate.mjs"], cwd=ROOT, capture_output=True, text=True,
                           env={**os.environ, "DEVICES_DIR": str(tmp)}, shell=(os.name == "nt"))
        return set(re.findall(r"✗ (\S+?)\.json", r.stderr))
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def main():
    ap = argparse.ArgumentParser(prog="collector")
    ap.add_argument("--sources", default="all", help="all или список через запятую: " + ",".join(SOURCES))
    ap.add_argument("--limit", type=int, default=200, help="максимум новых моделей на источник")
    ap.add_argument("--provider", default="deepseek", choices=list(llm.PROVIDERS))
    ap.add_argument("--base-url")
    ap.add_argument("--model")
    ap.add_argument("--batch", type=int, default=15)
    ap.add_argument("--price-in", type=float, help="цена за 1M входных токенов, USD (для оценки стоимости)")
    ap.add_argument("--price-out", type=float, help="цена за 1M выходных токенов, USD")
    ap.add_argument("--dry-run", action="store_true", help="ничего не писать, показать diff")
    a = ap.parse_args()
    load_env()

    names = list(SOURCES) if a.sources == "all" else a.sources.split(",")
    drafts: dict[str, dict] = {}
    for n in names:
        print(f"→ источник {n}")
        for d in SOURCES[n](a.limit, CACHE):
            if d["codename"] in drafts:
                drafts[d["codename"]]["names"] += [x for x in d["names"] if x not in drafts[d["codename"]]["names"]]
            else:
                drafts[d["codename"]] = d
    print(f"черновиков: {len(drafts)}")

    # Что вообще изменится: новые и обновляемые сборщиком; ручные пропускаем до обращения к нейросети.
    todo, protected = {}, 0
    for c, d in drafts.items():
        f = DEVICES / f"{c}.json"
        if not f.exists():
            todo[c] = d
        else:
            m = merge(json.loads(f.read_text(encoding="utf-8")), d)
            if m is None:
                protected += 1
            elif m != json.loads(f.read_text(encoding="utf-8")):
                todo[c] = d

    usage = llm.Usage()
    failed: list[dict] = []
    ready = list(todo.values())
    if not ready:
        pass
    elif llm.available(a.provider):
        print(f"→ нормализация через {a.provider}")
        ready, failed = llm.normalize(ready, a.provider, a.base_url, a.model, a.batch, usage)
        ready = ready  # failed в запись не попадают
    else:
        print(f"→ нормализация пропущена: нет ключа ({llm.PROVIDERS[a.provider]['key_env']} не задан)")

    final: dict[str, dict] = {}
    for d in ready:
        f = DEVICES / f"{d['codename']}.json"
        final[d["codename"]] = merge(json.loads(f.read_text(encoding="utf-8")), d) if f.exists() else d
    bad = validate_files(final) if final else set()
    for c in bad:
        failed.append(final.pop(c))

    added = updated = 0
    for c, d in final.items():
        f = DEVICES / f"{c}.json"
        old = f.read_text(encoding="utf-8") if f.exists() else ""
        new = dump(d)
        if a.dry_run:
            print("".join(difflib.unified_diff(old.splitlines(True), new.splitlines(True), f"a/{f.name}", f"b/{f.name}")) or "")
        else:
            f.write_text(new, encoding="utf-8")
        added += not old
        updated += bool(old)

    if failed:
        FAILED.mkdir(exist_ok=True)
        for d in failed:
            (FAILED / f"{d['codename']}.json").write_text(dump(d), encoding="utf-8")
    if final and not a.dry_run:
        subprocess.run(["npm", "run", "build:index"], cwd=ROOT, shell=(os.name == "nt"), check=False)

    print(f"\nИтог{' (dry-run)' if a.dry_run else ''}: добавлено {added}, обновлено {updated}, "
          f"пропущено ручных {protected}, упало {len(failed)}" + (f" (см. {FAILED})" if failed else ""))
    if usage.prompt or usage.completion:
        cost = ""
        if a.price_in is not None and a.price_out is not None:
            cost = f", ≈ ${usage.prompt / 1e6 * a.price_in + usage.completion / 1e6 * a.price_out:.4f}"
        print(f"Токены: вход {usage.prompt}, выход {usage.completion}{cost}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
