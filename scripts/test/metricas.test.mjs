import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  parseArgs,
  tabla,
  duracion,
  tipoPr,
  resumenPrs,
  fases,
  tipoFalla,
  origen,
  veredictos,
  filasBitacora,
  categoriaBitacora,
  jacocoCapas,
  filaGate,
  reemplazarBloque,
  resumenCorridas,
  bloque,
  INICIO,
  FIN,
} from '../metricas.mjs';

test('resumenCorridas cuenta por workflow y conclusión', () => {
  const r = resumenCorridas([
    { workflowName: 'CI', conclusion: 'success' },
    { workflowName: 'CI', conclusion: 'failure' },
    { workflowName: 'CI', conclusion: '', status: 'in_progress' },
    { workflowName: 'Claude review', conclusion: 'skipped' },
  ]);
  assert.deepEqual(r, {
    CI: { total: 3, success: 1, failure: 1, in_progress: 1 },
    'Claude review': { total: 1, skipped: 1 },
  });
});

test('categoriaBitacora: el 403 de F0 no es CodeQL y push --force es herramienta', () => {
  const f403 = {
    que: 'Branch protection, CodeQL, dependency-review y secret scanning devuelven 403 en el repo.',
    causa: 'Repo privado en plan GitHub Free.',
  };
  assert.equal(categoriaBitacora(f403), 'entorno / dependencias');
  const fForce = {
    que: 'Los PRs #11 y #12 se cerraron y se reemplazaron por ramas `-v2`.',
    causa: 'El hook bloquea `push --force`.',
  };
  assert.equal(categoriaBitacora(fForce), 'herramienta / Claude Code');
});

test('bloque arma todas las secciones con datos mínimos', () => {
  const md = bloque({
    generado: '2026-11-08T12:00:00.000Z',
    repo: 'o/r',
    fases: [],
    prs: [
      {
        number: 1,
        title: 'feat(c1): a|b',
        state: 'MERGED',
        createdAt: '2026-11-08T01:00:00Z',
        mergedAt: '2026-11-08T01:10:00Z',
        author: { login: 'x' },
      },
    ],
    resumenPrs: { feat: 1 },
    corridas: { CI: { total: 2, success: 1, failure: 1 } },
    reintentos: 1,
    fallas: [
      {
        createdAt: '2026-11-08T02:00:00Z',
        workflowName: 'CI',
        headBranch: 'dependabot/npm/x',
        attempt: 1,
        jobs: ['backend'],
        tipos: ['CI mecánico'],
      },
    ],
    veredictos: { 1: { aprobado: 1, cambios: 1, seguridad: 1, retenida: 0 } },
    bitacora: [{ n: 1, fase: 'F0', que: 'x', causa: 'y' }],
    jacoco: null,
    gates: [],
  });
  for (const s of [
    '### Tiempo por fase',
    '### Pull requests',
    '### Corridas de GitHub Actions',
    '### Fallas de gates mecánicos en CI',
    '### Gates de juicio',
    '### Bitácora de fallas',
    '### Cobertura JaCoCo',
    '### Gate local',
  ]) {
    assert.ok(md.includes(s), s);
  }
  assert.match(md, /feat\(c1\): a\\\|b/);
  assert.match(md, /\| CI mecánico \| 0 \| 1 \|/);
  assert.match(md, /\*\*1\*\*/);
  assert.match(md, /Totales: 1 CAMBIOS SOLICITADOS, 1 APROBADO/);
});

const pr = (number, title, extra = {}) => ({
  number,
  title,
  state: 'MERGED',
  createdAt: '2026-11-08T01:00:00Z',
  mergedAt: '2026-11-08T01:30:00Z',
  author: { login: 'gcastillo-emba', is_bot: false },
  ...extra,
});

test('parseArgs: valores por defecto y --gate repetible', () => {
  assert.deepEqual(parseArgs([]), {
    salida: undefined,
    mada: '.mada',
    gates: ['.gate/last-run.json'],
    jacoco: true,
    stdout: true,
  });
  const f = parseArgs(['--salida', 'x.md', '--gate', 'a.json', '--gate', 'b.json', '--sin-jacoco']);
  assert.equal(f.salida, 'x.md');
  assert.deepEqual(f.gates, ['a.json', 'b.json']);
  assert.equal(f.jacoco, false);
  assert.equal(f.stdout, false);
});

test('tabla escapa barras verticales y usa — para vacíos', () => {
  assert.equal(tabla(['a', 'b'], [['x|y', null]]), '| a | b |\n| --- | --- |\n| x\\|y | — |');
});

test('duracion en minutos y horas', () => {
  assert.equal(duracion(null), '—');
  assert.equal(duracion(12 * 60000), '12 min');
  assert.equal(duracion(91 * 60000), '1 h 31 min');
});

test('tipoPr: Conventional Commits, dependabot y reemplazados', () => {
  assert.equal(tipoPr(pr(1, 'feat(c1): x')), 'feat');
  assert.equal(tipoPr(pr(2, 'docs: retro y evidencia (F6)')), 'docs');
  assert.equal(tipoPr(pr(3, 'ci: migrar')), 'ci');
  assert.equal(tipoPr(pr(4, 'fix(c2): y', { state: 'CLOSED', mergedAt: null })), 'reemplazado');
  assert.equal(
    tipoPr(pr(5, 'chore(deps): bump', { author: { login: 'app/dependabot', is_bot: true } })),
    'dependabot',
  );
  const r = resumenPrs([
    pr(6, 'feat(c1): x'),
    pr(7, 'chore(deps): z', { state: 'OPEN', author: { login: 'app/dependabot', is_bot: true } }),
  ]);
  assert.deepEqual(r, { feat: 1, 'dependabot (open)': 1 });
});

