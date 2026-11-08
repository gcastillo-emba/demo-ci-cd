#!/usr/bin/env node
// PreToolUse (Edit|Write|MultiEdit|NotebookEdit y Bash|PowerShell).
// Bloquea lecturas y escrituras sobre archivos de secretos. Fail-closed: entrada ilegible => exit 2.
import { readStdinJson, block, allow, splitSegments, tokenize } from './lib/io.mjs';

const MSG =
  'Bloqueado por política MADA: archivo de secretos. Usa .env.example con valores ficticios.';
const FILE_TOOLS = new Set(['Read', 'Edit', 'Write', 'MultiEdit', 'NotebookEdit']);
// Comandos que vuelcan archivos: cualquier argumento no-flag se trata como ruta leída.
const READ_CMDS = new Set([
  'cat',
  'type',
  'less',
  'more',
  'head',
  'tail',
  'source',
  '.',
  'bat',
  'nl',
  'strings',
  'xxd',
  'od',
  'base64',
  'get-content',
  'gc',
]);
const SHELL_TOOLS = new Set(['Bash', 'PowerShell']);
const ENV_ALLOWED = new Set(['.env.example', '.env.sample']);
const SECRET_EXT = /\.(pem|key|p12|pfx|jks|keystore)$/i;

export function isSecretPath(p) {
  if (!p || typeof p !== 'string') return false;
  const norm = p.replace(/\\/g, '/').replace(/\/+$/, '');
  const segments = norm.split('/').filter(Boolean);
  if (segments.length === 0) return false;
  const base = segments[segments.length - 1];
  const lower = base.toLowerCase();
  const dirs = segments.slice(0, -1).map((s) => s.toLowerCase());

  if (/^\.env(\..+)?$/i.test(base) && !ENV_ALLOWED.has(lower)) return true;
  if (SECRET_EXT.test(base)) return true;
  if (/^id_(rsa|ed25519)/i.test(base)) return true;
  if (dirs.includes('secrets') || dirs.includes('secret')) return true;
  if (/^credentials.*\.json$/i.test(base)) return true;
  if (/^service-account.*\.json$/i.test(base)) return true;
  if (lower === '.npmrc' || lower === '.netrc') return true;
  return false;
}

const isFlag = (t) => /^-/.test(t);

/** Devuelve las rutas leídas o escritas detectadas en un segmento de shell. */
export function writeTargets(segment) {
  const targets = [];
  // Redirecciones: >, >>, 1>, 2>, &>, *> (PowerShell). Se ignoran 2>&1 y similares.
  const redir = /(?:^|[^<>&|])(?:\d|\*|&)?>>?\s*("([^"]*)"|'([^']*)'|[^\s;|&<>"'{}()]+)/g;
  let m;
  while ((m = redir.exec(segment)) !== null) {
    const raw = m[2] ?? m[3] ?? m[1];
    if (/^&\d$/.test(raw)) continue;
    targets.push(raw);
  }

  const tokens = tokenize(segment).map((t) => t.value);
  // Quitar asignaciones de entorno iniciales (FOO=bar cmd).
  let i = 0;
  while (i < tokens.length && /^[A-Za-z_][A-Za-z0-9_]*=/.test(tokens[i])) i++;
  const cmd = (tokens[i] || '').toLowerCase();
  const args = tokens.slice(i + 1).filter((t) => !/^\d?>>?/.test(t));

  if (READ_CMDS.has(cmd)) {
    for (const a of args) if (!isFlag(a)) targets.push(a);
  } else if (cmd === 'tee' || cmd === 'tee-object') {
    for (let k = 0; k < args.length; k++) {
      const a = args[k];
      if (/^-(filepath|literalpath|path)$/i.test(a)) {
        if (args[k + 1]) targets.push(args[++k]);
      } else if (!isFlag(a)) targets.push(a);
    }
  } else if (['set-content', 'out-file', 'add-content', 'sc', 'ac'].includes(cmd)) {
    let named = false;
    const positional = [];
    for (let k = 0; k < args.length; k++) {
      const a = args[k];
      if (/^-(path|filepath|literalpath)$/i.test(a)) {
        named = true;
        if (args[k + 1]) targets.push(args[++k]);
      } else if (/^-(value|encoding|inputobject|width|stream|delimiter)$/i.test(a)) {
        k++;
      } else if (!isFlag(a)) positional.push(a);
    }
    if (!named && positional.length) targets.push(positional[0]);
  } else if (
    [
      'cp',
      'mv',
      'copy',
      'move',
      'copy-item',
      'move-item',
      'cpi',
      'mi',
      'install',
      'rename-item',
      'ren',
    ].includes(cmd)
  ) {
    let named = false;
    const positional = [];
    for (let k = 0; k < args.length; k++) {
      const a = args[k];
      if (/^-(destination|newname)$/i.test(a)) {
        named = true;
        if (args[k + 1]) targets.push(args[++k]);
      } else if (/^-(path|literalpath|t|-target-directory)$/i.test(a)) {
        if (/^-(t|-target-directory)$/.test(a) && args[k + 1]) targets.push(args[k + 1]);
        k++;
      } else if (/^--target-directory=/.test(a)) {
        targets.push(a.split('=').slice(1).join('='));
      } else if (!isFlag(a)) positional.push(a);
    }
    if (!named && positional.length) targets.push(positional[positional.length - 1]);
  } else if (cmd === 'touch' || cmd === 'new-item' || cmd === 'ni') {
    for (let k = 0; k < args.length; k++) {
      const a = args[k];
      if (/^-(itemtype|value|name)$/i.test(a)) {
        if (/^-name$/i.test(a) && args[k + 1]) targets.push(args[k + 1]);
        k++;
      } else if (/^-path$/i.test(a)) {
        if (args[k + 1]) targets.push(args[++k]);
      } else if (!isFlag(a)) targets.push(a);
    }
  }
  return targets;
}

function checkCommand(command, depth = 0) {
  for (const seg of splitSegments(command)) {
    for (const t of writeTargets(seg)) {
      if (isSecretPath(t)) return true;
    }
    // Analiza recursivamente cadenas entre comillas (bash -c "...", pwsh -Command "...").
    if (depth < 3) {
      for (const tok of tokenize(seg)) {
        if (tok.quoted && /\s/.test(tok.value) && checkCommand(tok.value, depth + 1)) return true;
      }
    }
  }
  return false;
}

function main() {
  let input;
  try {
    input = readStdinJson();
  } catch {
    block('Bloqueado por política MADA: entrada del hook ilegible (fail-closed).');
  }
  const tool = input.tool_name;
  const ti = input.tool_input || {};

  if (FILE_TOOLS.has(tool)) {
    const p = ti.file_path ?? ti.notebook_path ?? ti.path;
    if (typeof p !== 'string' || !p)
      block('Bloqueado por política MADA: no se pudo leer la ruta del archivo (fail-closed).');
    if (isSecretPath(p)) block(MSG);
    allow();
  }
  if (SHELL_TOOLS.has(tool)) {
    if (typeof ti.command !== 'string')
      block('Bloqueado por política MADA: no se pudo leer el comando (fail-closed).');
    if (checkCommand(ti.command)) block(MSG);
    allow();
  }
  allow();
}

main();
