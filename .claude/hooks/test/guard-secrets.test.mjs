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

// Origen de cp/mv/Copy-Item/Move-Item, buscadores, git show, redirección de entrada e intérpretes.
for (const c of [
  'cp .env /tmp/x',
  'mv .env.local backup.txt',
  'Copy-Item -Path .env -Destination out.txt',
  'Copy-Item .env out.txt',
  'Move-Item -LiteralPath secrets/k.txt -Destination x.txt',
  'grep TOKEN .env',
  'egrep -i "pass" .env.local',
  'rg -n KEY .env',
  'grep -r TOKEN --include=.env .',
  "sed -n '1,5p' .env",
  "awk '{print}' .env",
  'findstr PASS .env',
  'Select-String -Path .env -Pattern "x"',
  'sls TOKEN .env',
  'git show HEAD:.env',
  'git show :.env',
  'git -C sub show HEAD:secrets/a.txt',
  'git cat-file -p HEAD:.env',
  'wc -l < .env',
  "node -e \"console.log(require('fs').readFileSync('.env','utf8'))\"",
  "node --eval \"require('fs').readFileSync('backend/.env.local')\"",
  'python -c "print(open(\'.env\').read())"',
  'python3 -c "import os; open(\'id_rsa\')"',
]) {
  test(`bloquea lectura/copia: ${c}`, () => {
    assert.equal(runHook(HOOK, bash(c)).code, 2);
  });
}

for (const c of [
  'grep TOKEN .env.example',
  'rg -n KEY src',
  "sed -n '1p' README.md",
  'git show HEAD:README.md',
  'git show HEAD',
  'wc -l < package.json',
  'cat <<EOF\nhola\nEOF',
  'node -e "console.log(1)"',
  'python -c "print(open(\'.env.example\').read())"',
  'Copy-Item -Path .env.example -Destination .env.sample',
]) {
  test(`permite lectura/copia: ${c}`, () => {
    assert.equal(runHook(HOOK, bash(c)).code, 0);
  });
}

// Herramientas Grep y Glob.
const grep = (tool_input) => ({ tool_name: 'Grep', cwd: REPO_ROOT, tool_input });
const glob = (tool_input) => ({ tool_name: 'Glob', cwd: REPO_ROOT, tool_input });

for (const [nombre, input] of [
  ['Grep path .env', grep({ pattern: 'TOKEN', path: '.env' })],
  ['Grep path backend/.env.local', grep({ pattern: 'x', path: 'backend/.env.local' })],
  ['Grep path dir secrets', grep({ pattern: 'x', path: 'ops/secrets' })],
  ['Grep glob .env*', grep({ pattern: 'x', glob: '.env*' })],
  ['Grep glob **/*.pem', grep({ pattern: 'x', glob: '**/*.pem' })],
  ['Grep glob *.{ts,key}', grep({ pattern: 'x', glob: '*.{ts,key}' })],
  ['Glob pattern **/.env*', glob({ pattern: '**/.env*' })],
  ['Glob path secrets', glob({ pattern: '*', path: 'secrets' })],
  ['Grep path no string (fail-closed)', grep({ pattern: 'x', path: 42 })],
]) {
  test(`bloquea ${nombre}`, () => {
    assert.equal(runHook(HOOK, input).code, 2);
  });
}

for (const [nombre, input] of [
  ['Grep sin path', grep({ pattern: 'TOKEN' })],
  ['Grep path src', grep({ pattern: 'x', path: 'src' })],
  ['Grep glob *.ts', grep({ pattern: 'x', glob: '*.ts' })],
  ['Grep glob .env.example', grep({ pattern: 'x', glob: '.env.example' })],
  ['Grep regex de contenido con .env', grep({ pattern: '\\.env' })],
  ['Glob pattern **/*', glob({ pattern: '**/*' })],
  ['Glob pattern **/*.mjs', glob({ pattern: '**/*.mjs' })],
]) {
  test(`permite ${nombre}`, () => {
    assert.equal(runHook(HOOK, input).code, 0);
  });
}

test('Read de .env.local con ruta absoluta Windows se bloquea', () => {
  const r = runHook(HOOK, { tool_name: 'Read', tool_input: { file_path: 'C:\\r\\.env.local' } });
  assert.equal(r.code, 2);
});

test('fail-closed ante JSON ilegible', () => {
  assert.equal(runHook(HOOK, '{no es json').code, 2);
});
