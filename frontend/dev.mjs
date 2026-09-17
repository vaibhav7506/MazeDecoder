import http from 'node:http';
import { readFile } from 'node:fs/promises';
const types = { html: 'text/html', js: 'text/javascript', css: 'text/css', svg: 'image/svg+xml' };
const allowed = new Set(['index.html', 'app.js', 'model.js', 'styles.css', 'favicon.svg']);
http.createServer(async (req, res) => {
  const path = new URL(req.url, 'http://localhost').pathname.slice(1) || 'index.html';
  res.setHeader('Cache-Control', 'no-store');
  if (path === 'config.js') {
    res.setHeader('Content-Type', 'text/javascript');
    return res.end(`window.MAZE_CONFIG = ${JSON.stringify({ apiBase: process.env.MAZE_API_URL || 'http://localhost:8080' })};`);
  }
  if (!allowed.has(path)) { res.writeHead(404); return res.end(); }
  try { res.setHeader('Content-Type', types[path.split('.').pop()]); res.end(await readFile(new URL(path, import.meta.url))); }
  catch { res.writeHead(404); res.end(); }
}).listen(Number(process.env.FRONTEND_PORT || 5173), '127.0.0.1', () => console.log('MazeDecoder frontend ready'));
