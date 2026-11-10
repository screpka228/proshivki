// SPDX-License-Identifier: AGPL-3.0-or-later
import { THEMES, applyTheme, loadThemeId } from './themes.js';
import { prepare, search } from './search.js';

// Замените на свой репозиторий перед публикацией.
const REPO = 'screpka228/proshivki';
const DISCLAIMER = 'Информация собрана сообществом и может быть неточной или устаревшей. Разблокировка загрузчика и прошивка могут лишить гарантии, стереть данные или вывести устройство из строя. Всё, что вы делаете с устройством, — на ваш страх и риск. Автор не несёт ответственности за любой ущерб.';
const POPULAR = ['oriole', 'alioth', 'lemonade', 'starlte', 'ele'];
const STATUS = {
  open: ['ok', 'открыт'], oem_unlock: ['ok', 'OEM unlock'], wait: ['wait', 'с ожиданием'],
  impossible: ['no', 'нельзя'], unknown: ['unk', 'неизвестно'],
};

const app = document.getElementById('app');

// «Недавние» хранятся только в этом браузере (localStorage), на сервер ничего не уходит.
const RECENT_KEY = 'recent';
function getRecent() {
  try { const v = JSON.parse(localStorage.getItem(RECENT_KEY)); return Array.isArray(v) ? v : []; } catch { return []; }
}
function pushRecent(code) {
  try { localStorage.setItem(RECENT_KEY, JSON.stringify([code, ...getRecent().filter((c) => c !== code)].slice(0, 8))); } catch { /* ignore */ }
}
let themeId = applyTheme(loadThemeId());
let base = null;
let index = null;
let prepared = null;

// ---------- вспомогательное ----------
function h(tag, attrs = {}, ...kids) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (v == null || v === false) continue;
    if (k === 'class') el.className = v;
    else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
    else el.setAttribute(k, v === true ? '' : v);
  }
  for (const k of kids.flat()) if (k != null && k !== false) el.append(k.nodeType ? k : document.createTextNode(String(k)));
  return el;
}
const SVG_NS = 'http://www.w3.org/2000/svg';
function phone(w = 22, fill = 'var(--line)', stroke = 'var(--sub)') {
  const s = document.createElementNS(SVG_NS, 'svg');
  s.setAttribute('viewBox', '0 0 60 120');
  s.setAttribute('width', w);
  s.setAttribute('height', w * 2);
  s.setAttribute('aria-hidden', 'true');
  const r1 = document.createElementNS(SVG_NS, 'rect');
  for (const [k, v] of Object.entries({ x: 3, y: 2, width: 54, height: 116, rx: 9, fill, stroke, 'stroke-width': 2.5 })) r1.setAttribute(k, v);
  const r2 = document.createElementNS(SVG_NS, 'rect');
  for (const [k, v] of Object.entries({ x: 8, y: 10, width: 44, height: 98, rx: 4, fill: 'var(--text)', opacity: 0.85 })) r2.setAttribute(k, v);
  s.append(r1, r2);
  return s;
}
function statusPill(s, w) {
  const [cls, label] = STATUS[s] || STATUS.unknown;
  return h('span', { class: `pill ${cls}` }, s === 'wait' && w ? `ожидание ${w} дн.` : label);
}
function safeUrl(u) {
  try { const x = new URL(u); return x.protocol === 'https:' ? x.href : null; } catch { return null; }
}
function extLink(url, text, cls = 'chip') {
  const u = safeUrl(url);
  return u ? h('a', { class: cls, href: u, target: '_blank', rel: 'noopener noreferrer' }, text) : null;
}
function issueUrl(model) {
  const p = new URLSearchParams({ template: 'device.yml', title: `[Модель] ${model}`, model });
  return `https://github.com/${REPO}/issues/new?${p}`;
}
function themeButton() {
  return h('button', { class: 'sqbtn', 'aria-label': 'Тема', onclick: () => { location.hash = '#/themes'; } }, '◐');
}

