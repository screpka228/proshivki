// SPDX-License-Identifier: AGPL-3.0-or-later
// Лёгкий индекс: только то, что нужно для поиска и списка. Полные карточки — в data/devices/<codename>.json
import { readFileSync, readdirSync, writeFileSync } from 'node:fs';

const devices = readdirSync('data/devices')
  .filter((f) => f.endsWith('.json'))
  .sort()
  .map((f) => JSON.parse(readFileSync(`data/devices/${f}`, 'utf8')))
  .map((d) => ({
    c: d.codename,
    n: d.names,
    b: d.brand,
    s: d.bootloader.status,
    w: d.bootloader.wait_days ?? null,
    r: d.roms.length,
    a: d.roms.filter((x) => x.status === 'active').map((x) => x.name),
    d: d.difficulty ?? null,
    i: d.image ? 1 : 0,
  }));

writeFileSync('data/index.json', JSON.stringify({ count: devices.length, generated: new Date().toISOString().slice(0, 10), devices }) + '\n');
console.log(`data/index.json: ${devices.length} моделей`);
