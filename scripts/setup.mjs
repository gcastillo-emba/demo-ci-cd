#!/usr/bin/env node
// Verifica el entorno local del POC y aplica configuración de git segura para Windows.
// Uso: npm run setup
import { pathToFileURL } from 'node:url';
import { capture, run } from './lib/run.mjs';

export function majorDe(texto) {
  const m = /(\d+)(?:\.(\d+))?/.exec(texto ?? '');
  if (!m) return null;
  // Formato antiguo de Java: 1.8 -> 8
  return m[1] === '1' && m[2] ? Number(m[2]) : Number(m[1]);
}

function checkNode() {
  const v = process.versions.node;
  const ok = majorDe(v) >= 22;
  return { herramienta: 'node', requerido: '>= 22', detectado: v, ok };
}

function checkJava() {
  const r = capture('java', ['-version']);
  const salida = `${r.stderr}\n${r.stdout}`;
  const m = /version "([^"]+)"/.exec(salida);
  const detectado = r.status === 0 && m ? m[1] : 'no encontrado';
  return { herramienta: 'java', requerido: '21', detectado, ok: majorDe(m?.[1]) === 21 };
}

function checkDocker() {
  const r = capture('docker', ['info', '--format', '{{.ServerVersion}}']);
  const ok = r.status === 0 && r.stdout.trim() !== '';
  return {
    herramienta: 'docker',
    requerido: 'motor activo',
    detectado: ok ? r.stdout.trim() : 'no disponible (¿Docker Desktop apagado?)',
    ok,
  };
}

export function main() {
  const filas = [checkNode(), checkJava(), checkDocker()];
  const w = (k, min) => Math.max(min, ...filas.map((f) => String(f[k]).length));
  const [wh, wr, wd] = [w('herramienta', 11), w('requerido', 9), w('detectado', 9)];
  console.log('\nEstado del entorno');
  console.log(
    `${'Herramienta'.padEnd(wh)}  ${'Requerido'.padEnd(wr)}  ${'Detectado'.padEnd(wd)}  Estado`,
  );
  console.log(`${'-'.repeat(wh)}  ${'-'.repeat(wr)}  ${'-'.repeat(wd)}  ------`);
  for (const f of filas) {
    console.log(
      `${f.herramienta.padEnd(wh)}  ${f.requerido.padEnd(wr)}  ${f.detectado.padEnd(wd)}  ${f.ok ? 'OK' : 'FALTA'}`,
    );
  }

  const git = run('git', ['config', 'core.longpaths', 'true']);
  console.log(
    git === 0
      ? '\ngit: core.longpaths = true (rutas largas en Windows).'
      : '\nAVISO: no se pudo fijar git core.longpaths.',
  );

  console.log('Siguiente paso: npm install (o npm ci) en la raíz y luego npm run gate.');
  const faltan = filas.filter((f) => !f.ok);
  if (faltan.length) {
    console.log(`\nFaltan requisitos: ${faltan.map((f) => f.herramienta).join(', ')}.`);
    return 1;
  }
  return 0;
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
  process.exitCode = main();
}
