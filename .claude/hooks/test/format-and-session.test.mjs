import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, rmSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { runHook, REPO_ROOT } from './helpers.mjs';

const tmp = (prefix) => mkdtempSync(path.join(tmpdir(), prefix));
// format-on-edit solo formatea archivos dentro del proyecto: temporales bajo .gate/ (gitignored).
const tmpInRepo = (prefix) => {
  const base = path.join(REPO_ROOT, '.gate');
  mkdirSync(base, { recursive: true });
  return mkdtempSync(path.join(base, prefix));
};
const edit = (file_path) => ({ tool_name: 'Write', tool_input: { file_path } });
const hasPrettier = existsSync(path.join(REPO_ROOT, 'node_modules', 'prettier'));

test('format-on-edit: sale 0 si prettier no está instalado', () => {
  const dir = tmp('mada-fmt-');
  try {
    writeFileSync(path.join(dir, 'package.json'), '{}');
    const f = path.join(dir, 'a.ts');
    writeFileSync(f, 'const   x=1');
    const r = runHook('format-on-edit.mjs', edit(f), { CLAUDE_PROJECT_DIR: dir });
    assert.equal(r.code, 0);
    assert.equal(readFileSync(f, 'utf8'), 'const   x=1');
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test('format-on-edit: archivo inexistente sale 0', () => {
  const r = runHook('format-on-edit.mjs', edit('no/existe.ts'), { CLAUDE_PROJECT_DIR: REPO_ROOT });
  assert.equal(r.code, 0);
});

test('format-on-edit: no toca archivos fuera del proyecto', () => {
  const dir = tmp('mada-fmt-out-');
  try {
    const f = path.join(dir, 'a.json');
    writeFileSync(f, '{"a":1,   "b":[1,2]}');
    const r = runHook('format-on-edit.mjs', edit(f), { CLAUDE_PROJECT_DIR: REPO_ROOT });
    assert.equal(r.code, 0, r.stderr);
    assert.equal(readFileSync(f, 'utf8'), '{"a":1,   "b":[1,2]}');
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test('format-on-edit: entrada ilegible nunca bloquea', () => {
  assert.equal(runHook('format-on-edit.mjs', 'xx').code, 0);
});

test(
  'format-on-edit: formatea con prettier del repo',
  { skip: !hasPrettier && 'prettier no instalado' },
  () => {
    const dir = tmpInRepo('mada-fmt-ok-');
    try {
      const f = path.join(dir, 'a.json');
      writeFileSync(f, '{"a":1,   "b":[1,2]}');
      const r = runHook('format-on-edit.mjs', edit(f), { CLAUDE_PROJECT_DIR: REPO_ROOT });
      assert.equal(r.code, 0, r.stderr);
      assert.notEqual(readFileSync(f, 'utf8'), '{"a":1,   "b":[1,2]}');
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  },
);

test(
  'format-on-edit: error de sintaxis sale 2',
  { skip: !hasPrettier && 'prettier no instalado' },
  () => {
    const dir = tmpInRepo('mada-fmt-err-');
    try {
      const f = path.join(dir, 'roto.ts');
      writeFileSync(f, 'const x = {');
      const r = runHook('format-on-edit.mjs', edit(f), { CLAUDE_PROJECT_DIR: REPO_ROOT });
      assert.equal(r.code, 2);
      assert.match(r.stderr, /error de sintaxis/);
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  },
);

test('session-context: imprime resumen de .mada/estado.json', () => {
  const dir = tmp('mada-ses-');
  try {
    mkdirSync(path.join(dir, '.mada'));
    writeFileSync(
      path.join(dir, '.mada', 'estado.json'),
      JSON.stringify({
        fase: 'C1',
        capacidad: 'clientes',
        rama: 'feat/c1',
        pr: 3,
        ultimoGate: 'verde',
      }),
    );
    const r = runHook(
      'session-context.mjs',
      { hook_event_name: 'SessionStart' },
      { CLAUDE_PROJECT_DIR: dir },
    );
    assert.equal(r.code, 0);
    assert.match(r.stdout, /Fase: C1/);
    assert.match(r.stdout, /Rama: feat\/c1/);
    assert.match(r.stdout, /Último gate: verde/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test('session-context: sin estado no imprime nada', () => {
  const dir = tmp('mada-ses-vacio-');
  try {
    const r = runHook('session-context.mjs', {}, { CLAUDE_PROJECT_DIR: dir });
    assert.equal(r.code, 0);
    assert.equal(r.stdout, '');
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});
