import { afterEach, expect, test } from 'bun:test';
import { createHash } from 'node:crypto';
import { basename, join } from 'node:path';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { publishRelease, repositoryFromOrigin, type Runner } from '../../scripts/publish-release';

const temporary: string[] = [];
afterEach(() => { for (const root of temporary.splice(0)) rmSync(root, { recursive: true, force: true }); });
function setup(version = '0.4.0') {
  const root = mkdtempSync(join(tmpdir(), 'appistan-publish-')); temporary.push(root);
  const apk = Buffer.from('verified fixture APK'), artifact = `AppIstan-${version}.apk`, commit = 'a'.repeat(40);
  const metadata = { version, artifact, versionCode: 4, sizeBytes: apk.length, sha256: createHash('sha256').update(apk).digest('hex'), gitCommit: commit, createdUtc: '2026-10-31T00:00:00Z' };
  writeFileSync(join(root, artifact), apk); writeFileSync(join(root, 'AppIstan-latest.apk'), apk);
  writeFileSync(join(root, 'release.json'), JSON.stringify(metadata));
  type State = { tag_name: string; draft: boolean; prerelease: boolean; assets: { name: string; size: number; digest: string }[] };
  const state = { remote: null as State | null, latest: null as State | null, pages: false, failUpload: false, failDispatch: false, wrongTag: false };
  const calls: string[][] = [];
  const ok = (value = '') => ({ status: 0, stdout: value, stderr: '' });
  const missing = () => ({ status: 1, stdout: '{"status":"404"}', stderr: 'HTTP 404' });
  const asset = (file: string) => { const bytes = readFileSync(file); return { name: basename(file), size: bytes.length, digest: `sha256:${createHash('sha256').update(bytes).digest('hex')}` }; };
  const published = (): State => ({ tag_name: `v${version}`, draft: false, prerelease: false, assets: [artifact, 'AppIstan-latest.apk', 'release.json'].map(name => asset(join(root, name))) });
  const run: Runner = (tool, args) => {
    calls.push([tool, ...args]);
    if (tool === 'git') {
      if (args[0] === 'remote') return ok('https://github.com/JuanRZAR/appistan.git');
      if (args[0] === 'branch') return ok('main');
      if (args[0] === 'rev-parse') return ok(state.wrongTag ? 'b'.repeat(40) : commit);
      if (['status', 'merge-base', 'push'].includes(args[0]!)) return ok();
    }
    if (tool === 'gh') {
      if (args[0] === 'api') {
        if (args[1] === '--paginate') return ok(JSON.stringify([state.remote ? [state.remote] : []]));
        if (args[1] === '--method') { state.pages = true; return ok(); }
        if (args[1]?.endsWith('/releases/latest')) return state.latest ? ok(JSON.stringify(state.latest)) : missing();
        if (args[1]?.includes('/releases/tags/')) return state.remote && !state.remote.draft ? ok(JSON.stringify(state.remote)) : missing();
        if (args[1]?.endsWith('/pages')) return state.pages ? ok('{"build_type":"workflow"}') : missing();
      }
      if (args[0] === 'release') {
        if (args[1] === 'create') { state.remote = { tag_name: `v${version}`, draft: true, prerelease: false, assets: [] }; return ok(); }
        if (args[1] === 'upload') {
          if (state.failUpload) { state.failUpload = false; return { status: 1, stdout: '', stderr: 'transfer interrupted' }; }
          state.remote!.assets = args.slice(3, args.indexOf('--repo')).map(asset); return ok();
        }
        if (args[1] === 'edit') { state.remote!.draft = false; state.latest = state.remote; return ok(); }
      }
      if (args[0] === 'workflow') return state.failDispatch ? { status: 1, stdout: '', stderr: 'HTTP 403' } : ok();
    }
    throw new Error(`Unexpected command: ${tool} ${args.join(' ')}`);
  };
  const count = (tool: string, command: string, subcommand?: string) => calls.filter(c => c[0] === tool && c[1] === command && (!subcommand || c[2] === subcommand)).length;
  return { root, state, run, calls, published, count, artifact };
}

