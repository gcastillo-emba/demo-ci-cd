import { test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync } from 'node:fs';
import { basename, isAbsolute } from 'node:path';
import { quote, commandLine, capture, mvnwCommand } from '../lib/run.mjs';

test('quote deja intactos los argumentos simples', () => {
  assert.equal(quote('verify'), 'verify');
});

test('quote cita rutas con espacios', () => {
  assert.match(quote('C:/gcastillo/Sistemas IA/x'), /^["'].*["']$/);
});

test('commandLine une comando y argumentos', () => {
  assert.equal(commandLine('node', ['-v']), 'node -v');
});

test('capture ejecuta node en cualquier SO', () => {
  const r = capture('node', ['-e', 'console.log("ok espacio")']);
  assert.equal(r.status, 0);
  assert.match(r.stdout, /ok espacio/);
});

test('mvnwCommand usa ruta absoluta al wrapper del backend (bitácora #12)', () => {
  for (const win of [true, false]) {
    const { cmd, cwd } = mvnwCommand(win);
    assert.ok(isAbsolute(cmd), `ruta no absoluta: ${cmd}`);
    assert.equal(basename(cmd), win ? 'mvnw.cmd' : 'mvnw');
    assert.equal(basename(cwd.replace(/[\/]$/, '')), 'backend');
    assert.ok(existsSync(cmd), `no existe ${cmd}`);
  }
});
