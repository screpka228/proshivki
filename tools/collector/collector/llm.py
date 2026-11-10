# SPDX-License-Identifier: AGPL-3.0-or-later
"""Необязательная нормализация через OpenAI-совместимый API (по умолчанию DeepSeek).

Нет DEEPSEEK_API_KEY (или ключа для выбранного провайдера) — шаг пропускается без ошибки.
Модель НЕ добавляет фактов: только склеивает дубли названий и приводит поля к схеме.
"""
import json
import os
import urllib.request

PROVIDERS = {
    "deepseek": {"base": "https://api.deepseek.com", "model": "deepseek-chat", "key_env": "DEEPSEEK_API_KEY"},
    "openrouter": {"base": "https://openrouter.ai/api/v1", "model": "deepseek/deepseek-chat", "key_env": "OPENROUTER_API_KEY"},
    "ollama": {"base": "http://localhost:11434/v1", "model": "qwen2.5:7b", "key_env": None},
}

SYSTEM = (
    "Ты нормализуешь записи базы прошивок. Используй ТОЛЬКО переданные данные, ничего не добавляй от себя. "
    "Неизвестное оставляй как есть (null или 'unknown'). Разрешено: склеить дубли и варианты написания в names, "
    "убрать явный мусор в названиях. Запрещено менять bootloader.status, roms[].status/official, ссылки и codename. "
    'Ответ — только JSON вида {"devices":[...]} с теми же codename и в той же схеме, что на входе.'
)

ALLOWED_TOP = {"codename", "names", "brand", "soc", "android_range", "bootloader", "recovery", "roms", "treble_gsi",
               "difficulty", "links", "image", "updated", "sources", "manual"}


class Usage:
    def __init__(self):
        self.prompt = 0
        self.completion = 0


def available(provider: str) -> bool:
    env = PROVIDERS[provider]["key_env"]
    return env is None or bool(os.environ.get(env))


def _call(provider: str, base: str | None, model: str | None, drafts: list[dict], usage: Usage) -> list[dict]:
    p = PROVIDERS[provider]
    body = {
        "model": model or p["model"],
        "temperature": 0,
        "response_format": {"type": "json_object"},
        "messages": [
            {"role": "system", "content": SYSTEM},
            {"role": "user", "content": json.dumps({"devices": drafts}, ensure_ascii=False)},
        ],
    }
    headers = {"Content-Type": "application/json"}
    if p["key_env"]:
        headers["Authorization"] = "Bearer " + os.environ[p["key_env"]]
    req = urllib.request.Request((base or p["base"]).rstrip("/") + "/chat/completions",
                                 data=json.dumps(body).encode(), headers=headers)
    with urllib.request.urlopen(req, timeout=120) as r:
        resp = json.load(r)
    u = resp.get("usage") or {}
    usage.prompt += u.get("prompt_tokens", 0)
    usage.completion += u.get("completion_tokens", 0)
    return json.loads(resp["choices"][0]["message"]["content"])["devices"]


def _ok(orig: dict, new: dict) -> bool:
    """Модель могла только поправить имена; всё критичное должно совпасть с источником."""
    if not isinstance(new, dict) or set(new) - ALLOWED_TOP:
        return False
    if new.get("codename") != orig["codename"]:
        return False
    if not isinstance(new.get("names"), list) or not new["names"] or not all(isinstance(n, str) and n for n in new["names"]):
        return False
    for k in ("brand", "bootloader", "recovery", "roms", "links", "sources", "updated", "manual", "treble_gsi", "difficulty"):
        if new.get(k) != orig.get(k):
            return False
    return True


def normalize(drafts: list[dict], provider: str, base: str | None, model: str | None, batch: int, usage: Usage):
    """Возвращает (готовые, упавшие). Невалидный ответ — один повтор, затем в упавшие."""
    ok, failed = [], []
    for i in range(0, len(drafts), batch):
        chunk = drafts[i:i + batch]
        by_code = {d["codename"]: d for d in chunk}
        got: dict[str, dict] = {}
        for attempt in (1, 2):
            try:
                for n in _call(provider, base, model, [by_code[c] for c in by_code if c not in got], usage):
                    c = n.get("codename") if isinstance(n, dict) else None
                    if c in by_code and c not in got and _ok(by_code[c], n):
                        got[c] = n
            except Exception as e:
                print(f"! LLM, попытка {attempt}: {e}")
            if len(got) == len(by_code):
                break
        for c, d in by_code.items():
            (ok if c in got else failed).append(got.get(c, d))
    return ok, failed
