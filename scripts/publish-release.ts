import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { latestRelease, readVerifiedRelease } from '../site/scripts/prepare-release';

type Result = { status: number | null; stdout: string; stderr: string };
export type Runner = (tool: 'git' | 'gh', args: string[]) => Result;
type Asset = { name: string; size: number; digest: string };
type Release = { tag_name: string; draft: boolean; prerelease: boolean; assets: Asset[] };

export function repositoryFromOrigin(origin: string): string {
  const match = origin.trim().match(/^(?:https:\/\/github\.com\/|git@github\.com:)([\w.-]+\/[\w.-]+?)(?:\.git)?\/?$/);
  if (!match) throw new Error('origin debe apuntar a un repositorio de github.com.');
  return match[1]!;
}

function checked(run: Runner, tool: 'git' | 'gh', args: string[]): string {
  const result = run(tool, args);
  if (result.status !== 0) throw new Error(`${tool} ${args[0]}: ${result.stderr.trim() || 'operación fallida'}`);
  return result.stdout.trim();
}

function api<T>(run: Runner, endpoint: string): T | null {
  const result = run('gh', ['api', endpoint]);
  if (result.status === 0) return JSON.parse(result.stdout) as T;
  if (/HTTP 404/.test(result.stderr) || /"status"\s*:\s*"?404/.test(result.stdout)) return null;
  throw new Error(`GitHub no pudo consultar ${endpoint}: ${result.stderr.trim()}`);
}

function releaseByTag(run: Runner, repository: string, tag: string): Release | null {
  const published = api<Release>(run, `repos/${repository}/releases/tags/${tag}`);
  if (published) return published;
  // GitHub's tag endpoint omits drafts; authenticated release listings include them.
  const pages = JSON.parse(checked(run, 'gh', ['api', '--paginate', '--slurp', `repos/${repository}/releases?per_page=100`])) as Release[][];
  const matches = pages.flat().filter(release => release.tag_name === tag);
  if (matches.length > 1) throw new Error('Hay varios borradores para esta etiqueta. Revisa las Releases antes de publicar.');
  return matches[0] || null;
}

function compareVersions(a: string, b: string) {
  if (![a, b].every(value => /^v?\d+\.\d+\.\d+$/.test(value))) throw new Error('La versión publicada no es una versión estable válida.');
  const left = a.replace(/^v/, '').split('.').map(Number), right = b.replace(/^v/, '').split('.').map(Number);
  return left[0]! - right[0]! || left[1]! - right[1]! || left[2]! - right[2]!;
}

export async function publishRelease(directory: string, run: Runner): Promise<string> {
  const { release, apk } = readVerifiedRelease(directory);
  if (!readFileSync(join(directory, 'AppIstan-latest.apk')).equals(apk)) throw new Error('El alias del APK no coincide con la entrega.');
  if (!/^[a-f0-9]{40}$/.test(release.gitCommit)) throw new Error('Commit de entrega inválido.');
  const repository = repositoryFromOrigin(checked(run, 'git', ['remote', 'get-url', 'origin']));
  if (checked(run, 'git', ['branch', '--show-current']) !== 'main') throw new Error('Publica desde la rama main.');
  if (checked(run, 'git', ['status', '--porcelain'])) throw new Error('Guarda los cambios en un commit antes de publicar.');
  const tag = `v${release.version}`;
  if (checked(run, 'git', ['rev-parse', `${tag}^{}`]) !== release.gitCommit) throw new Error('La etiqueta no coincide con el commit del APK.');
  checked(run, 'git', ['merge-base', '--is-ancestor', release.gitCommit, 'HEAD']);
  const latest = api<Release>(run, `repos/${repository}/releases/latest`);
  if (latest && compareVersions(tag, latest.tag_name) < 0) throw new Error('No se publicará una versión anterior a la última Release.');
  let remote = releaseByTag(run, repository, tag);
  const checksum = `${release.artifact}.sha256`;
  writeFileSync(join(directory, checksum), `${release.sha256}  ${release.artifact}\n`);
  const names = [release.artifact, 'AppIstan-latest.apk', 'release.json', checksum];
  const files = names.map(name => {
    const bytes = readFileSync(join(directory, name));
    return { name, size: bytes.length, digest: `sha256:${createHash('sha256').update(bytes).digest('hex')}` };
  });
  const verifyAssets = (state: Release, count: number) => {
    if (state.tag_name !== tag || state.prerelease) throw new Error('La Release no corresponde a la entrega estable.');
    for (const file of files.slice(0, count)) {
      const asset = state.assets.find(asset => asset.name === file.name);
      if (!asset || asset.size !== file.size || asset.digest !== file.digest) throw new Error(`El archivo publicado no coincide: ${file.name}. No se reemplaza una Release publicada.`);
    }
  };
  if (remote && !remote.draft) verifyAssets(remote, 3);
  checked(run, 'git', ['push', '--atomic', 'origin', 'HEAD:refs/heads/main', `refs/tags/${tag}`]);
  if (!remote) {
    checked(run, 'gh', ['release', 'create', tag, '--repo', repository, '--verify-tag', '--draft', '--title', `AppIstan ${release.version}`, '--generate-notes']);
    remote = releaseByTag(run, repository, tag);
    if (!remote) throw new Error('GitHub no devolvió el borrador creado. Reintenta la publicación.');
  }
  if (remote.draft) {
    checked(run, 'gh', ['release', 'upload', tag, ...names.map(name => join(directory, name)), '--repo', repository, '--clobber']);
    const uploaded = releaseByTag(run, repository, tag);
    if (!uploaded) throw new Error('No se pudo comprobar la subida de los archivos.');
    verifyAssets(uploaded, 4);
    checked(run, 'gh', ['release', 'edit', tag, '--repo', repository, '--draft=false', '--latest']);
  }
  const pages = api<{ build_type: string }>(run, `repos/${repository}/pages`);
  if (pages?.build_type !== 'workflow') checked(run, 'gh', ['api', '--method', pages ? 'PUT' : 'POST', `repos/${repository}/pages`, '-f', 'build_type=workflow']);
  // Explicit dispatch also works with GITHUB_TOKEN and on retries of an already published release.
  for (let attempt = 0; attempt < 4; attempt++) {
    const result = run('gh', ['workflow', 'run', 'pages.yml', '--repo', repository, '--ref', 'main']);
    if (result.status === 0) return `https://github.com/${repository}/releases/tag/${tag}`;
    if (attempt === 3 || !/404|not found/i.test(result.stderr)) throw new Error(`Release publicada; no se pudo iniciar Pages. Reintenta Publish-Release.ps1. ${result.stderr.trim()}`);
    await Bun.sleep(1500);
  }
  throw new Error('No se pudo iniciar Pages.');
}

export function authenticatedRunner(root: string): Runner {
  const portable = join(root, '.tools/github-cli/bin/gh.exe');
  const gh = Bun.which('gh') || (existsSync(portable) ? portable : null);
  if (!gh) throw new Error('Instala GitHub CLI una vez: winget install --id GitHub.cli');
  const environment: NodeJS.ProcessEnv = { ...process.env, GIT_TERMINAL_PROMPT: '0', GCM_INTERACTIVE: 'Never' };
  const execute = (tool: 'git' | 'gh', args: string[], input?: string): Result => {
    const result = spawnSync(tool === 'gh' ? gh : 'git', args, { cwd: root, env: environment, input, encoding: 'utf8', windowsHide: true, timeout: 120000 });
    return { status: result.status, stdout: result.stdout || '', stderr: result.error?.message || result.stderr || '' };
  };
  if (execute('gh', ['auth', 'status', '--hostname', 'github.com']).status !== 0) {
    const repository = repositoryFromOrigin(checked(execute, 'git', ['remote', 'get-url', 'origin']));
    const credential = execute('git', ['-c', 'credential.interactive=never', 'credential', 'fill'], `url=https://github.com/${repository}.git\n\n`);
    const password = credential.status === 0 ? credential.stdout.match(/^password=(.+)\r?$/m)?.[1]?.trim() : null;
    if (!password) throw new Error('Inicia sesión en GitHub una vez con gh auth login, o completa un git push con tu cuenta.');
    environment.GH_TOKEN = password;
  }
  return execute;
}

if (import.meta.main) {
  const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
  const args = process.argv.slice(2);
  if (args.length && (args.length !== 2 || args[0] !== '--release-dir')) throw new Error('Uso: publish-release.ts [--release-dir RUTA]');
  const directory = args[1] ? resolve(args[1]) : latestRelease(join(root, 'releases'));
  console.log('Verificando y publicando la entrega en GitHub…');
  console.log(await publishRelease(directory, authenticatedRunner(root)));
  console.log('Release verificada. Actualización de GitHub Pages iniciada.');
}
