const { spawnSync } = require('node:child_process');
const path = require('node:path');
const assert = require('node:assert/strict');
const [adbPath, serial] = process.argv.slice(2);
assert(adbPath && /^127\.0\.0\.1:\d+$/.test(serial), 'Indica ADB y un emulador local.');
const root = path.resolve(__dirname, '..');
const pkg = 'com.appistan.installer.debug';
const fixture = 'com.appistan.fixture';
const shared = `/sdcard/Android/data/${pkg}/files/Download/fixture-tests`;
const wait = ms => Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms);
function adb(args) {
  const result = spawnSync(adbPath, ['-P', '5038', '-s', serial, ...args], { windowsHide: true, maxBuffer: 4 * 1024 * 1024, timeout: 30000 });
  assert.equal(result.status, 0, `${args.join(' ')}: ${result.stderr?.toString() || result.error}`);
  return result.stdout.toString().trim();
}
const shell = (...args) => adb(['shell', ...args]);
function call(method) {
  const response = shell('content', 'call', '--uri', `content://${pkg}.test`, '--method', method);
  const value = response.match(/result=([\s\S]*?)\}\]/)?.[1];
  assert(value, response);
  return value;
}
function dump() {
  shell('rm', '-f', '/sdcard/appistan-window.xml');
  shell('uiautomator', 'dump', '/sdcard/appistan-window.xml');
  return adb(['exec-out', 'cat', '/sdcard/appistan-window.xml']);
}
function nodes(xml) {
  return [...xml.matchAll(/<node\s[^>]+>/g)].map(match => Object.fromEntries([...match[0].matchAll(/([\w-]+)="([^"]*)"/g)].map(value => [value[1], value[2]])));
}
function tap(node) {
  const bounds = node.bounds.match(/\[(\d+),(\d+)\]\[(\d+),(\d+)\]/);
  assert(bounds, 'El control no tiene una posición válida.');
  shell('input', 'tap', Math.round((+bounds[1] + +bounds[3]) / 2), Math.round((+bounds[2] + +bounds[4]) / 2));
}
function open() { shell('am', 'start', '-W', '-n', `${pkg}/com.appistan.installer.MainActivity`); }
function version() { return Number(shell('dumpsys', 'package', fixture).match(/versionCode=(\d+)/)?.[1]); }
function marker() {
  shell('am', 'start', '-W', '-n', `${fixture}/.FixtureActivity`);
  const value = dump().match(/fixture:[0-9a-f-]{36}/)?.[0];
  assert(value, 'La fixture debe mostrar su marcador persistente.');
  return value;
}
function awaitStatus(expected) {
  const deadline = Date.now() + 15000;
  let result;
  do { result = call('status'); if (result.startsWith(expected + '|')) return result; wait(500); } while (Date.now() < deadline);
  assert.fail(`Estado esperado ${expected}, recibido ${result}`);
}

console.log('1/4: catálogo y apertura de AppIstan');
open();
assert.equal(call('catalog'), '50|50');
assert.equal(version(), 1);
const before = marker();
open();
assert(dump().includes('AppIstan'));
shell('mkdir', '-p', shared);
for (const value of [1, 2]) adb(['push', path.join(root, `.tools/test-assets/fixture-v${value}.apk`), `${shared}/fixture-v${value}.apk`]);

console.log('2/4: permiso desde Ajustes y actualización con confirmación nativa');
let controls;
if (call('install-permission') !== 'true') {
  shell('am', 'start', '-W', '-a', 'android.settings.MANAGE_UNKNOWN_APP_SOURCES', '-d', `package:${pkg}`);
  const permissionDeadline = Date.now() + 10000;
  let permission;
  do { permission = nodes(dump()).find(node => node.class === 'android.widget.Switch'); if (!permission) wait(500); } while (!permission && Date.now() < permissionDeadline);
  assert(permission, 'No se encontró el permiso normal de instalación.');
  if (permission.checked !== 'true') tap(permission);
  assert.equal(call('install-permission'), 'true');
  shell('input', 'keyevent', '4');
}
call('seed-v2'); open();
let confirmed = false;
const deadline = Date.now() + 90000;
while (Date.now() < deadline && version() !== 2) {
  controls = nodes(dump());
  const button = controls.find(node => /packageinstaller.*:id\/ok_button$/.test(node['resource-id']) && node.enabled === 'true');
  if (button) { tap(button); confirmed = true; }
  wait(700);
}
assert(confirmed, 'Android no presentó la confirmación nativa.');
assert.equal(version(), 2);
awaitStatus('SUCCESS');
assert.equal(marker(), before, 'La actualización debe conservar los datos.');

console.log('3/4: rechazo de un APK alterado');
call('seed-corrupt'); open();
awaitStatus('FAILED');
assert.equal(version(), 2);
assert.equal(marker(), before);

console.log('4/4: omisión de una versión anterior y conservación de datos');
call('seed-v1'); open();
awaitStatus('SKIPPED');
assert.equal(version(), 2);
assert.equal(marker(), before);
open();
console.log('BLUE_STACKS_TESTS_PASSED: 4 escenarios; actualización confirmada, APK alterado rechazado y datos conservados.');
