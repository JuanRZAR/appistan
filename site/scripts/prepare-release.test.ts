import { afterEach, expect, test } from 'bun:test';
import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { latestRelease, prepareRelease, type ReleaseMetadata } from './prepare-release';

const temporary: string[] = [];
function area() { const root = mkdtempSync(join(tmpdir(), 'appistan-site-')); temporary.push(root); return root; }
afterEach(() => { for (const root of temporary.splice(0)) rmSync(root, { recursive: true, force: true }); });
function fixture(root: string, version: string) {
  const directory = join(root, version), apk = Buffer.from('signed-apk-fixture-' + version);
  mkdirSync(directory, { recursive: true });
  const metadata: ReleaseMetadata = { version, versionCode: 1, artifact: `AppIstan-${version}.apk`,
    sha256: createHash('sha256').update(apk).digest('hex'), sizeBytes: apk.length, gitCommit: 'fixture', createdUtc: '2026-10-31T00:00:00Z' };
  writeFileSync(join(directory, metadata.artifact), apk);
  writeFileSync(join(directory, 'release.json'), JSON.stringify(metadata));
  return { directory, metadata, apk };
}

test('selects the latest stable delivery numerically and ignores an unfinished release', () => {
  const root = area(); fixture(root, '0.9.0'); const last = fixture(root, '0.10.0');
  mkdirSync(join(root, '1.0.0')); mkdirSync(join(root, '2.0.0-beta'));
  expect(latestRelease(root)).toBe(last.directory);
});
test('prepares exact APK bytes for direct and stable downloads and removes older generated versions', () => {
  const root = area(), publicRoot = join(root, 'public');
  const first = fixture(root, '0.4.0'); prepareRelease(first.directory, publicRoot);
  const last = fixture(root, '0.5.0'); prepareRelease(last.directory, publicRoot);
  expect(readdirSync(join(publicRoot, 'downloads')).sort()).toEqual(['AppIstan-0.5.0.apk', 'AppIstan-latest.apk']);
  expect(readFileSync(join(publicRoot, 'downloads/AppIstan-latest.apk'))).toEqual(last.apk);
  expect(JSON.parse(readFileSync(join(publicRoot, 'release.json'), 'utf8')).version).toBe('0.5.0');
});
test('rejects a modified APK before changing the working download', () => {
  const root = area(), publicRoot = join(root, 'public'), first = fixture(root, '0.4.0');
  prepareRelease(first.directory, publicRoot);
  const broken = fixture(root, '0.5.0'); writeFileSync(join(broken.directory, broken.metadata.artifact), Buffer.alloc(broken.apk.length, 0xff));
  expect(() => prepareRelease(broken.directory, publicRoot)).toThrow('SHA-256');
  expect(readFileSync(join(publicRoot, 'downloads/AppIstan-latest.apk'))).toEqual(first.apk);
});
test('rejects filenames outside the release folder', () => {
  const root = area(), release = fixture(root, '0.4.0'), publicRoot = join(root, 'public');
  writeFileSync(join(release.directory, 'release.json'), JSON.stringify({ ...release.metadata, artifact: '../outside.apk' }));
  expect(() => prepareRelease(release.directory, publicRoot)).toThrow('inválidos');
  expect(existsSync(publicRoot)).toBe(false);
});
