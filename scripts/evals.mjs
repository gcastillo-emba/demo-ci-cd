#!/usr/bin/env node
// Runner de evals del Asistente de descripción (C4, design D11). Uso: node scripts/evals.mjs
//
// 1. Verifica que haya EXACTAMENTE 10 casos en evals/description-assistant/cases/ (si no, falla).
// 2. Delega en el backend: `mvnw -B -ntp -Pevals verify` (DescriptionAssistantEvalsIT llama al
//    endpoint real con MockMvc). El proveedor es el de APP_ASSISTANT_PROVIDER (fake por defecto;
//    evals-real.yml usa anthropic) y se hereda del entorno.
// 3. Imprime una tabla con el resultado de cada caso (backend/target/evals/description-assistant.json).
import { existsSync, readdirSync, readFileSync, rmSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { mvnw } from './lib/run.mjs';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
export const CASES_DIR = join(ROOT, 'evals', 'description-assistant', 'cases');
export const REPORT = join(ROOT, 'backend', 'target', 'evals', 'description-assistant.json');
export const EXPECTED_CASES = 10;

/** Lista los archivos de casos (.yaml, .yml, .json) ordenados. */
export function findCases(dir = CASES_DIR) {
  if (!existsSync(dir)) return [];
  return readdirSync(dir)
    .filter((f) => /\.(ya?ml|json)$/i.test(f))
    .sort();
}

/** Mensaje de error si el número de casos no es exactamente EXPECTED_CASES; si no, null. */
export function checkCount(cases) {
  if (cases.length === EXPECTED_CASES) return null;
  return (
    `✗ Se esperaban exactamente ${EXPECTED_CASES} casos de eval y hay ${cases.length}` +
    ' en evals/description-assistant/cases/.'
  );
}

/** Tabla de texto (caso | HTTP | proveedor | resultado | detalle) a partir del reporte. */
export function formatTable(rows) {
  const header = ['Caso', 'HTTP', 'Proveedor', 'Resultado', 'Detalle'];
  const body = rows.map((r) => [
    String(r.id),
    String(r.status),
    String(r.provider || '-'),
    r.passed ? 'PASA' : 'FALLA',
    (r.failures || []).join('; '),
  ]);
  const widths = header.map((h, i) => Math.max(h.length, ...body.map((row) => row[i].length)));
  const line = (cells) =>
    cells
      .map((c, i) => c.padEnd(widths[i]))
      .join(' | ')
      .trimEnd();
  return [line(header), widths.map((w) => '-'.repeat(w)).join('-|-'), ...body.map(line)].join('\n');
}

function readReport() {
  if (!existsSync(REPORT)) return null;
  try {
    return JSON.parse(readFileSync(REPORT, 'utf8'));
  } catch {
    return null;
  }
}

function main() {
  const cases = findCases();
  const countError = checkCount(cases);
  if (countError) {
    console.error(countError);
    return 1;
  }
  const provider = process.env.APP_ASSISTANT_PROVIDER || 'fake';
  console.log(`Evals del Asistente de descripción: ${cases.length} casos (proveedor ${provider})`);

  rmSync(REPORT, { force: true });
  const code = mvnw(['-B', '-ntp', '-Pevals', 'verify']);

  const rows = readReport();
  if (!rows) {
    console.error('✗ El backend no produjo el reporte de evals (ver la salida de Maven).');
    return code === 0 ? 1 : code;
  }
  console.log(`\n${formatTable(rows)}\n`);
  const passed = rows.filter((r) => r.passed).length;
  console.log(`${passed}/${rows.length} casos pasan`);
  if (rows.length !== EXPECTED_CASES || passed !== rows.length || code !== 0) {
    console.error('✗ Evals en rojo');
    return code === 0 ? 1 : code;
  }
  console.log('✓ Evals en verde');
  return 0;
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  process.exit(main());
}
