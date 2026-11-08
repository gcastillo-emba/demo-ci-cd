import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { acquire, lockPath, isStale } from '../lib/lock.mjs';

const tmp = () => mkdtempSync(join(tmpdir(), 'lock-test-'));

test('acquire crea y libera el candado', () => {
  const dir = tmp();
  const release = acquire('x', { dir });
  assert.ok(existsSync(lockPath('x', dir)));
  release();
  assert.ok(!existsSync(lockPath('x', dir)));
});

test('un segundo acquire espera y falla por timeout mientras el primero vive', () => {
  const dir = tmp();
  const release = acquire('y', { dir });
  assert.throws(() => acquire('y', { dir, timeoutMs: 50, pollMs: 10 }), /candado y/);
  release();
});

test('un candado de un proceso muerto se considera abandonado', () => {
  const dir = tmp();
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
