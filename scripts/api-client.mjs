#!/usr/bin/env node
// Genera el cliente TypeScript del contrato (openapi-typescript) en
// frontend/src/api/generated/schema.ts. Uso:
//   node scripts/api-client.mjs           → escribe el archivo
//   node scripts/api-client.mjs --check   → compara en memoria con el versionado (exit 1 si difiere)
import { mkdirSync, readFileSync, writeFileSync, existsSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
export const SPEC = join(ROOT, 'api', 'openapi.yaml');
export const OUTPUT = join(ROOT, 'frontend', 'src', 'api', 'generated', 'schema.ts');
export const HEADER =
  '// Generado desde api/openapi.yaml — no editar. Ejecuta npm run api:generate.\n';

/** Normaliza finales de línea para comparar igual en Windows (CRLF) y Linux (LF). */
export function normalize(text) {
  return text.replace(/\r\n/g, '\n');
}

/** Devuelve el contenido esperado de schema.ts (siempre con LF). */
export async function generate(spec = SPEC) {
  const { default: openapiTS, astToString } = await import('openapi-typescript');
  const ast = await openapiTS(pathToFileURL(spec));
  return normalize(HEADER + astToString(ast));
}

async function main() {
  const check = process.argv.includes('--check');
  const rel = relative(ROOT, OUTPUT).replace(/\\/g, '/');
  const expected = await generate();

  if (check) {
    const actual = existsSync(OUTPUT) ? normalize(readFileSync(OUTPUT, 'utf8')) : null;
    if (actual !== expected) {
      console.error(
        `✗ ${rel} no está sincronizado con api/openapi.yaml.\n` +
          '  Ejecuta "npm run api:generate" y versiona el resultado.',
      );
      process.exit(1);
    }
    console.log(`✓ ${rel} sincronizado con api/openapi.yaml.`);
    return;
  }

  mkdirSync(dirname(OUTPUT), { recursive: true });
  writeFileSync(OUTPUT, expected, 'utf8');
  console.log(`✓ Generado ${rel}.`);
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  main().catch((err) => {
    console.error(`✗ No se pudo generar el cliente: ${err.message}`);
    process.exit(1);
  });
}
