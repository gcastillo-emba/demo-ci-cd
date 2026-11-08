import { test } from 'node:test';
import assert from 'node:assert/strict';
import { PROMPT, parseArgs, claudeArgs, documento, main } from '../claude-resumen-bitacora.mjs';
import { parseArgs as demoArgs } from '../demo-check.mjs';

test('claudeArgs arma el modo headless: JSON, 3 turnos y solo Read', () => {
  const args = claudeArgs('hola');
  assert.deepEqual(args, [
    '-p',
    'hola',
    '--output-format',
    'json',
    '--max-turns',
    '3',
    '--allowedTools',
    'Read',
  ]);
});

test('el prompt es una sola línea sin caracteres que la shell interprete', () => {
  assert.doesNotMatch(PROMPT, /[\n"%!`$]/);
  assert.match(PROMPT, /docs\/bitacora-fallas\.md/);
  assert.match(PROMPT, /Top 5 lecciones/);
});

test('parseArgs: --dry-run y --salida con valor por defecto', () => {
  assert.deepEqual(parseArgs([]), { dryRun: false, salida: 'docs/bitacora-resumen.md' });
  assert.deepEqual(parseArgs(['--dry-run', '--salida', 'x.md']), { dryRun: true, salida: 'x.md' });
});

test('documento agrega la cabecera headless con fecha, modelo y costo', () => {
  const md = documento('## Panorama\n\nTexto.', {
    fecha: new Date('2026-11-08T12:00:00Z'),
    modelo: 'm',
    costo: 0.5,
  });
  assert.match(md, /^# Resumen ejecutivo de la bitácora de fallas\n/);
  assert.match(
    md,
    /modo headless con `claude -p`.*2026-11-08 · modelo: m · costo informado: USD 0\.5000/,
  );
  assert.match(md, /## Panorama\n\nTexto\.\n$/);
});

test('--dry-run imprime el comando y no ejecuta claude', async () => {
  const original = console.log;
  const lines = [];
  console.log = (l) => lines.push(l);
  try {
    assert.equal(await main(['--dry-run']), 0);
  } finally {
    console.log = original;
  }
  assert.match(
    lines.join('\n'),
    /^claude -p .* --output-format json --max-turns 3 --allowedTools Read$/,
  );
});

test('demo-check: parseArgs de --tag, --keep y --metricas', () => {
  assert.deepEqual(demoArgs([]), { tag: undefined, keep: false, metricas: undefined });
  assert.deepEqual(demoArgs(['--tag', 'v0.1.0', '--keep', '--metricas', 'm.txt']), {
    tag: 'v0.1.0',
    keep: true,
    metricas: 'm.txt',
  });
});
