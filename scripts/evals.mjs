#!/usr/bin/env node
// Runner de evals del Asistente de descripción (C4). Uso: node scripts/evals.mjs
//
// Estado F3 (walking skeleton): solo descubre los casos. Si no hay casos, pasa con un aviso.
// C4 reemplaza runCases() para delegar en el backend: `node scripts/mvn.mjs -B -ntp -Pevals verify`
// (adaptador fake, determinista), que lee los mismos archivos de casos.
import { existsSync, readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
export const CASES_DIR = join(ROOT, 'evals', 'description-assistant', 'cases');

/** Lista los archivos de casos (.yaml, .yml, .json) ordenados. */
export function findCases(dir = CASES_DIR) {
  if (!existsSync(dir)) return [];
  return readdirSync(dir)
    .filter((f) => /\.(ya?ml|json)$/i.test(f))
    .sort();
}

/** Punto de extensión de C4: ejecutar los casos y devolver el código de salida. */
function runCases(cases) {
  console.error(
    `✗ Hay ${cases.length} casos de eval pero el runner aún no está implementado (C4).\n` +
      '  C4 debe delegar en el backend con el perfil Maven -Pevals.',
  );
  return 1;
}

function main() {
  const cases = findCases();
  if (cases.length === 0) {
    console.log('0 casos — evals de C4 pendientes');
    return 0;
  }
  console.log(`Evals del Asistente de descripción: ${cases.length} casos`);
  return runCases(cases);
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  process.exit(main());
}
