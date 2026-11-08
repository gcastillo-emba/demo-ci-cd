#!/usr/bin/env node
// Verifica de forma mecánica que un diff solo toque las rutas propias del rol (AGENTS.md §2).
// Uso: node scripts/check-ownership.mjs <base> <head> --allow "<glob>[,<glob>...]"
// Lista `git diff --name-only <base>...<head>` y sale con 1 si hay archivos fuera de los globs.
// Globs: `**` = cualquier cantidad de segmentos (incluido ninguno), `*` = cualquier texto sin `/`.
import { pathToFileURL } from 'node:url';
import { capture } from './lib/run.mjs';

/** Coincide un segmento con un patrón que solo admite `*` (cualquier texto). Sin RegExp. */
export function matchSegment(seg, pat) {
  const parts = pat.split('*');
  if (parts.length === 1) return seg === pat;
  let pos = 0;
  if (!seg.startsWith(parts[0])) return false;
  pos = parts[0].length;
  for (let i = 1; i < parts.length - 1; i++) {
    const idx = seg.indexOf(parts[i], pos);
    if (idx < 0) return false;
    pos = idx + parts[i].length;
  }
  const last = parts[parts.length - 1];
  return seg.length - pos >= last.length && seg.endsWith(last);
}

/** Coincidencia por segmentos: `**` = cero o más segmentos, `*` = texto dentro de un segmento. */
export function matchGlob(file, glob) {
  const norm = (x) => String(x).trim().split('\\').join('/').replace(/^\.\//, '');
  const f = norm(file).split('/');
  const g = norm(glob).split('/');
  const memo = new Map();
  const rec = (i, j) => {
    const k = i + ',' + j;
    if (memo.has(k)) return memo.get(k);
    let r;
    if (j === g.length) r = i === f.length;
    else if (g[j] === '**') r = rec(i, j + 1) || (i < f.length && rec(i + 1, j));
    else r = i < f.length && matchSegment(f[i], g[j]) && rec(i + 1, j + 1);
    memo.set(k, r);
    return r;
  };
  return rec(0, 0);
}

/** ¿La ruta coincide con alguno de los globs? */
export function matchesAny(file, globs) {
  return globs.some((g) => matchGlob(file, g));
}

/** Devuelve los archivos que no coinciden con ningún glob permitido. */
export function outsideAllowed(files, globs) {
  return files.filter((f) => !matchesAny(f, globs));
}

export function parseArgs(argv) {
  const positional = [];
  let allow = null;
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--allow') allow = argv[++i] ?? '';
    else if (a.startsWith('--allow=')) allow = a.slice('--allow='.length);
    else positional.push(a);
  }
  const globs = (allow ?? '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
  return { base: positional[0], head: positional[1], globs };
}

export function main(argv = process.argv.slice(2)) {
  const { base, head, globs } = parseArgs(argv);
  const refOk = (r) => typeof r === 'string' && r.length > 0 && !r.startsWith('-');
  if (!refOk(base) || !refOk(head) || globs.length === 0) {
    console.error(
      'Uso: node scripts/check-ownership.mjs <base> <head> --allow "<glob>[,<glob>...]"',
    );
    return 1;
  }
  const r = capture('git', ['diff', '--name-only', '--end-of-options', `${base}...${head}`]);
  if (r.status !== 0) {
    console.error(`git diff falló (fail-closed): ${r.stderr.trim()}`);
    return 1;
  }
  const files = r.stdout
    .split(/\r?\n/)
    .map((s) => s.trim())
    .filter(Boolean);
  const fuera = outsideAllowed(files, globs);
  if (fuera.length) {
    console.error(`Archivos fuera de las rutas permitidas (${globs.join(', ')}):`);
    for (const f of fuera) console.error(`  - ${f}`);
    return 1;
  }
  console.log(`check-ownership OK: ${files.length} archivo(s) dentro de ${globs.join(', ')}.`);
  return 0;
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
  process.exitCode = main();
}