test('accepts only GitHub origins without embedded credentials', () => {
  expect(repositoryFromOrigin('https://github.com/JuanRZAR/appistan.git')).toBe('JuanRZAR/appistan');
  expect(repositoryFromOrigin('git@github.com:JuanRZAR/appistan.git')).toBe('JuanRZAR/appistan');
  expect(() => repositoryFromOrigin('https://token@github.com/owner/repo.git')).toThrow();
  expect(() => repositoryFromOrigin('https://example.com/owner/repo.git')).toThrow();
});
test('creates a draft, verifies four uploads, publishes and starts Pages automatically', async () => {
  const s = setup();
  expect(await publishRelease(s.root, s.run)).toEndWith('/releases/tag/v0.4.0');
  expect(s.state.remote?.draft).toBe(false); expect(s.state.remote?.assets).toHaveLength(4);
  expect(s.count('gh', 'release', 'create')).toBe(1); expect(s.count('gh', 'workflow', 'run')).toBe(1);
  expect(s.state.pages).toBe(true);
  expect(s.calls.some(c => c[0] === 'gh' && c.includes('--paginate'))).toBe(true);
  const push = s.calls.find(c => c[0] === 'git' && c[1] === 'push')!;
  expect(push).toContain('--atomic'); expect(push).not.toContain('--force');
});
test('retries an interrupted upload using the same draft', async () => {
  const s = setup(); s.state.failUpload = true;
  await expect(publishRelease(s.root, s.run)).rejects.toThrow('interrupted');
  expect(s.state.remote?.draft).toBe(true); expect(s.count('gh', 'release', 'edit')).toBe(0);
  await publishRelease(s.root, s.run);
  expect(s.count('gh', 'release', 'create')).toBe(1); expect(s.count('gh', 'release', 'upload')).toBe(2);
});
test('retries Pages without replacing an already published release', async () => {
  const s = setup(); s.state.failDispatch = true;
  await expect(publishRelease(s.root, s.run)).rejects.toThrow('no se pudo iniciar Pages');
  s.state.failDispatch = false; await publishRelease(s.root, s.run);
  expect(s.count('gh', 'release', 'create')).toBe(1); expect(s.count('gh', 'release', 'upload')).toBe(1);
});
test('accepts a matching legacy release with three assets without uploading it again', async () => {
  const s = setup(); s.state.remote = s.published(); s.state.latest = s.state.remote;
  await publishRelease(s.root, s.run);
  expect(s.count('gh', 'release')).toBe(0); expect(s.count('gh', 'workflow', 'run')).toBe(1);
});
test('refuses different assets under a published version before pushing anything', async () => {
  const s = setup(); s.state.remote = s.published(); s.state.remote.assets[0]!.digest = 'sha256:wrong';
  await expect(publishRelease(s.root, s.run)).rejects.toThrow('no coincide');
  expect(s.count('git', 'push')).toBe(0); expect(s.count('gh', 'release')).toBe(0);
});
test('refuses a tag that does not identify the APK commit', async () => {
  const s = setup(); s.state.wrongTag = true;
  await expect(publishRelease(s.root, s.run)).rejects.toThrow('etiqueta'); expect(s.count('git', 'push')).toBe(0);
});
test('refuses rollback but compares newer versions numerically', async () => {
  const s = setup(); s.state.latest = { ...s.published(), tag_name: 'v0.5.0' };
  await expect(publishRelease(s.root, s.run)).rejects.toThrow('anterior'); expect(s.count('git', 'push')).toBe(0);
  const newer = setup('0.10.0'); newer.state.latest = { ...newer.published(), tag_name: 'v0.9.0' };
  await publishRelease(newer.root, newer.run); expect(newer.state.remote?.tag_name).toBe('v0.10.0');
});
test('rejects a corrupt local APK before invoking Git or GitHub', async () => {
  const s = setup(); writeFileSync(join(s.root, s.artifact), 'corrupt');
  await expect(publishRelease(s.root, s.run)).rejects.toThrow('SHA-256'); expect(s.calls).toHaveLength(0);
});
