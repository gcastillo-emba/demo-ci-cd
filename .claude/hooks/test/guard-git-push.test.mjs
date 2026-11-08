import { test } from 'node:test';
import assert from 'node:assert/strict';
import { runHook, REPO_ROOT } from './helpers.mjs';

const HOOK = 'guard-git-push.mjs';
const bash = (command, tool_name = 'Bash') => ({
  tool_name,
  cwd: REPO_ROOT,
  tool_input: { command },
});

const BLOCKED = [
  'git push --force',
  'git push -f origin feat/x',
  'git push -uf origin feat/x',
  'git push --force-with-lease origin feat/x',
  'git push --force-if-includes origin feat/x',
  'git push --mirror origin',
  'git push origin main',
  'git push origin master',
  'git push origin HEAD:main',
  'git push origin :main',
  'git push origin refs/heads/main',
  'git push origin feat/x:main',
  'git push --delete origin main',
  'git push origin +feat/x',
  'git push origin v0.1.0',
  'git push origin refs/tags/v0.1.0',
  'git push --tags',
  'npm test && git push origin main',
  'git -C "otro dir" push origin main',
  'bash -c "git push --force"',
  'gh pr merge 3 --squash',
  'gh pr merge 3 --squash --admin --match-head-commit abc',
  'gh pr merge 3 --squash --match-head-commit abc',
  'gh api repos/o/r/pulls/3/merge -X PUT',
  'git push origin HEAD:refs/heads/main',
  'git -C . push --force-with-lease',
];
for (const c of BLOCKED) {
  test(`bloquea: ${c}`, () => {
    const r = runHook(HOOK, bash(c));
    assert.equal(r.code, 2, `stdout=${r.stdout} stderr=${r.stderr}`);
    assert.match(r.stderr, /política MADA/);
  });
}

test('mensaje específico para push de tag', () => {
  const r = runHook(HOOK, bash('git push origin v0.1.0'));
  assert.match(
    r.stderr,
    /Push de tag de release: acción externa irreversible; la confirma y ejecuta el humano/,
  );
});

const ALLOWED = [
  'git push -u origin feat/c1',
  'git push origin feat/c1:feat/c1',
  'node scripts/merge-si-verde.mjs 3',
  'gh api repos/o/r/pulls/3',
  'git status',
  'git commit -m "push a main en otra ocasión"',
  'git log --oneline | head -5',
  'gh pr view 3',
];
for (const c of ALLOWED) {
  test(`permite: ${c}`, () => {
    const r = runHook(HOOK, bash(c));
    assert.equal(r.code, 0, r.stderr);
  });
}

test('PowerShell también se analiza', () => {
  assert.equal(runHook(HOOK, bash('git push origin main', 'PowerShell')).code, 2);
});

test('fail-closed ante JSON ilegible', () => {
  assert.equal(runHook(HOOK, 'no-json').code, 2);
});
