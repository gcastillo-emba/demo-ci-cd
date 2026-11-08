import { test } from 'node:test';
import assert from 'node:assert/strict';
import { quote, commandLine, capture } from '../lib/run.mjs';

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