// ---------- данные ----------
async function getJson(path) {
  const bases = base ? [base] : ['data/', '../data/'];
  let lastErr;
  for (const b of bases) {
    try {
      const r = await fetch(b + path);
      if (r.ok) { base = b; return await r.json(); }
      lastErr = new Error(r.status);
    } catch (e) { lastErr = e; }
  }
  throw lastErr;
}
async function ensureIndex() {
  if (index) return;
  index = await getJson('index.json');
  prepared = prepare(index.devices);
}

// ---------- экраны ----------
function disclaimer() { return h('p', { class: 'disc' }, DISCLAIMER); }

function deviceRow(d) {
  return h('a', { class: 'item', href: `#/d/${encodeURIComponent(d.c)}` },
    h('div', { style: 'display:flex;gap:12px;align-items:center' }, phone(22),
      h('div', {}, h('b', {}, d.n[0]), h('span', { class: 'sub' }, d.c))),
    statusPill(d.s, d.w));
}

const openBrands = new Set();
// Все модели, сгруппированные по брендам; раскрытые разделы запоминаются, пока открыта страница.
function brandSections() {
  const groups = new Map();
  for (const d of index.devices) groups.set(d.b, [...(groups.get(d.b) || []), d]);
  const sorted = [...groups].sort((a, b) => b[1].length - a[1].length || a[0].localeCompare(b[0]));
  return h('div', { style: 'display:flex;flex-direction:column;gap:10px;margin-top:14px' },
    h('span', { style: 'font-weight:800;font-size:16px' }, `Все модели · ${index.count}`),
    sorted.map(([brand, list]) => h('details', {
      class: 'card brand', open: openBrands.has(brand),
      ontoggle: (e) => (e.target.open ? openBrands.add(brand) : openBrands.delete(brand)),
    }, h('summary', {}, h('b', {}, brand), h('span', { class: 'sub' }, `${list.length}`)),
    h('div', { class: 'list' }, list.sort((a, b) => a.n[0].localeCompare(b.n[0])).map(deviceRow)))));
}

async function home() {
  await ensureIndex();
  const input = h('input', { type: 'search', id: 'q', placeholder: 'Модель или кодовое имя', autocomplete: 'off', 'aria-label': 'Поиск' });
  const results = h('div');
  const render = () => {
    results.replaceChildren();
    const q = input.value.trim();
    const recent = getRecent().map((c) => index.devices.find((d) => d.c === c)).filter(Boolean);
    const list = q ? search(prepared, q) : recent.length ? recent : POPULAR.map((c) => index.devices.find((d) => d.c === c)).filter(Boolean);
    results.append(h('span', { style: 'font-weight:800;font-size:16px;display:block;margin:0 0 8px' }, q ? 'Результаты' : recent.length ? 'Недавние' : 'Популярное'));
    if (list.length) results.append(h('div', { class: 'card list' }, list.map(deviceRow)));
    if (!q) results.append(brandSections());
    else if (!list.length) results.append(h('div', { class: 'card' },
      h('div', { class: 'empty' }, `«${q}» нет в базе`),
      h('a', { class: 'btn', href: `#/add?q=${encodeURIComponent(q)}` }, 'Не нашли модель? Добавить')));
  };
  input.addEventListener('input', render);
  app.replaceChildren(
    h('div', { class: 'row', style: 'margin-top:8px' },
      h('div', {}, h('div', { class: 'sub' }, `В базе ${index.count} моделей · ${index.devices.reduce((n, d) => n + d.r, 0)} прошивок · обновлено ${index.generated || '—'}`), h('h1', {}, 'Прошивки')), themeButton()),
    input, results,
    h('a', { class: 'btn alt', href: '#/add' }, '＋ Добавить или исправить модель'),
    h('a', { class: 'btn alt', href: '#/stats' }, 'Статистика'),
    disclaimer());
  render();
}

