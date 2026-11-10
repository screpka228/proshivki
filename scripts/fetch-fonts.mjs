// SPDX-License-Identifier: AGPL-3.0-or-later
// Скачивает шрифты (SIL OFL 1.1) из репозитория google/fonts на GitHub, кладёт ttf в web/fonts и пишет web/css/fonts.css.
// Те же ttf годятся для APK (положить в android/app/src/main/res/font, имена — строчные буквы и подчёркивания).
// Запуск из корня проекта: node scripts/fetch-fonts.mjs
import { mkdirSync, existsSync, writeFileSync } from 'node:fs';

const RAW = 'https://raw.githubusercontent.com/google/fonts/main/ofl/';
// [папка в google/fonts, файл, CSS-семейство, вес (число или диапазон), локальное имя]
const FONTS = [
  ['manrope', 'Manrope[wght].ttf', 'Manrope', '200 800', 'manrope.ttf'],
  ['spacegrotesk', 'SpaceGrotesk[wght].ttf', 'Space Grotesk', '300 700', 'space_grotesk.ttf'],
  ['jetbrainsmono', 'JetBrainsMono[wght].ttf', 'JetBrains Mono', '100 800', 'jetbrains_mono.ttf'],
  ['rubik', 'Rubik[wght].ttf', 'Rubik', '300 900', 'rubik.ttf'],
  ['inter', 'Inter[opsz,wght].ttf', 'Inter', '100 900', 'inter.ttf'],
  ['vt323', 'VT323-Regular.ttf', 'VT323', '400', 'vt323.ttf'],
  ['nunito', 'Nunito[wght].ttf', 'Nunito', '200 1000', 'nunito.ttf'],
  ['orbitron', 'Orbitron[wght].ttf', 'Orbitron', '400 900', 'orbitron.ttf'],
  ['exo2', 'Exo2[wght].ttf', 'Exo 2', '100 900', 'exo_2.ttf'],
  ['atkinsonhyperlegible', 'AtkinsonHyperlegible-Regular.ttf', 'Atkinson Hyperlegible', '400', 'atkinson_regular.ttf'],
  ['atkinsonhyperlegible', 'AtkinsonHyperlegible-Bold.ttf', 'Atkinson Hyperlegible', '700', 'atkinson_bold.ttf'],
];

mkdirSync('web/fonts', { recursive: true });
let css = '/* SPDX-License-Identifier: AGPL-3.0-or-later; шрифты — SIL OFL 1.1, подключены локально */\n';
for (const [dir, file, family, weight, local] of FONTS) {
  const dest = `web/fonts/${local}`;
  if (!existsSync(dest)) {
    const r = await fetch(`${RAW}${dir}/${encodeURIComponent(file)}`);
    if (!r.ok) { console.warn('нет файла', file, r.status); continue; }
    writeFileSync(dest, Buffer.from(await r.arrayBuffer()));
    console.log('скачан', local);
  }
  css += `@font-face{font-family:"${family}";font-weight:${weight};font-style:normal;font-display:swap;src:url(../fonts/${local}) format("truetype")}\n`;
}
writeFileSync('web/css/fonts.css', css);
console.log('Готово: web/fonts, web/css/fonts.css');
