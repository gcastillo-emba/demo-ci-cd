#!/usr/bin/env node
// Verificación cronometrada de la ruta rápida del runbook (F5): imágenes publicadas en GHCR.
// Uso:
//   node scripts/demo-check.mjs [--tag v0.1.0] [--keep] [--metricas <archivo>]
// Pasos: candado `compose-e2e` (el demo también usa el puerto 8080) → `compose pull` →
// `compose up -d --wait` → readiness y login vía nginx (http://localhost:8080) → `down -v`.
// Imprime los tiempos medidos y, con --metricas, los agrega al archivo indicado.
import { appendFileSync, mkdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { run } from './lib/run.mjs';
import { acquire } from './lib/lock.mjs';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const BASE_URL = process.env.BASE_URL || 'http://localhost:8080';
// Credenciales del seed sintético de demo (README, .env.example): sin valor real.
const DEMO_USER = { email: 'socia@asuntos.test', password: 'Demo2026!' };

export function parseArgs(argv) {
  const value = (flag) => {
    const i = argv.indexOf(flag);
    return i >= 0 ? argv[i + 1] : undefined;
  };
  return {
    tag: value('--tag'),
    keep: argv.includes('--keep'),
    metricas: value('--metricas'),
  };
}

const compose = (...args) => ['compose', '-f', 'docker-compose.demo.yml', ...args];
const seconds = (ms) => (ms / 1000).toFixed(1);

async function checkHealth() {
  const res = await fetch(`${BASE_URL}/api/actuator/health/readiness`);
  const body = await res.json().catch(() => ({}));
  if (res.status !== 200 || body.status !== 'UP')
    throw new Error(`readiness respondió ${res.status} ${JSON.stringify(body)}`);
  return body.status;
}

async function checkLogin() {
  const res = await fetch(`${BASE_URL}/api/auth/login`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(DEMO_USER),
  });
  const body = await res.json().catch(() => ({}));
  if (res.status !== 200 || !body.accessToken)
    throw new Error(`login respondió ${res.status} (${body.code ?? 'sin code'})`);
  return body.user?.role ?? '?';
}

export async function main(argv = process.argv.slice(2)) {
  const flags = parseArgs(argv);
  const env = { ...process.env, ...(flags.tag ? { TAG: flags.tag } : {}) };
  const exec = (args) => run('docker', args, { cwd: ROOT, env });

  const release = acquire('compose-e2e', {
    onWait: (lp) => console.log(`… [demo] otro worktree usa el puerto 8080; esperando ${lp}`),
  });
  let code = 0;
  try {
    const t0 = Date.now();
    console.log(`▶ [demo] docker compose pull (TAG=${flags.tag ?? 'latest'})…`);
    code = exec(compose('pull'));
    const t1 = Date.now();
    if (code === 0) {
      console.log('▶ [demo] docker compose up -d --wait…');
      code = exec(compose('up', '-d', '--wait'));
    }
    const t2 = Date.now();
    if (code !== 0) throw new Error(`docker compose falló (código ${code})`);

    const health = await checkHealth();
    const role = await checkLogin();
    const t3 = Date.now();

    const resumen = [
      `fecha=${new Date().toISOString()}`,
      `tag=${flags.tag ?? 'latest'}`,
      `pull_s=${seconds(t1 - t0)}`,
      `up_wait_s=${seconds(t2 - t1)}`,
      `pull_mas_up_s=${seconds(t2 - t0)}`,
      `hasta_login_ok_s=${seconds(t3 - t0)}`,
      `readiness=${health}`,
      `login_rol=${role}`,
    ].join(' ');
    console.log(`✓ [demo] ${resumen}`);
    if (flags.metricas) {
      mkdirSync(dirname(resolve(ROOT, flags.metricas)), { recursive: true });
      appendFileSync(resolve(ROOT, flags.metricas), `demo-check ${resumen}\n`);
    }
  } catch (err) {
    code = code || 1;
    console.error(`✗ [demo] ${err.message}`);
    console.error('▶ [demo] Logs (últimas 100 líneas por servicio):');
    exec(compose('logs', '--no-color', '--tail', '100'));
  } finally {
    if (flags.keep) {
      console.log('■ [demo] --keep: el stack sigue arriba en 8080; el candado se libera.');
    } else {
      console.log('▶ [demo] docker compose down -v…');
      exec(compose('down', '-v'));
    }
    release();
  }
  return code;
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
  process.exitCode = await main();
}
