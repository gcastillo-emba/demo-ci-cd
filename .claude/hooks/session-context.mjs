#!/usr/bin/env node
// SessionStart. Si existe .mada/estado.json imprime un resumen breve para retomar el trabajo.
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { readStdinJson, projectDir as resolveProjectDir } from './lib/io.mjs';

function fmt(v) {
  if (v === undefined || v === null || v === '') return '—';
  if (typeof v === 'object') return JSON.stringify(v);
  return String(v);
}

function main() {
  let input = {};
  try {
    input = readStdinJson();
  } catch {
    input = {};
  }
  const file = path.join(resolveProjectDir(input), '.mada', 'estado.json');
  if (!existsSync(file)) process.exit(0);
  let e;
  try {
    e = JSON.parse(readFileSync(file, 'utf8'));
  } catch {
    process.stdout.write('Estado MADA: .mada/estado.json existe pero no es JSON válido.\n');
    process.exit(0);
  }
  const lines = [
    'Estado MADA (de .mada/estado.json):',
    `- Fase: ${fmt(e.fase)}`,
    `- Capacidad: ${fmt(e.capacidad)}`,
    `- Rama: ${fmt(e.rama)}`,
    `- PR: ${fmt(e.pr)}`,
    `- Último gate: ${fmt(e.ultimoGate)}`,
  ];
  process.stdout.write(lines.join('\n') + '\n');
  process.exit(0);
}

try {
  main();
} catch {
  process.exit(0);
}
