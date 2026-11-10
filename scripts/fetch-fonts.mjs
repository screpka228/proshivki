// SPDX-License-Identifier: AGPL-3.0-or-later
// Скачивает шрифты (SIL OFL) через npm-пакеты @fontsource, кладёт woff2 в web/fonts и пишет web/css/fonts.css.
// Для APK нужны ttf (Compose не читает woff2) — отдельно, см. android/README.
// Запуск: node scripts/fetch-fonts.mjs
import { execSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, copyFileSync, existsSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

// [пакет, CSS-семейство, подмножества, веса]
const FONTS = [
  ['manrope', 'Manrope', ['latin', 'cyrillic'], [500, 700, 800]],
  ['space-grotesk', 'Space Grotesk', ['latin'], [400, 700]],
  ['jetbrains-mono', 'JetBrains Mono', ['latin', 'cyrillic'], [400, 700]],
  ['rubik', 'Rubik', ['latin', 'cyrillic'], [400, 500, 700]],
  ['inter', 'Inter', ['latin', 'cyrillic'], [400, 700]],
  ['vt323', 'VT323', ['latin', 'cyrillic'], [400]],
  ['nunito', 'Nunito', ['latin', 'cyrillic'], [400, 700]],
  ['orbitron', 'Orbitron', ['latin'], [700]],
  ['exo-2', 'Exo 2', ['latin', 'cyrillic'], [400, 700]],
  ['atkinson-hyperlegible', 'Atkinson Hyperlegible', ['latin'], [400, 700]],
];
const RANGE = {
  latin: 'U+0000-00FF,U+0131,U+0152-0153,U+02BB-02BC,U+02C6,U+02DA,U+02DC,U+2000-206F,U+2074,U+20AC,U+2122,U+2191,U+2193,U+2212,U+2215,U+FEFF,U+FFFD',
  cyrillic: 'U+0301,U+0400-045F,U+0490-0491,U+04B0-04B1,U+2116',
};

const tmp = mkdtempSync(join(tmpdir(), 'fonts-'));
execSync('npm init -y', { cwd: tmp, stdio: 'ignore' });
execSync(`npm i --no-audit --no-fund ${FONTS.map((f) => '@fontsource/' + f[0]).join(' ')}`, { cwd: tmp, stdio: 'inherit' });

mkdirSync('web/fonts', { recursive: true });
let css = '/* SPDX-License-Identifier: AGPL-3.0-or-later; шрифты — SIL OFL 1.1, подключены локально */\n';
for (const [pkg, family, subsets, weights] of FONTS) {
  for (const sub of subsets) for (const w of weights) {
    const name = `${pkg}-${sub}-${w}-normal.woff2`;
    const src = join(tmp, 'node_modules', '@fontsource', pkg, 'files', name);
    if (!existsSync(src)) { console.warn('нет файла', name); continue; }
    copyFileSync(src, join('web/fonts', name));
    css += `@font-face{font-family:"${family}";font-weight:${w};font-style:normal;font-display:swap;src:url(../fonts/${name}) format("woff2");unicode-range:${RANGE[sub]}}\n`;
  }
}
writeFileSync('web/css/fonts.css', css);
console.log('Готово: web/fonts, web/css/fonts.css');
