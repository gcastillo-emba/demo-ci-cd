import { test } from 'node:test';
import assert from 'node:assert/strict';
import { checkCount, findCases, formatTable, EXPECTED_CASES } from '../evals.mjs';

test('el repositorio tiene exactamente 10 casos de eval', () => {
  assert.equal(EXPECTED_CASES, 10);
  assert.equal(findCases().length, 10);
  assert.equal(checkCount(findCases()), null);
});

test('checkCount falla si hay menos o más de 10 casos', () => {
  assert.match(checkCount([]), /exactamente 10 .* hay 0/);
  assert.match(checkCount(Array.from({ length: 9 }, (_, i) => `${i}.yaml`)), /hay 9/);
  assert.match(checkCount(Array.from({ length: 11 }, (_, i) => `${i}.yaml`)), /hay 11/);
});

test('formatTable muestra cada caso con su resultado y detalle', () => {
  const table = formatTable([
    { id: '01-a', status: 200, provider: 'fake', passed: true, failures: [] },
    { id: '02-b', status: 503, provider: '', passed: false, failures: ['estado 503', 'otro'] },
  ]);
  const lines = table.split('\n');
  assert.equal(lines.length, 4);
  assert.match(lines[0], /^Caso/);
  assert.match(lines[2], /01-a +\| 200 +\| fake +\| PASA/);
  assert.match(lines[3], /02-b +\| 503 +\| - +\| FALLA +\| estado 503; otro$/);
});