async function device(code) {
  app.replaceChildren(h('div', { class: 'empty' }, 'Загрузка…'));
  let d;
  try { d = await getJson(`devices/${code}.json`); } catch {
    app.replaceChildren(h('a', { href: '#/' }, '← Назад'), h('div', { class: 'card empty' }, 'Не удалось загрузить карточку.'));
    return;
  }
  pushRecent(code);
  const bl = d.bootloader;
  const img = d.image && safeUrl(d.image.url) ? d.image : null;
  const photo = h('div', { class: 'photo' },
    img ? h('img', { src: img.url, alt: d.names[0], loading: 'lazy', referrerpolicy: 'no-referrer' }) : phone(60, 'var(--card)'),
    img ? h('small', {}, `${img.author}, ${img.license}`) : null);
  const kv = (k, v) => v == null || v === '' ? null : h('div', { class: 'kv' }, h('span', {}, k), h('span', {}, v));
  const list = (items, fn) => items.length ? items.map(fn) : [h('div', { class: 'sub' }, 'нет данных')];
  app.replaceChildren(
    h('div', { class: 'row' }, h('a', { href: '#/', class: 'sqbtn', style: 'display:grid;place-items:center;text-decoration:none', 'aria-label': 'Назад' }, '←'), themeButton()),
    h('div', { class: 'row', style: 'align-items:flex-start;gap:16px' }, photo,
      h('div', { style: 'flex:1;display:flex;flex-direction:column;gap:6px' },
        h('span', { class: 'cap' }, d.brand), h('h1', { style: 'font-size:24px' }, d.names[0]),
        h('span', { class: 'sub' }, d.codename + (d.soc ? ` · ${d.soc}` : '')), h('div', {}, statusPill(bl.status, bl.wait_days)))),
    h('div', { class: 'card' }, h('span', { class: 'cap' }, 'Загрузчик'),
      kv('Статус', (STATUS[bl.status] || STATUS.unknown)[1]),
      kv('Ожидание', bl.wait_days != null ? `${bl.wait_days} дн.` : null),
      kv('Способ', bl.method), bl.notes ? h('div', { class: 'sub' }, bl.notes) : null,
      extLink(bl.guide_url, 'Руководство ↗')),
    h('div', { class: 'card' }, h('span', { class: 'cap' }, 'Рекавери'),
      ...list(d.recovery, (r) => h('div', { class: 'kv' }, h('span', {}, r.name), r.url ? extLink(r.url, r.official ? 'официальный ↗' : 'ссылка ↗') : h('span', {}, r.official ? 'официальный' : 'неофициальный')))),
    h('div', { class: 'card' }, h('span', { class: 'cap' }, 'Прошивки'),
      ...list(d.roms, (r) => h('div', { class: 'kv' },
        h('span', {}, `${r.name}${r.android ? ' · A' + r.android : ''}`),
        h('span', {}, extLink(r.url, `${r.official ? 'офиц.' : 'неофиц.'}${r.status === 'abandoned' ? ', заброшена' : ''} ↗`) || (r.status === 'abandoned' ? 'заброшена' : 'активна'))))),
    h('div', { class: 'card' }, kv('Treble / GSI', d.treble_gsi == null ? 'неизвестно' : d.treble_gsi ? 'да' : 'нет'),
      kv('Android', d.android_range), kv('Сложность', d.difficulty ? `${d.difficulty} из 5` : 'неизвестно'), kv('Обновлено', d.updated)),
    h('div', { class: 'card' }, h('span', { class: 'cap' }, 'Обсуждения'),
      h('div', { class: 'chips' }, extLink(d.links?.['4pda'], '4PDA ↗'), extLink(d.links?.xda, 'XDA ↗'),
        !safeUrl(d.links?.['4pda']) && !safeUrl(d.links?.xda) ? h('span', { class: 'sub' }, 'Ссылок пока нет') : null),
      h('a', { class: 'btn alt', href: issueUrl(`${d.names[0]} · ${d.codename}`), target: '_blank', rel: 'noopener noreferrer' }, 'Исправить / дополнить')),
    h('div', { class: 'card' }, h('span', { class: 'cap' }, 'Источники'),
      ...d.sources.map((s) => extLink(s, s.replace('https://', ''), 'sub'))),
    disclaimer());
  document.title = `${d.names[0]} — Прошивки`;
}

