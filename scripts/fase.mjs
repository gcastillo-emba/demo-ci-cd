#!/usr/bin/env node
// Registra el inicio o fin de una fase (F0–F6) o capacidad (C1–C4) de MADA-MB.
// - Agrega {ts, evento, fase} a .mada/fases.jsonl
// - Actualiza .mada/estado.json {fase, capacidad?, actualizado}
// Uso: node scripts/fase.mjs inicio|fin <F#|Cn>
import { appendFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const EVENTOS = new Set(['inicio', 'fin']);
const FASE_RE = /^(F[0-6]|C[1-4])$/;

export function parseArgs(argv) {
  const [evento, faseRaw] = argv;
  const fase = (faseRaw ?? '').toUpperCase();
  if (!EVENTOS.has(evento) || !FASE_RE.test(fase)) {
    throw new Error('Uso: node scripts/fase.mjs inicio|fin <F0..F6|C1..C4>');
  }
  return { evento, fase };
}

function readJson(file, fallback) {
  try {
    return JSON.parse(readFileSync(file, 'utf8'));
  } catch {
    return fallback;
  }
}

function readEventos(file) {
  if (!existsSync(file)) return [];
  return readFileSync(file, 'utf8')
    .split(/\r?\n/)
    .filter(Boolean)
    .flatMap((line) => {
      try {
        return [JSON.parse(line)];
      } catch {
        return [];
      }
    });
}

function formatDuracion(ms) {
  const totalMin = Math.round(ms / 60000);
  const h = Math.floor(totalMin / 60);
  const m = totalMin % 60;
  return h > 0 ? `${h} h ${m} min` : `${m} min`;
}

export function registrar({ evento, fase }, { root, now = new Date() }) {
  const dir = join(root, '.mada');
  mkdirSync(dir, { recursive: true });
  const fasesFile = join(dir, 'fases.jsonl');
  const estadoFile = join(dir, 'estado.json');
  const ts = now.toISOString();

  const previos = readEventos(fasesFile);
  appendFileSync(fasesFile, JSON.stringify({ ts, evento, fase }) + '\n', 'utf8');

  const estado = readJson(estadoFile, {});
  const esCapacidad = fase.startsWith('C');
  if (evento === 'inicio') {
    if (esCapacidad) {
      estado.capacidad = fase;
      if (!estado.fase) estado.fase = 'F4';
    } else {
      estado.fase = fase;
    }
  } else if (esCapacidad && estado.capacidad === fase) {
    delete estado.capacidad;
  }
  estado.actualizado = ts;
  writeFileSync(estadoFile, JSON.stringify(estado, null, 2) + '\n', 'utf8');

  let duracionMs;
  if (evento === 'fin') {
    const inicio = [...previos].reverse().find((e) => e.fase === fase && e.evento === 'inicio');
    if (inicio) duracionMs = now.getTime() - new Date(inicio.ts).getTime();
  }
  return { ts, estado, duracionMs };
}

const isMain = process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1];
if (isMain) {
  try {
    const args = parseArgs(process.argv.slice(2));
    const root = join(dirname(fileURLToPath(import.meta.url)), '..');
    const { ts, estado, duracionMs } = registrar(args, { root });
    let msg = `[fase] ${args.evento} ${args.fase} @ ${ts}`;
    if (duracionMs !== undefined) msg += ` (duración: ${formatDuracion(duracionMs)})`;
    else if (args.evento === 'fin') msg += ' (sin inicio registrado)';
    console.log(msg);
    console.log(`[fase] estado: ${JSON.stringify(estado)}`);
  } catch (err) {
    console.error(err.message);
    process.exit(2);
  }
}
