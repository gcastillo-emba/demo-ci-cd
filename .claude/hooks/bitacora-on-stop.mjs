#!/usr/bin/env node
// Stop. Avisa (systemMessage) si .mada/fallas.jsonl tiene fallas de gate posteriores a la última
// edición de docs/bitacora-fallas.md. Con MADA_BITACORA_MODE=block devuelve decision:block.
// Agrega la REGLA DE PARADA si el mismo paso falló 3 veces seguidas sobre el mismo sha.
import { existsSync, readFileSync, statSync } from 'node:fs';
import path from 'node:path';
import { readStdinJson, projectDir as resolveProjectDir } from './lib/io.mjs';

const STOP_RULE =
  'REGLA DE PARADA: el mismo gate falló 3 veces seguidas; detente y pide ayuda al humano.';

function main() {
  let input = {};
  try {
    input = readStdinJson();
  } catch {
    input = {};
  }
  if (input.stop_hook_active) process.exit(0);

  const dir = resolveProjectDir(input);
  const fallasPath = path.join(dir, '.mada', 'fallas.jsonl');
  if (!existsSync(fallasPath)) process.exit(0);

  const bitacoraPath = path.join(dir, 'docs', 'bitacora-fallas.md');
  const since = existsSync(bitacoraPath) ? statSync(bitacoraPath).mtimeMs : 0;

  const records = [];
  for (const line of readFileSync(fallasPath, 'utf8').split(/\r?\n/)) {
    if (!line.trim()) continue;
    try {
      const r = JSON.parse(line);
      const t = Date.parse(r.ts);
      if (Number.isNaN(t)) continue;
      if (r.exitCode === 0) continue;
      records.push({ ...r, _t: t });
    } catch {
      /* línea corrupta: se ignora */
    }
  }
  const nuevas = records.filter((r) => r._t > since);
  if (nuevas.length === 0) process.exit(0);

  const desde = nuevas.reduce((a, r) => (r._t < a._t ? r : a)).ts;
  const pasos = [...new Set(nuevas.map((r) => r.paso ?? '?'))].join(', ');
  let msg = `Aviso MADA: hay ${nuevas.length} fallas de gate desde ${desde} (pasos: ${pasos}) sin registrar en docs/bitacora-fallas.md.`;

  // Regla de parada: 3 fallas consecutivas del mismo paso sobre el mismo sha.
  let run = 0;
  let prevKey = null;
  let stop = false;
  for (const r of nuevas) {
    const key = `${r.paso ?? ''}@${r.sha ?? ''}`;
    run = key === prevKey ? run + 1 : 1;
    prevKey = key;
    if (run >= 3) stop = true;
  }
  if (stop) msg += ` ${STOP_RULE}`;

  const out =
    process.env.MADA_BITACORA_MODE === 'block'
      ? { decision: 'block', reason: msg }
      : { systemMessage: msg };
  process.stdout.write(JSON.stringify(out) + '\n');
  process.exit(0);
}

try {
  main();
} catch {
  process.exit(0);
}
