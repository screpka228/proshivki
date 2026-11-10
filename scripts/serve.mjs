// SPDX-License-Identifier: AGPL-3.0-or-later
// Локальный статический сервер для разработки: http://localhost:8080/web/
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname, join, normalize } from 'node:path';

const TYPES = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.woff2': 'font/woff2', '.svg': 'image/svg+xml', '.webp': 'image/webp' };
const root = process.cwd();
createServer(async (req, res) => {
  let p = normalize(decodeURIComponent(new URL(req.url, 'http://x').pathname)).replace(/^([/\\]\.\.)+/, '');
  if (p.endsWith('/') || p.endsWith('\\')) p += 'index.html';
  try {
    const body = await readFile(join(root, p));
    res.writeHead(200, { 'content-type': TYPES[extname(p)] || 'application/octet-stream' });
    res.end(body);
  } catch {
    res.writeHead(404); res.end('not found');
  }
}).listen(8080, () => console.log('http://localhost:8080/web/'));