test('fases: une F0–F1 y usa el merge del PR cuando falta el fin', () => {
  const eventos = [
    { ts: '2026-11-08T02:00:00.000Z', evento: 'fin', fase: 'F1' },
    { ts: '2026-11-08T02:12:00.000Z', evento: 'inicio', fase: 'F3' },
    { ts: '2026-11-08T09:28:00.000Z', evento: 'inicio', fase: 'C4' },
  ];
  const prs = [
    pr(1, 'chore: encuadre'),
    pr(5, 'feat(skeleton): x', {
      createdAt: '2026-11-08T03:26:00Z',
      mergedAt: '2026-11-08T03:43:00Z',
    }),
    pr(20, 'feat(c4): x', { state: 'OPEN', mergedAt: null }),
  ];
  const [f01, f3, c4] = fases(eventos, '2026-11-08T01:41:00Z', prs);
  assert.equal(f01.fase, 'F0–F1');
  assert.equal(f01.ms, 19 * 60000);
  assert.equal(f3.fuente, 'merge del PR #5');
  assert.equal(f3.ms, 91 * 60000);
  assert.equal(f3.ciclo, 17 * 60000);
  assert.match(c4.fuente, /en curso/);
  assert.equal(c4.ms, null);
});

test('tipoFalla y origen clasifican jobs y ramas', () => {
  assert.equal(tipoFalla('PR title', 'semantic-title'), 'semantic-title');
  assert.equal(tipoFalla('Security', 'codeql (java-kotlin)'), 'CodeQL');
  assert.equal(tipoFalla('CI', 'backend'), 'CI mecánico');
  assert.equal(tipoFalla('Dependabot Updates', 'Dependabot'), 'Dependabot (infraestructura)');
  assert.equal(origen('dependabot/npm_and_yarn/x', 'CI'), 'dependabot');
  assert.equal(origen('main', 'Dependabot Updates'), 'dependabot');
  assert.equal(origen('feat/c4', 'Security'), 'agente');
});

test('veredictos cuenta APROBADO, CAMBIOS SOLICITADOS y etiquetas retenidas', () => {
  const url = (n) => `https://api.github.com/repos/o/r/issues/${n}`;
  const v = veredictos([
    { issue_url: url(1), body: 'VEREDICTO: CAMBIOS SOLICITADOS\n...' },
    { issue_url: url(1), body: 'texto\nVEREDICTO: APROBADO' },
    { issue_url: url(1), body: '## Revisión de seguridad (ASVS)\n**No pongo la etiqueta**' },
    { issue_url: url(2), body: 'Menciono VEREDICTO: APROBADO en línea, no cuenta' },
  ]);
  assert.deepEqual(v[1], { aprobado: 1, cambios: 1, seguridad: 1, retenida: 1 });
  assert.deepEqual(v[2], { aprobado: 0, cambios: 0, seguridad: 0, retenida: 0 });
});

test('filasBitacora lee la tabla y categoriaBitacora agrupa', () => {
  const md = [
    '| #   | Fecha | Fase | Qué pasó | Causa | Resolución | Lección |',
    '| --- | --- | --- | --- | --- | --- | --- |',
    '| 1 | 2026-11-08 | F1 | CodeQL en FAIL en PR #1 | falso positivo | x | y |',
    '| 2 | 2026-11-08 | F4 | Clasificador de modo auto bloqueó gh pr comment | permisos | x | y |',
  ].join('\n');
  const filas = filasBitacora(md);
  assert.deepEqual(
    filas.map((f) => [f.n, f.fase]),
    [
      [1, 'F1'],
      [2, 'F4'],
    ],
  );
  assert.equal(categoriaBitacora(filas[0]), 'CodeQL');
  assert.equal(categoriaBitacora(filas[1]), 'herramienta / Claude Code');
});

test('jacocoCapas agrega solo domain y application', () => {
  const csv = [
    'GROUP,PACKAGE,CLASS,IM,IC,BM,BC,LM,LC',
    'g,com.emba.asuntoshoras.domain.user,A,0,10,0,2,0,4',
    'g,com.emba.asuntoshoras.application.service,B,1,9,1,1,0,6',
    'g,com.emba.asuntoshoras.adapters.in.web,C,50,50,5,5,9,9',
  ].join('\n');
  assert.deepEqual(jacocoCapas(csv), {
    instrucciones: '95.0 %',
    ramas: '75.0 %',
    lineas: '100.0 %',
  });
});

test('filaGate resume total, paso más lento y fallas', () => {
  const fila = filaGate('c4', {
    ts: '2026-11-08T10:20:17.065Z',
    args: ['--e2e'],
    steps: [
      { id: 'spec', status: 'PASS', ms: 2000 },
      { id: 'backend', status: 'FAIL', ms: 100000 },
    ],
  });
  assert.deepEqual(fila, [
    'c4',
    '2026-11-08 10:20',
    '--e2e',
    '102.0 s',
    'backend (100.0 s)',
    'backend',
  ]);
});

test('reemplazarBloque solo toca lo que está entre marcadores', () => {
  const doc = `# T\n\ntexto\n\n${INICIO}\nviejo\n${FIN}\n\ncola\n`;
  const nuevo = reemplazarBloque(doc, 'nuevo');
  assert.equal(nuevo, `# T\n\ntexto\n\n${INICIO}\n\nnuevo\n\n${FIN}\n\ncola\n`);
  assert.throws(() => reemplazarBloque('# sin marcadores', 'x'), /marcadores/);
});
