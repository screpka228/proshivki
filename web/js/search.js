// SPDX-License-Identifier: AGPL-3.0-or-later
// Нечёткий поиск: нормализация, кириллица→латиница, подстрока, расстояние Левенштейна по словам.
const CYR = { а: 'a', б: 'b', в: 'v', г: 'g', д: 'd', е: 'e', ё: 'e', ж: 'zh', з: 'z', и: 'i', й: 'y', к: 'k', л: 'l', м: 'm', н: 'n', о: 'o', п: 'p', р: 'r', с: 's', т: 't', у: 'u', ф: 'f', х: 'h', ц: 'c', ч: 'ch', ш: 'sh', щ: 'sh', ъ: '', ы: 'y', ь: '', э: 'e', ю: 'yu', я: 'ya' };

export function norm(s) {
  return String(s).toLowerCase().replace(/[а-яё]/g, (c) => CYR[c] ?? c).replace(/ks/g, 'x').replace(/[^a-z0-9 ]+/g, ' ').replace(/\s+/g, ' ').trim();
}

function lev(a, b, max) {
  if (Math.abs(a.length - b.length) > max) return max + 1;
  let prev = Array.from({ length: b.length + 1 }, (_, i) => i);
  for (let i = 1; i <= a.length; i++) {
    const cur = [i];
    let best = i;
    for (let j = 1; j <= b.length; j++) {
      cur[j] = Math.min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1));
      if (cur[j] < best) best = cur[j];
    }
    if (best > max) return max + 1;
    prev = cur;
  }
  return prev[b.length];
}

export function prepare(devices) {
  return devices.map((d) => {
    const fields = [d.c, d.b, ...d.n].map(norm);
    return { d, fields, words: [...new Set(fields.flatMap((f) => f.split(' ')))], joined: fields.join(' ').replace(/ /g, '') };
  });
}

export function search(prepared, query, limit = 20) {
  const q = norm(query);
  if (!q) return [];
  const qs = q.split(' ');
  const res = [];
  for (const p of prepared) {
    let score = 0;
    let ok = true;
    for (const t of qs) {
      let best = 0;
      if (p.fields.some((f) => f === t)) best = 100;
      else if (p.words.some((w) => w.startsWith(t))) best = 80;
      else if (p.joined.includes(t)) best = 60;
      else {
        const max = t.length >= 7 ? 2 : t.length >= 4 ? 1 : 0;
        if (max) {
          for (const w of p.words) {
            if (lev(t, w, max) <= max || lev(t, w.slice(0, t.length), max) <= max) { best = 40; break; }
          }
        }
      }
      if (!best) { ok = false; break; }
      score += best;
    }
    if (ok) res.push([score, p.d]);
  }
  return res.sort((a, b) => b[0] - a[0]).slice(0, limit).map((x) => x[1]);
}
