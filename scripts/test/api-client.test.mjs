import { test } from 'node:test';
import assert from 'node:assert/strict';
import { normalize, generate, HEADER } from '../api-client.mjs';
import { findCases } from '../evals.mjs';

test('normalize convierte CRLF en LF', () => {
  assert.equal(normalize('a\r\nb\r\n'), 'a\nb\n');
});

test('generate produce el cliente con cabecera y operaciones del contrato', async () => {
  const out = await generate();
  assert.ok(out.startsWith(HEADER));
  assert.doesNotMatch(out, /\r\n/);
  for (const op of ['login', 'getMe', 'getFeatures']) assert.match(out, new RegExp(`\\b${op}\\b`));
});

test('findCases devuelve lista vacía si la carpeta no existe', () => {
  assert.deepEqual(findCases('carpeta/que/no/existe'), []);
});
