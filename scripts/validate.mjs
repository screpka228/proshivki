// SPDX-License-Identifier: AGPL-3.0-or-later
import { readFileSync, readdirSync } from 'node:fs';
import Ajv from 'ajv';

const DIR = process.env.DEVICES_DIR || 'data/devices';
const schema = JSON.parse(readFileSync('data/schema.json', 'utf8'));
const ajv = new Ajv({ allErrors: true });
const validate = ajv.compile(schema);

let bad = 0;
const seen = new Map();
for (const f of readdirSync(DIR).filter((x) => x.endsWith('.json'))) {
  const d = JSON.parse(readFileSync(`${DIR}/${f}`, 'utf8'));
  if (!validate(d)) {
    bad++;
    console.error(`✗ ${f}`);
    for (const e of validate.errors) console.error(`  ${e.instancePath || '/'} ${e.message}`);
    continue;
  }
  if (f !== `${d.codename}.json`) {
    bad++;
    console.error(`✗ ${f}: имя файла должно совпадать с codename (${d.codename})`);
  }
  if (seen.has(d.codename)) {
    bad++;
    console.error(`✗ ${f}: повтор codename`);
  }
  seen.set(d.codename, f);
  if (d.bootloader.status === 'wait' && d.bootloader.wait_days == null && !d.bootloader.notes) {
    console.warn(`! ${f}: статус wait без wait_days и пояснения`);
  }
}
if (bad) process.exit(1);
console.log(`OK: ${seen.size} моделей`);