function add(q) {
  app.replaceChildren(
    h('div', { class: 'row' }, h('a', { href: '#/', class: 'sqbtn', style: 'display:grid;place-items:center;text-decoration:none', 'aria-label': 'Назад' }, '←'), themeButton()),
    h('h1', {}, 'Не нашли модель'), h('p', { class: 'sub' }, 'Помогите базе — поможете всем'),
    h('div', { class: 'card' },
      h('b', {}, q || 'Укажите модель в Issue'),
      h('span', { class: 'sub' }, 'Этой модели пока нет в базе. Отправьте, что знаете, и после проверки она появится у всех.'),
      h('span', { class: 'sub' }, 'В Issue: статус загрузчика, прошивки и рекавери, ссылки на 4PDA/XDA — своими словами, без копирования чужих текстов.')),
    h('a', { class: 'btn', href: issueUrl(q || ''), target: '_blank', rel: 'noopener noreferrer' }, 'Отправить на GitHub'),
    h('span', { class: 'sub' }, 'Откроется готовый Issue с заполненными полями'), disclaimer());
}

async function stats() {
  await ensureIndex();
  const count = (arr) => Object.entries(arr.reduce((m, k) => ((m[k] = (m[k] || 0) + 1), m), {})).sort((a, b) => b[1] - a[1]);
  const brands = count(index.devices.map((d) => d.b));
  const roms = count(index.devices.flatMap((d) => d.a || []));
  const rows = (list) => list.slice(0, 10).map(([k, v]) => h('div', { class: 'kv' }, h('span', {}, k), h('span', {}, v)));
  app.replaceChildren(
    h('div', { class: 'row' }, h('a', { href: '#/', class: 'sqbtn', style: 'display:grid;place-items:center;text-decoration:none', 'aria-label': 'Назад' }, '←'), themeButton()),
    h('h1', {}, 'Статистика'),
    h('div', { class: 'card' }, h('span', { class: 'cap' }, 'Моделей по брендам'), ...rows(brands)),
    h('div', { class: 'card' }, h('span', { class: 'cap' }, 'Активных прошивок'), h('b', {}, roms.reduce((n, [, v]) => n + v, 0)),
      h('span', { class: 'cap' }, 'Топ прошивок по числу устройств'), ...rows(roms)),
    disclaimer());
}

function themes() {
  const items = THEMES.map((t) => h('button', {
    class: 'card theme-item', style: `border:${t.id === themeId ? '2px solid var(--accent)' : 'var(--bw) solid var(--bc)'}`,
    onclick: () => { themeId = applyTheme(t.id); themes(); },
  }, h('span', { class: 'swatch' }, ['--bg', '--card', '--accent', '--ok-bg'].map((k) => h('i', { style: `background:${t.tokens[k]}` }))),
  h('span', { style: 'flex:1;font-weight:700' }, t.name), t.id === themeId ? h('span', {}, '✓') : null));
  app.replaceChildren(
    h('div', { class: 'row' }, h('a', { href: '#/', class: 'sqbtn', style: 'display:grid;place-items:center;text-decoration:none', 'aria-label': 'Назад' }, '←')),
    h('h1', {}, 'Тема'), ...items);
}

// ---------- роутер ----------
async function route() {
  const [, path = '', arg = ''] = location.hash.replace(/^#/, '').split('/');
  window.scrollTo(0, 0);
  document.title = 'Прошивки';
  try {
    if (path === 'd' && arg) await device(decodeURIComponent(arg));
    else if (path.startsWith('add')) add(new URLSearchParams(location.hash.split('?')[1] || '').get('q') || '');
    else if (path === 'themes') themes();
    else if (path === 'stats') await stats();
    else await home();
  } catch {
    app.replaceChildren(h('div', { class: 'card empty' }, 'Не удалось загрузить базу. Проверьте соединение.'));
  }
}
window.addEventListener('hashchange', route);
route();
