#!/usr/bin/env node
// Entorno de desarrollo local. Uso: npm run dev
//   1. Levanta PostgreSQL con `docker compose up -d postgres`.
//   2. Arranca en paralelo el backend (perfil demo, :8081) y el frontend (Vite, :5173),
//      con la salida de cada uno prefijada.
//   Ctrl+C detiene ambos (PostgreSQL sigue arriba; `docker compose down` para bajarlo).
import { createInterface } from 'node:readline';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { run, start, killTree } from './lib/run.mjs';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');

const COLORS = { backend: '\x1b[36m', frontend: '\x1b[35m' };
const RESET = '\x1b[0m';

function pipeWithPrefix(stream, name, out) {
  const prefix = process.stdout.isTTY ? `${COLORS[name]}[${name}]${RESET} ` : `[${name}] `;
  createInterface({ input: stream }).on('line', (line) => out.write(`${prefix}${line}\n`));
}

function launch(name, cmd, args) {
  const child = start(cmd, args, { cwd: ROOT, stdio: ['ignore', 'pipe', 'pipe'] });
  pipeWithPrefix(child.stdout, name, process.stdout);
  pipeWithPrefix(child.stderr, name, process.stderr);
  return child;
}

function main() {
  console.log('▶ Levantando PostgreSQL (docker compose up -d postgres)…');
  const pg = run('docker', ['compose', 'up', '-d', 'postgres'], { cwd: ROOT });
  if (pg !== 0) {
    console.error('✗ No se pudo levantar PostgreSQL. ¿Docker Desktop está corriendo?');
    process.exit(pg);
  }

  const children = {
    backend: launch('backend', 'node', [
      'scripts/mvn.mjs',
      'spring-boot:run',
      '-Dspring-boot.run.profiles=demo',
    ]),
    frontend: launch('frontend', 'npm', ['run', 'dev', '-w', 'frontend']),
  };

  let stopping = false;
  const stopAll = (code) => {
    if (stopping) return;
    stopping = true;
    console.log('\n■ Deteniendo backend y frontend…');
    for (const child of Object.values(children)) killTree(child);
    process.exitCode = code;
  };

  for (const [name, child] of Object.entries(children)) {
    child.on('exit', (code) => {
      if (!stopping) {
        console.error(`✗ ${name} terminó (código ${code ?? 'señal'}); se detiene el resto.`);
        stopAll(code || 1);
      }
    });
  }

  process.on('SIGINT', () => stopAll(0));
  process.on('SIGTERM', () => stopAll(0));
  console.log(
    '✓ Backend http://localhost:8081/api · Frontend http://localhost:5173 (Ctrl+C para salir)',
  );
}

main();
