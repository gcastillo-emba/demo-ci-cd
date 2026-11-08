import { test } from 'node:test';
import assert from 'node:assert/strict';
import { runHook, REPO_ROOT } from './helpers.mjs';

const HOOK = 'guard-secrets.mjs';
const write = (file_path) => ({
  tool_name: 'Write',
  cwd: REPO_ROOT,
  tool_input: { file_path, content: 'x' },
});
const bash = (command) => ({ tool_name: 'Bash', cwd: REPO_ROOT, tool_input: { command } });

for (const p of [
  '.env',
  '.env.local',
  'x/secrets/a.txt',
  'id_rsa',
  'C:\\repo\\config\\.env.production',
  'certs/server.pem',
  'keys/app.p12',
  'credentials-prod.json',
  'service-account.json',
  '.npmrc',
  'home/.netrc',
  'ops/secret/token.txt',
  '.ssh/id_ed25519.pub',
]) {
  test(`bloquea escritura en ${p}`, () => {
    const r = runHook(HOOK, write(p));
    assert.equal(r.code, 2, r.stderr);
    assert.match(r.stderr, /archivo de secretos/);
  });
}

for (const p of [
  '.env.example',
  '.env.sample',
  'src/app.ts',
  'docs/secretos.md',
  'environment.ts',
]) {
  test(`permite escritura en ${p}`, () => {
    assert.equal(runHook(HOOK, write(p)).code, 0);
  });
}

test('Read de .env se bloquea', () => {
  const r = runHook(HOOK, { tool_name: 'Read', tool_input: { file_path: 'backend/.env' } });
  assert.equal(r.code, 2);
});

test('NotebookEdit usa notebook_path', () => {
  const r = runHook(HOOK, {
    tool_name: 'NotebookEdit',
    tool_input: { notebook_path: 'secrets/n.ipynb' },
  });
  assert.equal(r.code, 2);
});

for (const c of [
  'echo x > .env',
  'echo x >> .env.local',
  'echo TOKEN=1 | tee .env',
  'Set-Content -Path .env -Value "x"',
  'Out-File -FilePath secrets/k.txt',
  'Add-Content .npmrc "x"',
  'cp .env.example .env',
  'mv tmp.txt id_rsa',
  'Copy-Item .env.example -Destination .env.local',
  'bash -c "echo x > .env"',
  'Set-Content .env x',
  'cat .env',
  'Get-Content -Path backend/.env',
  'source .env.local',
  'cat ~/.ssh/id_rsa',
]) {
  test(`bloquea comando: ${c}`, () => {
    assert.equal(runHook(HOOK, bash(c)).code, 2);
  });
}

for (const c of [
  'cat .env.example',
  'echo hola > salida.txt',
  'cp .env.example backup.txt',
  'npm test 2>&1',
  'git status',
  'echo x > .env.example',
]) {
  test(`permite comando: ${c}`, () => {
    assert.equal(runHook(HOOK, bash(c)).code, 0);
  });
}

test('fail-closed ante JSON ilegible', () => {
  assert.equal(runHook(HOOK, '{no es json').code, 2);
});
