import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, utimesSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { runHook } from './helpers.mjs';

const HOOK = 'bitacora-on-stop.mjs';

function fixture(lines) {
  const dir = mkdtempSync(path.join(tmpdir(), 'mada-bitacora-'));
  mkdirSync(path.join(dir, 'docs'));
  mkdirSync(path.join(dir, '.mada'));
  const bit = path.join(dir, 'docs', 'bitacora-fallas.md');
  writeFileSync(bit, '| # | fecha |\n');
  const old = new Date('2026-01-01T00:00:00Z');
  utimesSync(bit, old, old);
  if (lines)
    writeFileSync(
      path.join(dir, '.mada', 'fallas.jsonl'),
      lines.map((l) => JSON.stringify(l)).join('\n') + '\n',
    );
  return dir;
}

const falla = (paso, sha, ts = new Date().toISOString()) => ({
  ts,
  fase: 'C1',
  paso,
  cmd: 'npm run gate',
  exitCode: 1,
  resumen: 'falló',
  sha,
});

test('avisa con systemMessage si hay fallas posteriores a la bitácora', () => {
  const dir = fixture([falla('backend:test', 'abc'), falla('spec:validate', 'abc')]);
  try {
    const r = runHook(
      HOOK,
      { hook_event_name: 'Stop', stop_hook_active: false },
      { CLAUDE_PROJECT_DIR: dir },
    );
    assert.equal(r.code, 0);
    const out = JSON.parse(r.stdout);
    assert.match(out.systemMessage, /Aviso MADA: hay 2 fallas de gate/);
    assert.match(out.systemMessage, /backend:test, spec:validate/);
    assert.doesNotMatch(out.systemMessage, /REGLA DE PARADA/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test('regla de parada con 3 fallas seguidas del mismo paso y sha', () => {
  const dir = fixture([falla('e2e', 'abc'), falla('e2e', 'abc'), falla('e2e', 'abc')]);
  try {
    const r = runHook(HOOK, {}, { CLAUDE_PROJECT_DIR: dir });
    assert.match(JSON.parse(r.stdout).systemMessage, /REGLA DE PARADA/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test('modo block devuelve decision:block', () => {
  const dir = fixture([falla('lint', 'abc')]);
  try {
    const r = runHook(HOOK, {}, { CLAUDE_PROJECT_DIR: dir, MADA_BITACORA_MODE: 'block' });
    const out = JSON.parse(r.stdout);
    assert.equal(out.decision, 'block');
    assert.match(out.reason, /Aviso MADA/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test('fallas anteriores a la bitácora no avisan', () => {
  const dir = fixture([falla('lint', 'abc', '2025-06-01T00:00:00Z')]);
  try {
    const r = runHook(HOOK, {}, { CLAUDE_PROJECT_DIR: dir });
    assert.equal(r.code, 0);
    assert.equal(r.stdout.trim(), '');
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test('stop_hook_active sale sin aviso', () => {
  const dir = fixture([falla('lint', 'abc')]);
  try {
    const r = runHook(HOOK, { stop_hook_active: true }, { CLAUDE_PROJECT_DIR: dir });
    assert.equal(r.code, 0);
    assert.equal(r.stdout.trim(), '');
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test('sin .mada/fallas.jsonl sale con 0 y sin salida', () => {
  const dir = fixture(null);
  try {
    const r = runHook(HOOK, {}, { CLAUDE_PROJECT_DIR: dir });
    assert.equal(r.code, 0);
    assert.equal(r.stdout.trim(), '');
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});
