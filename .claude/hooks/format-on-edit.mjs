#!/usr/bin/env node
// PostToolUse (Edit|Write|MultiEdit). Formatea el archivo editado:
//  - Prettier (API, cargado desde la raíz del repo) para ts/tsx/js/mjs/cjs/json/md/css/yaml/yml.
//  - Spotless (backend/mvnw, -DspotlessFiles) para .java; se desactiva con MADA_SPOTLESS_ON_EDIT=0.
// Nunca bloquea, salvo error de sintaxis de Prettier (exit 2 para que Claude lo vea).
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { readStdinJson, projectDir as resolveProjectDir } from './lib/io.mjs';

const PRETTIER_EXT = new Set([
  '.ts',
  '.tsx',
  '.js',
  '.mjs',
  '.cjs',
  '.json',
  '.md',
  '.css',
  '.yaml',
  '.yml',
]);
const SKIP_SEGMENTS = new Set(['generated', 'node_modules', 'target', 'dist']);
const isWin = process.platform === 'win32';

function quote(arg) {
  const s = String(arg);
  if (s === '') return '""';
  if (!/[\s"'&|<>^%!$`;()*?\\]/.test(s)) return s;
  if (isWin) return `"${s.replace(/"/g, '""')}"`;
  return `'${s.replace(/'/g, `'\\''`)}'`;
}

function shouldSkip(rel) {
  const segs = rel.split('/');
  if (segs.some((s) => SKIP_SEGMENTS.has(s))) return true;
  if (rel === 'openspec/changes/archive' || rel.startsWith('openspec/changes/archive/'))
    return true;
  return false;
}

async function runPrettier(projectDir, file) {
  let prettier;
  try {
    const req = createRequire(path.join(projectDir, 'package.json'));
    prettier = req('prettier');
  } catch {
    return 0; // Prettier no instalado: salir en silencio.
  }
  try {
    const ignorePath = path.join(projectDir, '.prettierignore');
    const info = await prettier.getFileInfo(file, {
      ignorePath: existsSync(ignorePath) ? ignorePath : undefined,
    });
    if (info.ignored || !info.inferredParser) return 0;
    const config = (await prettier.resolveConfig(file)) || {};
    const src = readFileSync(file, 'utf8');
    const out = await prettier.format(src, { ...config, filepath: file });
    if (out !== src) writeFileSync(file, out, 'utf8');
    return 0;
  } catch (err) {
    const isSyntax =
      err &&
      (err.name === 'SyntaxError' || err.loc || /SyntaxError/.test(String(err && err.message)));
    if (isSyntax) {
      process.stderr.write(`Prettier: error de sintaxis en ${file}:\n${err.message}\n`);
      return 2;
    }
    return 0;
  }
}

function runSpotless(projectDir, file) {
  if (process.env.MADA_SPOTLESS_ON_EDIT === '0') return;
  const backend = path.join(projectDir, 'backend');
  if (!existsSync(path.join(backend, 'mvnw')) && !existsSync(path.join(backend, 'mvnw.cmd')))
    return;
  const relBackend = path.relative(backend, file).split(path.sep).join('/');
  if (relBackend.startsWith('..')) return;
  const escaped = relBackend.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const cmd = isWin ? 'mvnw.cmd' : './mvnw';
  const line = [cmd, '-q', 'spotless:apply', `-DspotlessFiles=.*${escaped}`].map(quote).join(' ');
  spawnSync(line, { cwd: backend, shell: true, stdio: 'ignore', timeout: 90000 });
}

async function main() {
  let input;
  try {
    input = readStdinJson();
  } catch {
    process.exit(0);
  }
  const projectDir = resolveProjectDir(input);
  const ti = input.tool_input || {};
  const p = ti.file_path;
  if (typeof p !== 'string' || !p) process.exit(0);
  const file = path.resolve(projectDir, p);
  if (!existsSync(file)) process.exit(0);
  const rel = path.relative(projectDir, file).split(path.sep).join('/');
  // Solo archivos del repo: nunca reformatear rutas externas (scratchpad, home, etc.).
  if (rel.startsWith('..') || path.isAbsolute(rel)) process.exit(0);
  if (shouldSkip(rel)) process.exit(0);

  const ext = path.extname(file).toLowerCase();
  if (PRETTIER_EXT.has(ext)) {
    process.exit(await runPrettier(projectDir, file));
  }
  if (ext === '.java') {
    try {
      runSpotless(projectDir, file);
    } catch {
      /* nunca bloquea */
    }
  }
  process.exit(0);
}

main().catch(() => process.exit(0));
