import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const versionPattern = /^\d+\.\d+\.\d+$/;
export interface ReleaseMetadata {
  version: string;
  versionCode: number;
  artifact: string;
  sha256: string;
  sizeBytes: number;
  gitCommit: string;
  createdUtc: string;
}

export function latestRelease(releasesRoot: string): string {
  const versions = readdirSync(releasesRoot, { withFileTypes: true })
    .filter(entry => entry.isDirectory() && versionPattern.test(entry.name) && existsSync(join(releasesRoot, entry.name, 'release.json')))
    .map(entry => entry.name)
    .sort((a, b) => {
      const left = a.split('.').map(Number), right = b.split('.').map(Number);
      return right[0]! - left[0]! || right[1]! - left[1]! || right[2]! - left[2]!;
    });
  if (!versions[0]) throw new Error('No hay entregas locales. Genera un APK firmado con scripts/Release.ps1.');
  return join(releasesRoot, versions[0]);
}

export function prepareRelease(releaseDirectory: string, publicDirectory: string): ReleaseMetadata {
  const release: ReleaseMetadata = JSON.parse(readFileSync(join(releaseDirectory, 'release.json'), 'utf8').replace(/^\uFEFF/, ''));
  if (!versionPattern.test(release.version) || release.artifact !== `AppIstan-${release.version}.apk`) {
    throw new Error('Nombre de APK o versión inválidos en release.json.');
  }
  if (!/^[a-f0-9]{64}$/.test(release.sha256) || !Number.isSafeInteger(release.sizeBytes) || release.sizeBytes <= 0) {
    throw new Error('SHA-256 o tamaño inválidos en release.json.');
  }
  const apk = readFileSync(join(releaseDirectory, release.artifact));
  if (apk.length !== release.sizeBytes || createHash('sha256').update(apk).digest('hex') !== release.sha256) {
    throw new Error('El APK no coincide con el tamaño o SHA-256 de su entrega.');
  }
  const downloads = resolve(publicDirectory, 'downloads');
  if (relative(resolve(publicDirectory), downloads) !== 'downloads') throw new Error('Carpeta de descarga inválida.');
  // This directory contains generated APK copies only; keep the previous files if verification fails.
  rmSync(downloads, { recursive: true, force: true });
  mkdirSync(downloads, { recursive: true });
  writeFileSync(join(downloads, release.artifact), apk);
  writeFileSync(join(downloads, 'AppIstan-latest.apk'), apk);
  writeFileSync(join(publicDirectory, 'release.json'), JSON.stringify(release, null, 2) + '\n');
  return release;
}

if (import.meta.main) {
  const siteRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
  const args = process.argv.slice(2);
  if (args.length && (args.length !== 2 || args[0] !== '--release-dir')) throw new Error('Uso: prepare-release.ts [--release-dir RUTA]');
  const explicit = args[1] || process.env.APPISTAN_RELEASE_DIR;
  const source = explicit ? resolve(explicit) : latestRelease(resolve(siteRoot, '../releases'));
  const release = prepareRelease(source, join(siteRoot, 'public'));
  console.log(`Descarga directa preparada: AppIstan ${release.version} · ${release.sizeBytes} bytes · SHA-256 verificado`);
}
