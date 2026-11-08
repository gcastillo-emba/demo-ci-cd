#!/usr/bin/env node
// E2E local sobre docker compose. Uso: npm run e2e [-- --no-build] [-- --no-up] [-- --keep]
//   1. `docker compose up -d --build --wait --wait-timeout 300` (salvo --no-up; sin --build con --no-build).
//   2. `npm run test -w e2e` con BASE_URL (por defecto http://localhost:8080).
//   3. Si algo falla: vuelca `docker compose logs --no-color --tail 200`.
//   4. Siempre `docker compose down -v` (salvo --keep, o --no-up: el stack no lo levantó este script).
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { run } from './lib/run.mjs';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const DEFAULT_BASE_URL = 'http://localhost:8080';

export function parseArgs(argv) {
  return {
    build: !argv.includes('--no-build'),
    up: !argv.includes('--no-up'),
    keep: argv.includes('--keep'),
  };
}

/** Plan de comandos (puro, para poder probarlo sin Docker). */
export function plan(flags, env = process.env) {
  const up = ['compose', 'up', '-d', ...(flags.build ? ['--build'] : []), '--wait'];
  up.push('--wait-timeout', '300');
  return {
    up: flags.up ? { cmd: 'docker', args: up } : null,
    test: {
      cmd: 'npm',
      args: ['run', 'test', '-w', 'e2e'],
      env: { BASE_URL: env.BASE_URL || DEFAULT_BASE_URL },
    },
    logs: { cmd: 'docker', args: ['compose', 'logs', '--no-color', '--tail', '200'] },
    down: flags.up && !flags.keep ? { cmd: 'docker', args: ['compose', 'down', '-v'] } : null,
  };
}

export function main(argv = process.argv.slice(2)) {
  const flags = parseArgs(argv);
  const p = plan(flags);
  const exec = (step, extraEnv) =>
    run(step.cmd, step.args, { cwd: ROOT, env: { ...process.env, ...extraEnv } });

  let code = 0;
  try {
    if (p.up) {
      console.log('▶ [e2e] Levantando el stack con docker compose…');
      code = exec(p.up);
      if (code !== 0) console.error(`✗ [e2e] docker compose up falló (código ${code}).`);
    }
    if (code === 0) {
      console.log(`▶ [e2e] Playwright contra ${p.test.env.BASE_URL}…`);
      code = exec(p.test, p.test.env);
      if (code !== 0) console.error(`✗ [e2e] La suite E2E falló (código ${code}).`);
    }
    if (code !== 0) {
      console.error('▶ [e2e] Logs de los contenedores (últimas 200 líneas por servicio):');
      exec(p.logs);
    }
  } finally {
    if (p.down) {
      console.log('▶ [e2e] Bajando el stack (docker compose down -v)…');
      exec(p.down);
    } else if (p.up) {
      console.log('■ [e2e] --keep: el stack sigue arriba (docker compose down -v para bajarlo).');
    }
  }
  return code;
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
  process.exitCode = main();
}
