// Utilidades compartidas por los tests de hooks.
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

export const HOOKS_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export const REPO_ROOT = path.resolve(HOOKS_DIR, '..', '..');

/** Ejecuta un hook con `input` (objeto o string) por stdin. */
export function runHook(name, input, env = {}) {
  const hookPath = path.join(HOOKS_DIR, name);
  const res = spawnSync(process.execPath, [hookPath], {
    input: typeof input === 'string' ? input : JSON.stringify(input),
    encoding: 'utf8',
    env: { ...process.env, CLAUDE_PROJECT_DIR: '', ...env },
    timeout: 30000,
  });
  return { code: res.status, stdout: res.stdout ?? '', stderr: res.stderr ?? '' };
}
