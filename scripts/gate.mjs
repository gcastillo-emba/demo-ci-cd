#!/usr/bin/env node
// Gate mecánico local (MADA-MB). Uso:
//   node scripts/gate.mjs [--rapido] [--e2e] [--fail-fast]
// Corre los pasos base y agrega los opcionales solo si su carpeta/script existe, así el mismo
// gate crece con el repo sin cambiar su interfaz. Orden: spec · hooks · scripts · format ·
// contract (api:lint, api:check) · backend (--rapido → backend:test:fast, si no backend:verify) ·
// frontend (lint, typecheck, test) · evals · e2e (solo con --e2e). Lo que falte se omite.
// Escribe .gate/last-run.json y, ante fallas, agrega líneas a .mada/fallas.jsonl.
import { existsSync, mkdirSync, readFileSync, writeFileSync, appendFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { run, capture, commandLine } from './lib/run.mjs';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');

function readJson(path) {
  try {
    return JSON.parse(readFileSync(path, 'utf8'));
  } catch {
    return null;
  }
}

function hasScript(pkgPath, name) {
  const pkg = readJson(pkgPath);
  return Boolean(pkg?.scripts?.[name]);
}

export function parseArgs(argv) {
  return {
    rapido: argv.includes('--rapido'),
    e2e: argv.includes('--e2e'),
    failFast: argv.includes('--fail-fast'),
  };
}

/** Construye la lista de pasos según los flags y lo que existe en el repo. */
export function buildSteps(flags, root = ROOT) {
  const at = (p) => join(root, p);
  const rootPkg = at('package.json');
  const npm = (id, ...args) => ({ id, cmd: 'npm', args });

  const steps = [
    npm('spec', 'run', 'spec:validate'),
    npm('hooks', 'run', 'hooks:test'),
    npm('scripts', 'run', 'scripts:test'),
    npm('format', 'run', 'format:check'),
  ];

  // Contrato: lint de OpenAPI + cliente TS generado sin drift.
  if (existsSync(at('api/openapi.yaml'))) {
    for (const s of ['api:lint', 'api:check']) {
      if (hasScript(rootPkg, s)) steps.push(npm(`contract:${s.split(':')[1]}`, 'run', s));
    }
  }
  // Backend: --rapido omite los IT (Testcontainers); sin flag, verify completo con JaCoCo.
  if (existsSync(at('backend/pom.xml'))) {
    const script = flags.rapido ? 'backend:test:fast' : 'backend:verify';
    if (hasScript(rootPkg, script)) steps.push(npm('backend', 'run', script));
  }
  // Frontend: solo los scripts que el workspace define (y su alias en el package.json raíz).
  const frontendPkg = at('frontend/package.json');
  if (existsSync(frontendPkg)) {
    for (const s of ['lint', 'typecheck', 'test']) {
      if (hasScript(frontendPkg, s) && hasScript(rootPkg, `frontend:${s}`)) {
        steps.push(npm(`frontend:${s}`, 'run', `frontend:${s}`));
      }
    }
  }
  if (hasScript(rootPkg, 'evals') && existsSync(at('scripts/evals.mjs'))) {
    steps.push(npm('evals', 'run', 'evals'));
  }
  // E2E (compose + Playwright): solo con --e2e.
  if (flags.e2e && hasScript(rootPkg, 'e2e') && existsSync(at('scripts/e2e.mjs'))) {
    steps.push(npm('e2e', 'run', 'e2e'));
  }

  return steps;
}

function gitSha() {
  const r = capture('git', ['rev-parse', 'HEAD'], { cwd: ROOT });
  return r.status === 0 ? r.stdout.trim() : null;
}

function faseActual() {
  return readJson(join(ROOT, '.mada', 'estado.json'))?.fase ?? 'desconocida';
}

function fmtMs(ms) {
  return ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(1)} s`;
}

function registrarFalla(step, exitCode, sha) {
  const dir = join(ROOT, '.mada');
  mkdirSync(dir, { recursive: true });
  const entry = {
    ts: new Date().toISOString(),
    fase: faseActual(),
    paso: step.id,
    cmd: commandLine(step.cmd, step.args),
    exitCode,
    resumen: `El paso "${step.id}" del gate falló con código ${exitCode}`,
    sha,
  };
  appendFileSync(join(dir, 'fallas.jsonl'), JSON.stringify(entry) + '\n');
}

export function main(argv = process.argv.slice(2)) {
  const flags = parseArgs(argv);
  const steps = buildSteps(flags);
  const sha = gitSha();
  const results = [];
  let abortado = false;

  for (const step of steps) {
    if (abortado) {
      results.push({ id: step.id, status: 'SKIP', ms: 0 });
      continue;
    }
    console.log(`\n=== [gate] ${step.id}: ${commandLine(step.cmd, step.args)} ===`);
    const t0 = Date.now();
    let code;
    try {
      code = step.exec ? step.exec() : run(step.cmd, step.args, { cwd: ROOT });
    } catch (err) {
      console.error(`[gate] error al ejecutar ${step.id}: ${err.message}`);
      code = 1;
    }
    const ms = Date.now() - t0;
    const status = code === 0 ? 'PASS' : 'FAIL';
    results.push({ id: step.id, status, ms });
    console.log(`=== [gate] ${step.id}: ${status} (${fmtMs(ms)}) ===`);
    if (status === 'FAIL') {
      registrarFalla(step, code, sha);
      if (flags.failFast) abortado = true;
    }
  }

  const ancho = Math.max(4, ...results.map((r) => r.id.length));
  console.log('\nResumen del gate');
  console.log(`${'Paso'.padEnd(ancho)}  Estado  Tiempo`);
  console.log(`${'-'.repeat(ancho)}  ------  ------`);
  for (const r of results) {
    console.log(`${r.id.padEnd(ancho)}  ${r.status.padEnd(6)}  ${fmtMs(r.ms)}`);
  }
  const fallidos = results.filter((r) => r.status === 'FAIL');
  const total = results.reduce((a, r) => a + r.ms, 0);
  console.log(
    fallidos.length
      ? `\nGATE ROJO: ${fallidos.length} paso(s) fallido(s): ${fallidos.map((r) => r.id).join(', ')} (total ${fmtMs(total)}). Registrado en .mada/fallas.jsonl.`
      : `\nGATE VERDE: ${results.length} paso(s) en ${fmtMs(total)}.`,
  );

  const gateDir = join(ROOT, '.gate');
  mkdirSync(gateDir, { recursive: true });
  writeFileSync(
    join(gateDir, 'last-run.json'),
    JSON.stringify({ ts: new Date().toISOString(), sha, args: argv, steps: results }, null, 2) +
      '\n',
  );

  return fallidos.length ? 1 : 0;
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
  process.exitCode = main();
}
