import { mkdir, copyFile, writeFile } from 'node:fs/promises';
const api = process.env.MAZE_API_URL;
if (!api || !/^https?:\/\//.test(api)) throw new Error('Set MAZE_API_URL to the deployed backend origin (or local origin for a local build).');
const origin = new URL(api).origin;
await mkdir('dist', { recursive: true });
for (const file of ['index.html', 'styles.css', 'app.js', 'model.js', 'favicon.svg']) await copyFile(file, `dist/${file}`);
await writeFile('dist/config.js', `window.MAZE_CONFIG = ${JSON.stringify({ apiBase: origin })};\n`);
console.log(`Built static frontend for ${origin}`);
