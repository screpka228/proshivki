// SPDX-License-Identifier: AGPL-3.0-or-later
// 10 тем = 10 наборов токенов. Компоненты общие (css/app.css).
const S = (okb, okf, wb, wf, nb, nf, ub, uf) => ({
  '--ok-bg': okb, '--ok-fg': okf, '--wait-bg': wb, '--wait-fg': wf, '--no-bg': nb, '--no-fg': nf, '--unk-bg': ub, '--unk-fg': uf,
});

export const THEMES = [
  { id: 'soft', name: 'Мягкий светлый', tokens: {
    '--bg': '#F4F3FA', '--card': '#fff', '--line': '#EFEDF7', '--text': '#1B1830', '--sub': '#6B6785',
    '--accent': '#5B4CF0', '--on-accent': '#fff', '--radius': '26px', '--font': 'Manrope,system-ui,sans-serif',
    ...S('#DDF5E6', '#14653A', '#FFF0C7', '#7A5300', '#FFE0DC', '#9A2414', '#ECEBF2', '#5B5870') } },
  { id: 'navy', name: 'Тёмно-синий', tokens: {
    '--bg': '#0B0D11', '--card': '#14171D', '--line': '#22262E', '--text': '#E8EAED', '--sub': '#9AA0A6',
    '--accent': '#8AB4F8', '--on-accent': '#0B0D11', '--radius': '18px', '--font': 'system-ui,sans-serif',
    '--bw': '1px', '--bc': '#22262E', '--shadow': 'none',
    ...S('#1E3A2A', '#81C995', '#3A3018', '#FDD663', '#3A2020', '#F28B82', '#1D2A3D', '#8AB4F8') } },
  { id: 'techpass', name: 'Техпаспорт', tokens: {
    '--bg': '#F2F1EC', '--card': '#F2F1EC', '--line': '#111', '--text': '#111', '--sub': '#555',
    '--accent': '#111', '--on-accent': '#F2F1EC', '--radius': '0px', '--font': '"Space Grotesk",system-ui,sans-serif',
    '--bw': '2px', '--bc': '#111', '--shadow': 'none', '--pill-r': '0px', '--pill-bw': '2px',
    ...S('#C8F169', '#111', '#FFD23F', '#111', '#FF5A1F', '#111', '#fff', '#111') } },
  { id: 'terminal', name: 'Терминал', tokens: {
    '--bg': '#050805', '--card': '#050805', '--line': '#1F3A1F', '--text': '#B7F5B0', '--sub': '#5F8A5A',
    '--accent': '#39FF6A', '--on-accent': '#050805', '--radius': '0px', '--font': '"JetBrains Mono",monospace',
    '--bw': '1px', '--bc': '#1F3A1F', '--shadow': 'none', '--pill-r': '0px', '--pill-bw': '1px',
    ...S('#050805', '#39FF6A', '#050805', '#FFD23F', '#050805', '#FF5F56', '#050805', '#5F8A5A') } },
  { id: 'material', name: 'Material You', tokens: {
    '--bg': '#131316', '--card': '#1F1F25', '--line': '#2E2E36', '--text': '#E5E1E9', '--sub': '#C8C5D0',
    '--accent': '#C6BFFF', '--on-accent': '#2A1F6E', '--radius': '28px', '--font': 'Rubik,system-ui,sans-serif', '--shadow': 'none', '--pill-r': '10px',
    ...S('#1E4D3A', '#9CF2C6', '#4D3F12', '#F5D97A', '#5C1F1A', '#FFB4AB', '#2E2E36', '#C8C5D0') } },
  { id: 'amoled', name: 'AMOLED', tokens: {
    '--bg': '#000', '--card': '#0E0E0E', '--line': '#1A1A1A', '--text': '#F5F5F5', '--sub': '#9A9A9A',
    '--accent': '#FF7A1A', '--on-accent': '#000', '--radius': '20px', '--font': 'Inter,system-ui,sans-serif', '--shadow': 'none', '--pill-r': '20px',
    ...S('#0F2E1A', '#6BE39A', '#33260A', '#FFC24A', '#3A1010', '#FF7B6B', '#1A1A1A', '#9A9A9A') } },
  { id: 'lcd', name: 'Ретро LCD', tokens: {
    '--bg': '#9BAE84', '--card': '#AFC197', '--line': '#8FA276', '--text': '#1F2A14', '--sub': '#3E4D2E',
    '--accent': '#1F2A14', '--on-accent': '#AFC197', '--radius': '2px', '--font': 'VT323,monospace', '--shadow': 'none', '--pill-r': '2px', '--pill-bw': '1px',
    ...S('#1F2A14', '#AFC197', '#AFC197', '#1F2A14', '#1F2A14', '#E4EED5', '#AFC197', '#3E4D2E') } },
  { id: 'ocean', name: 'Океан', tokens: {
    '--bg': '#06212B', '--card': '#0C3340', '--line': '#14495A', '--text': '#E3F6F8', '--sub': '#8FB8C0',
    '--accent': '#3FD2C7', '--on-accent': '#06212B', '--radius': '24px', '--font': 'Nunito,system-ui,sans-serif', '--shadow': 'none',
    ...S('#0E4A3E', '#7EF0C5', '#4A3A0E', '#FFD98A', '#4A1820', '#FF9AA6', '#14495A', '#8FB8C0') } },
  { id: 'synth', name: 'Синтвейв', tokens: {
    '--bg': '#140A26', '--card': '#22113D', '--line': '#2E1A50', '--text': '#F4E9FF', '--sub': '#B39CD6',
    '--accent': '#FF3EA5', '--on-accent': '#140A26', '--radius': '14px', '--font': '"Exo 2",system-ui,sans-serif', '--head': 'Orbitron,"Exo 2",system-ui,sans-serif', '--shadow': 'none', '--pill-r': '14px',
    ...S('#0E3A3A', '#3EF2E0', '#3D2A0A', '#FFC857', '#4A0E2E', '#FF7AC0', '#2E1A50', '#B39CD6') } },
  { id: 'contrast', name: 'Высокий контраст', tokens: {
    '--bg': '#000', '--card': '#000', '--line': '#fff', '--text': '#fff', '--sub': '#E0E0E0',
    '--accent': '#FFE500', '--on-accent': '#000', '--radius': '6px', '--font': '"Atkinson Hyperlegible",system-ui,sans-serif',
    '--bw': '2px', '--bc': '#fff', '--shadow': 'none', '--pill-r': '6px', '--pill-bw': '2px',
    ...S('#000', '#7CFF6B', '#000', '#FFE500', '#000', '#FF8A80', '#000', '#fff') } },
];

const KEY = 'theme';
const ALL_KEYS = [...new Set(THEMES.flatMap((t) => Object.keys(t.tokens)))];

export function loadThemeId() {
  try {
    const v = localStorage.getItem(KEY);
    if (THEMES.some((t) => t.id === v)) return v;
  } catch { /* localStorage недоступен */ }
  return 'soft';
}

export function applyTheme(id) {
  const t = THEMES.find((x) => x.id === id) || THEMES[0];
  const root = document.documentElement;
  for (const k of ALL_KEYS) root.style.removeProperty(k);
  for (const [k, v] of Object.entries(t.tokens)) root.style.setProperty(k, v);
  try { localStorage.setItem(KEY, t.id); } catch { /* ignore */ }
  return t.id;
}
