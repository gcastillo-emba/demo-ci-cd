#!/usr/bin/env node
// Ejemplo de Claude Code en modo headless (capacidad 11 del plan maestro): `claude -p` lee la
// bitácora de fallas y redacta un resumen ejecutivo en español en docs/bitacora-resumen.md.
// Uso:
//   node scripts/claude-resumen-bitacora.mjs [--dry-run] [--salida <ruta>]
//   npm run bitacora:resumen
// Solo lectura para el agente: --allowedTools Read y --max-turns 3. Si el CLI `claude` no está
// instalado, termina con código 0 y un aviso (no es un gate). En CI lo usa
// .github/workflows/bitacora-resumen.yml (manual, inerte sin ANTHROPIC_API_KEY).
import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { capture, commandLine } from './lib/run.mjs';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const BITACORA = 'docs/bitacora-fallas.md';

// Una sola línea y sin comillas dobles, % ni !: se pasa como argumento por la shell (run.mjs).
export const PROMPT =
  `Lee el archivo ${BITACORA} (bitácora de fallas del POC Asuntos y Horas, metodología MADA-MB) ` +
  'y redacta en español un resumen ejecutivo en Markdown para la dirección del proyecto. ' +
  'Estructura obligatoria con encabezados de nivel 2: ' +
  '1) Panorama (2 o 3 frases con el total de fallas y el periodo); ' +
  '2) Fallas por fase y por tipo (una tabla con fase, cantidad y tipos: gate mecánico, gate de juicio, herramienta o entorno, instrucción del orquestador, concurrencia entre worktrees, test inestable); ' +
  '3) Causas recurrentes (viñetas, citando los números de fila); ' +
  '4) Top 5 lecciones para MADA-MB (lista numerada, cada una accionable). ' +
  'No inventes datos: usa solo lo que dice la bitácora. No incluyas un título de nivel 1 ni texto antes ni después del resumen.';

export function parseArgs(argv) {
  const i = argv.indexOf('--salida');
  return {
    dryRun: argv.includes('--dry-run'),
    salida: i >= 0 ? argv[i + 1] : 'docs/bitacora-resumen.md',
  };
}

export function claudeArgs(prompt = PROMPT) {
  return ['-p', prompt, '--output-format', 'json', '--max-turns', '3', '--allowedTools', 'Read'];
}

export function documento(resumen, { fecha = new Date(), modelo, costo } = {}) {
  const meta = [
    `Generado en modo headless con \`claude -p\` (\`scripts/claude-resumen-bitacora.mjs\`) el ${fecha.toISOString().slice(0, 10)}`,
    modelo ? `modelo: ${modelo}` : null,
    typeof costo === 'number' ? `costo informado: USD ${costo.toFixed(4)}` : null,
  ]
    .filter(Boolean)
    .join(' · ');
  return [
    '# Resumen ejecutivo de la bitácora de fallas',
    '',
    `> ${meta}.`,
    `> Fuente: [\`${BITACORA}\`](bitacora-fallas.md). Texto generado por un modelo: revisarlo antes de citarlo.`,
    '',
    resumen.trim(),
    '',
  ].join('\n');
}

async function formatear(texto, ruta) {
  try {
    const prettier = await import('prettier');
    const config = (await prettier.resolveConfig(ruta)) ?? {};
    return await prettier.format(texto, { ...config, filepath: ruta });
  } catch {
    return texto; // sin prettier instalado se escribe tal cual
  }
}

export async function main(argv = process.argv.slice(2)) {
  const flags = parseArgs(argv);
  const args = claudeArgs();
  if (flags.dryRun) {
    console.log(commandLine('claude', args));
    return 0;
  }
  if (capture('claude', ['--version'], { cwd: ROOT }).status !== 0) {
    console.log('ℹ El CLI `claude` no está instalado o no está en el PATH: se omite el resumen.');
    return 0;
  }

  console.log('▶ claude -p (headless, solo Read, máx. 3 turnos)…');
  const t0 = Date.now();
  const r = capture('claude', args, { cwd: ROOT, maxBuffer: 16 * 1024 * 1024 });
  const segundos = ((Date.now() - t0) / 1000).toFixed(1);
  let out;
  try {
    out = JSON.parse(r.stdout);
  } catch {
    console.error(`✗ Salida no JSON de claude (código ${r.status}): ${r.stderr || r.stdout}`);
    return 1;
  }
  if (r.status !== 0 || out.is_error || out.subtype !== 'success' || !out.result) {
    console.error(`✗ claude terminó con error (${out.subtype ?? r.status}) en ${segundos} s.`);
    return 1;
  }

  const ruta = join(ROOT, flags.salida);
  const modelo = Object.keys(out.modelUsage ?? {})[0];
  const texto = await formatear(documento(out.result, { modelo, costo: out.total_cost_usd }), ruta);
  writeFileSync(ruta, texto);
  console.log(
    `✓ ${flags.salida} escrito en ${segundos} s (turnos: ${out.num_turns ?? '?'}, ` +
      `costo informado: USD ${out.total_cost_usd ?? '?'}).`,
  );
  return 0;
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
  process.exitCode = await main();
}
