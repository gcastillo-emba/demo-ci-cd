import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdirSync, writeFileSync, existsSync, rmSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { acquire, lockPath, isStale, defaultLockDir } from '../lib/lock.mjs';

// Directorio de pruebas dentro del repo (.gate/ está en .gitignore), no en el /tmp compartido.
const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
let seq = 0;
const scratch = () => {
  const d = join(ROOT, '.gate', 'lock-tests', `${process.pid}-${seq++}`);
  rmSync(d, { recursive: true, force: true });
  mkdirSync(d, { recursive: true });
  return d;
};

test('el directorio por defecto es por usuario, no el temporal compartido', () => {
  const d = defaultLockDir({ LOCALAPPDATA: 'C:/U/AppData/Local', XDG_CACHE_HOME: '/home/u/.c' });
  assert.match(d.replace(/\\/g, '/'), /asuntos-horas\/locks$/);
});

test('acquire crea y libera el candado', () => {
  const dir = scratch();
  const release = acquire('x', { dir });
  assert.ok(existsSync(lockPath('x', dir)));
  release();
  assert.ok(!existsSync(lockPath('x', dir)));
});

test('un segundo acquire espera y falla por timeout mientras el primero vive', () => {
  const dir = scratch();
  const release = acquire('y', { dir });
  assert.throws(() => acquire('y', { dir, timeoutMs: 50, pollMs: 10 }), /candado y/);
  release();
});

test('un candado de un proceso muerto se considera abandonado y se retira', () => {
  const dir = scratch();
  const p = lockPath('z', dir);
  mkdirSync(p);
  writeFileSync(
    join(p, 'owner.json'),
    JSON.stringify({ pid: 999999, since: new Date().toISOString() }),
  );
  assert.equal(isStale(p, 60_000), true);
  const release = acquire('z', { dir, timeoutMs: 100, pollMs: 10 });
  release();
});

test('release no borra un candado que ya no es propio', () => {
  const dir = scratch();
  const release = acquire('w', { dir });
  const p = lockPath('w', dir);
  writeFileSync(
    join(p, 'owner.json'),
    JSON.stringify({ pid: process.pid + 1, since: new Date().toISOString() }),
  );
  release();
  assert.ok(existsSync(p));
});
