#!/usr/bin/env node
// Verifica de forma mecánica que un diff solo toque las rutas propias del rol (AGENTS.md §2).
// Uso: node scripts/check-ownership.mjs <base> <head> --allow "<glob>[,<glob>...]"
// Lista `git diff --name-only <base>...<head>` y sale con 1 si hay archivos fuera de los globs.
// Globs: `**` = cualquier cantidad de segmentos (incluido ninguno), `*` = cualquier texto sin `/`.
import { pathToFileURL } from 'node:url';
import { capture } from './lib/run.mjs';

/** Convierte un glob simple (** y *) a RegExp anclada. */
export function globToRegex(glob) {
  const g = String(glob).trim().replace(/\\/g, '/').replace(/^\.\//, '');
  let re = '';
  for (let i = 0; i < g.length; i++) {
    const ch = g[i];
    if (ch === '*') {
      if (g[i + 1] === '*') {
        i++;
        if (g[i + 1] === '/') {
          i++;
          re += '(?:.*/)?'; // "**/" => cero o más directorios
        } else {
          re += '.*'; // "**" al final o pegado => cualquier cosa
        }
      } else {
        re += '[^/]*';
      }
    } else if ('\\^$+?.()|{}[]'.includes(ch)) {
      re += '\\' + ch;
    } else {
      re += ch;
    }
  }
  return new RegExp(`^${re}$`);
}

/** ¿La ruta coincide con alguno de los globs? */
export function matchesAny(file, globs) {
  const f = String(file).replace(/\\/g, '/');
  return globs.some((g) => globToRegex(g).test(f));
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
  if (!base || !head || globs.length === 0) {
    console.error(
      'Uso: node scripts/check-ownership.mjs <base> <head> --allow "<glob>[,<glob>...]"',
    );
    return 1;
  }
  const r = capture('git', ['diff', '--name-only', `${base}...${head}`]);
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
