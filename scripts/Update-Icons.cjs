const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');

const root = path.resolve(__dirname, '..');
const assets = path.join(root, 'app/src/main/assets');
const drawable = path.join(root, 'app/src/main/res/drawable');
const apps = [...JSON.parse(fs.readFileSync(path.join(assets, 'catalog.json'), 'utf8')),
  { id: 'com.google.android.gms', name: 'Google Play Services' }];
const icons = [];

async function download(app) {
  const page = `https://play.google.com/store/apps/details?id=${app.id}&hl=es&gl=US`;
  const response = await fetch(page, { signal: AbortSignal.timeout(30000) });
  assert(response.ok, `${app.id}: Google Play HTTP ${response.status}`);
  const html = await response.text();
  const original = html.match(/<meta\s+property="og:image"\s+content="([^"]+)"/)?.[1];
  assert(original, `${app.id}: no se encontró el icono de Google Play`);
  const imageUrl = new URL(original.replace(/&amp;/g, '&'));
  assert.equal(imageUrl.protocol, 'https:');
  assert.equal(imageUrl.hostname, 'play-lh.googleusercontent.com');
  imageUrl.pathname = imageUrl.pathname.replace(/=.*$/, '') + '=w128-h128-rw';
  const image = await fetch(imageUrl, { signal: AbortSignal.timeout(30000) });
  assert(image.ok, `${app.id}: icono HTTP ${image.status}`);
  const bytes = Buffer.from(await image.arrayBuffer());
  assert.equal(bytes.toString('ascii', 0, 4), 'RIFF');
  assert.equal(bytes.toString('ascii', 8, 12), 'WEBP');
  assert(bytes.length > 100 && bytes.length < 100000, `${app.id}: tamaño de icono inesperado`);
  const resource = 'app_' + app.id.replaceAll('.', '_').toLowerCase();
  fs.writeFileSync(path.join(drawable, resource + '.webp'), bytes);
  icons.push({ id: app.id, name: app.name, page, image: imageUrl.href, resource,
    sha256: crypto.createHash('sha256').update(bytes).digest('hex') });
  console.log(`Icono: ${app.name}`);
}

(async () => {
  let index = 0;
  await Promise.all(Array.from({ length: 4 }, async () => {
    while (index < apps.length) await download(apps[index++]);
  }));
  icons.sort((a, b) => apps.findIndex(app => app.id === a.id) - apps.findIndex(app => app.id === b.id));
  fs.writeFileSync(path.join(assets, 'icons.json'), JSON.stringify(icons, null, 2) + '\n');
  fs.writeFileSync(path.join(root, 'app/src/main/java/com/appistan/installer/AppIcons.kt'),
    'package com.appistan.installer\n\nobject AppIcons {\n    fun resource(id: String): Int? = when (id) {\n' +
    icons.map(icon => `        "${icon.id}" -> R.drawable.${icon.resource}`).join('\n') +
    '\n        else -> null\n    }\n}\n');
  console.log(`${icons.length} iconos oficiales descargados de Google Play.`);
})().catch(error => { console.error(error.message); process.exitCode = 1; });
