import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  evaluarPr,
  evaluarFrescura,
  evaluarEtiquetasVsReferencia,
  evaluarVeredicto,
  referenciaCiOk,
  parseEventos,
  citaSha,
} from '../merge-si-verde.mjs';

const SHA = 'abcdef1234567890abcdef1234567890abcdef12';
const CI_START = '2026-11-07T10:00:00Z';
const ciOk = (extra = {}) => ({
  __typename: 'CheckRun',
  name: 'ci-ok',
  status: 'COMPLETED',
  conclusion: 'SUCCESS',
  startedAt: CI_START,
  completedAt: '2026-11-07T10:01:00Z',
  ...extra,
});
const labeled = (name, created_at) => ({ event: 'labeled', label: { name }, created_at });
const eventosOk = [
  labeled('revisor:aprobado', '2026-11-07T10:05:00Z'),
  labeled('seguridad:ok', '2026-11-07T10:06:00Z'),
];
const veredicto = {
  authorAssociation: 'OWNER',
  body: `VEREDICTO: APROBADO\nCommit revisado: ${SHA.slice(0, 7)}`,
};
const okRes = (eventos) => ({ status: 0, stdout: JSON.stringify([eventos]), stderr: '' });

const base = (over = {}) => ({
  checks: [ciOk()],
  eventosRes: okRes(eventosOk),
  comentarios: [veredicto],
  sha: SHA,
  ...over,
});

test('camino feliz: etiquetas posteriores a ci-ok y veredicto con SHA => ok', () => {
  assert.deepEqual(evaluarFrescura(base()), []);
});

test('evaluarPr acepta un PR abierto con ci-ok verde y etiquetas', () => {
  const data = {
    state: 'OPEN',
    isDraft: false,
    statusCheckRollup: [ciOk()],
    labels: [{ name: 'revisor:aprobado' }, { name: 'seguridad:ok' }],
  };
  assert.deepEqual(evaluarPr(data), []);
});

test('etiquetas anteriores al inicio de ci-ok => rechazo', () => {
  const viejas = [
    labeled('revisor:aprobado', '2026-11-07T09:59:00Z'),
    labeled('seguridad:ok', '2026-11-07T10:06:00Z'),
  ];
  const m = evaluarFrescura(base({ eventosRes: okRes(viejas) }));
  assert.equal(m.length, 1);
  assert.match(m[0], /revisor:aprobado/);
});

test('etiqueta igual al inicio de ci-ok => rechazo (debe ser estrictamente posterior)', () => {
  const m = evaluarEtiquetasVsReferencia(
    [labeled('revisor:aprobado', CI_START), labeled('seguridad:ok', '2026-11-07T10:06:00Z')],
    CI_START,
  );
  assert.equal(m.length, 1);
});

test('se usa la última aplicación de cada etiqueta', () => {
  const ev = [...eventosOk, labeled('revisor:aprobado', '2026-11-07T09:00:00Z')];
  assert.deepEqual(evaluarEtiquetasVsReferencia(ev, CI_START), []);
});

test('falta startedAt y completedAt de ci-ok => rechazo', () => {
  const m = evaluarFrescura(base({ checks: [ciOk({ startedAt: null, completedAt: null })] }));
  assert.ok(m.some((x) => /fail-closed/.test(x)));
});

test('fecha nula de GitHub (0001-01-01) cuenta como ausente => rechazo', () => {
  const r = referenciaCiOk([
    ciOk({ startedAt: '0001-01-01T00:00:00Z', completedAt: '0001-01-01T00:00:00Z' }),
  ]);
  assert.ok(r.error);
});

test('sin check ci-ok => rechazo', () => {
  assert.ok(evaluarFrescura(base({ checks: [] })).length > 0);
});

test('completedAt se usa si falta startedAt', () => {
  const r = referenciaCiOk([ciOk({ startedAt: undefined })]);
  assert.equal(r.iso, '2026-11-07T10:01:00Z');
});

test('referencia vacía en evaluarEtiquetasVsReferencia => rechazo', () => {
  assert.equal(evaluarEtiquetasVsReferencia(eventosOk, undefined).length, 1);
});

test('falla de la API de eventos => rechazo (no aviso)', () => {
  const m = evaluarFrescura(base({ eventosRes: { status: 1, stdout: '', stderr: 'HTTP 502' } }));
  assert.ok(m.some((x) => /HTTP 502/.test(x)));
});

test('JSON de eventos ilegible => rechazo', () => {
  const m = evaluarFrescura(base({ eventosRes: { status: 0, stdout: '{no json', stderr: '' } }));
  assert.ok(m.some((x) => /ilegible/.test(x)));
});

test('parseEventos rechaza un objeto que no es arreglo', () => {
  assert.throws(() => parseEventos('{"a":1}'));
});

test('falta el comentario de veredicto => rechazo', () => {
  const m = evaluarFrescura(base({ comentarios: [] }));
  assert.equal(m.length, 1);
  assert.match(m[0], /VEREDICTO: APROBADO/);
});

test('veredicto sin SHA => rechazo', () => {
  assert.equal(
    evaluarVeredicto([{ authorAssociation: 'OWNER', body: 'VEREDICTO: APROBADO' }], SHA).length,
    1,
  );
});

test('veredicto con SHA de otro commit => rechazo', () => {
  assert.equal(
    evaluarVeredicto([{ authorAssociation: 'OWNER', body: 'VEREDICTO: APROBADO 1234567' }], SHA)
      .length,
    1,
  );
});

test('SHA sin la marca de veredicto => rechazo', () => {
  assert.equal(
    evaluarVeredicto(
      [{ authorAssociation: 'OWNER', body: `VEREDICTO: CAMBIOS SOLICITADOS ${SHA}` }],
      SHA,
    ).length,
    1,
  );
});

test('veredicto con SHA completo => ok', () => {
  assert.deepEqual(
    evaluarVeredicto([{ authorAssociation: 'OWNER', body: `VEREDICTO: APROBADO (${SHA})` }], SHA),
    [],
  );
});

test('citaSha exige al menos 7 caracteres', () => {
  assert.equal(citaSha('abcdef', SHA), false);
  assert.equal(citaSha('abcdef1', SHA), true);
});

test('veredicto de autor sin permisos no cuenta', () => {
  assert.equal(
    evaluarVeredicto([{ authorAssociation: 'NONE', body: `VEREDICTO: APROBADO ${SHA}` }], SHA)
      .length,
    1,
  );
});

test('un CAMBIOS SOLICITADOS posterior invalida el APROBADO anterior', () => {
  const cs = [
    { authorAssociation: 'OWNER', body: `VEREDICTO: APROBADO ${SHA}` },
    { authorAssociation: 'OWNER', body: `VEREDICTO: CAMBIOS SOLICITADOS ${SHA}` },
  ];
  assert.equal(evaluarVeredicto(cs, SHA).length, 1);
});
