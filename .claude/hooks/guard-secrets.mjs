#!/usr/bin/env node
// PreToolUse (Read|Edit|Write|MultiEdit|NotebookEdit|Grep|Glob y Bash|PowerShell).
// Bloquea lecturas y escrituras sobre archivos de secretos. Fail-closed: entrada ilegible => exit 2.
import { readStdinJson, block, allow, splitSegments, tokenize } from './lib/io.mjs';

const MSG =
  'Bloqueado por política MADA: archivo de secretos. Usa .env.example con valores ficticios.';
const FILE_TOOLS = new Set(['Read', 'Edit', 'Write', 'MultiEdit', 'NotebookEdit']);
const SEARCH_TOOLS = new Set(['Grep', 'Glob']);
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
  // Buscadores y filtros: leen el contenido de los archivos que reciben.
  'grep',
  'egrep',
  'fgrep',
  'rg',
  'sed',
  'awk',
  'gawk',
  'findstr',
  'select-string',
  'sls',
]);
// Intérpretes cuyo flag de código en línea se inspecciona buscando nombres de archivos de secretos.
const INLINE_CODE = {
  node: ['-e', '--eval', '-p', '--print'],
  deno: ['eval'],
  bun: ['-e', '--eval', '-p', '--print'],
  python: ['-c'],
  python3: ['-c'],
  py: ['-c'],
  ruby: ['-e'],
  perl: ['-e', '-E'],
};
const COPY_CMDS = new Set([
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
]);
const SHELL_TOOLS = new Set(['Bash', 'PowerShell']);
const ENV_ALLOWED = new Set(['.env.example', '.env.sample']);
const SECRET_EXT = /\.(pem|key|p12|pfx|jks|keystore)$/i;
// Nombres de muestra para decidir si un glob apunta a secretos (y no a "todo").
const SECRET_SAMPLES = [
  '.env',
  '.env.local',
  '.env.production',
  'id_rsa',
  'id_ed25519',
  'server.pem',
  'app.key',
  'app.p12',
  'app.pfx',
  'app.jks',
  'credentials.json',
  'service-account.json',
  '.npmrc',
  '.netrc',
];
const BENIGN_SAMPLES = ['readme.md', 'app.ts', 'index.html', 'package.json'];

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

/** Ruta de búsqueda (Grep/Glob): además, un directorio `secrets/` como destino final. */
export function isSecretSearchPath(p) {
  if (isSecretPath(p)) return true;
  if (typeof p !== 'string') return false;
  const segs = p.replace(/\\/g, '/').split('/').filter(Boolean);
  return segs.some((s) => ['secrets', 'secret'].includes(s.toLowerCase()));
}

function globBaseToRegex(base) {
  let re = '';
  for (let i = 0; i < base.length; i++) {
    const ch = base[i];
    if (ch === '*') re += '.*';
    else if (ch === '?') re += '.';
    else if (ch === '{') re += '(?:';
    else if (ch === '}') re += ')';
    else if (ch === ',') re += '|';
    else if ('\\^$+.()|[]'.includes(ch)) re += '\\' + ch;
    else re += ch;
  }
  return new RegExp(`^${re}$`, 'i');
}

/**
 * ¿El glob apunta a archivos de secretos? Se bloquea si coincide con algún nombre de secreto
 * de muestra y no con nombres corrientes (así `*` o `**\/*.ts` no se bloquean, pero `.env*`,
 * `*.pem` o `*.{ts,pem}` sí).
 */
export function isSecretGlob(glob) {
  if (!glob || typeof glob !== 'string') return false;
  if (isSecretSearchPath(glob)) return true;
  const segs = glob.replace(/\\/g, '/').split('/').filter(Boolean);
  const base = segs[segs.length - 1] ?? '';
  if (!/[*?{]/.test(base)) return false;
  // Cada alternativa de llaves se evalúa por separado: `*.{ts,key}` => `*.ts` y `*.key`.
  return expandBraces(base).some((alt) => {
    if (!/[*?]/.test(alt)) return isSecretPath(alt);
    let re;
    try {
      re = globBaseToRegex(alt);
    } catch {
      return true; // glob ilegible: fail-closed
    }
    const hitsSecret = SECRET_SAMPLES.some((s) => re.test(s));
    const hitsBenign = BENIGN_SAMPLES.some((s) => re.test(s));
    return hitsSecret && !hitsBenign;
  });
}

/** Expansión simple de llaves (recursiva, sin anidamiento): `a{b,c}d` => [abd, acd]. */
function expandBraces(s, depth = 0) {
  const m = /\{([^{}]*)\}/.exec(s);
  if (!m || depth > 5) return [s];
  const pre = s.slice(0, m.index);
  const post = s.slice(m.index + m[0].length);
  return m[1].split(',').flatMap((alt) => expandBraces(pre + alt + post, depth + 1));
}

// Intérprete con código en línea en cualquier parte del comando (antes de dividir por `;`,
// porque el código suele contener `;` dentro de comillas).
const INLINE_RE =
  /(?:^|[\s;&|(])(?:[^\s;&|()]*[\\/])?(?:node|deno|bun|python3?|py|ruby|perl)(?:\.exe)?\s(?:[^\n]*?\s)?(?:-e|--eval|-p|--print|-c|-E|eval)\s+([^\n]*)/gi;

/** ¿El comando ejecuta código en línea que menciona un archivo de secretos? */
export function inlineCodeTouchesSecret(command) {
  let m;
  INLINE_RE.lastIndex = 0;
  while ((m = INLINE_RE.exec(String(command))) !== null) {
    if (codeMentionsSecret(m[1])) return true;
  }
  return false;
}

/** ¿Un fragmento de código en línea menciona un archivo de secretos? */
export function codeMentionsSecret(code) {
  return String(code)
    .split(/[\s'"`(),;+=[\]{}]+/)
    .some((piece) => piece && isSecretPath(piece));
}

const isFlag = (t) => /^-/.test(t);
const cmdName = (t) =>
  String(t || '')
    .toLowerCase()
    .replace(/^.*[\\/]/, '')
    .replace(/\.exe$/, '');

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
  // Redirección de entrada: `< .env` (se ignoran heredocs `<<` y sustitución `<(`).
  const inRedir = /(?:^|[^<\d])0?<(?![<(&])\s*("([^"]*)"|'([^']*)'|[^\s;|&<>"'{}()]+)/g;
  while ((m = inRedir.exec(segment)) !== null) {
    targets.push(m[2] ?? m[3] ?? m[1]);
  }

  const tokens = tokenize(segment).map((t) => t.value);
  // Quitar asignaciones de entorno iniciales (FOO=bar cmd).
  let i = 0;
  while (i < tokens.length && /^[A-Za-z_][A-Za-z0-9_]*=/.test(tokens[i])) i++;
  const cmd = cmdName(tokens[i]);
  const args = tokens.slice(i + 1).filter((t) => !/^\d?>>?/.test(t));

  if (READ_CMDS.has(cmd)) {
    for (const a of args) {
      if (!isFlag(a)) targets.push(a);
      else if (a.includes('=')) targets.push(a.slice(a.indexOf('=') + 1)); // --include=.env
    }
  } else if (cmd === 'git') {
    // git show HEAD:.env / git show :.env / git cat-file -p HEAD:.env
    let s = 0;
    while (s < args.length && isFlag(args[s]))
      s += /^-(C|c)$|^--(git-dir|work-tree)$/.test(args[s]) ? 2 : 1;
    const sub = args[s];
    if (sub === 'show' || sub === 'cat-file') {
      for (const a of args.slice(s + 1)) {
        if (isFlag(a)) continue;
        targets.push(a.includes(':') ? a.slice(a.indexOf(':') + 1) : a);
      }
    }
  } else if (INLINE_CODE[cmd]) {
    const flags = INLINE_CODE[cmd];
    for (let k = 0; k < args.length; k++) {
      if (flags.includes(args[k]) && args[k + 1] !== undefined && codeMentionsSecret(args[k + 1]))
        targets.push('.env'); // marcador: el código en línea toca un archivo de secretos
    }
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
  } else if (COPY_CMDS.has(cmd)) {
    // Origen Y destino: copiar o mover un secreto también lo expone.
    for (let k = 0; k < args.length; k++) {
      const a = args[k];
      if (/^-(destination|newname|path|literalpath|t|-target-directory)$/i.test(a)) {
        if (args[k + 1]) targets.push(args[++k]);
      } else if (/^--target-directory=/.test(a)) {
        targets.push(a.split('=').slice(1).join('='));
      } else if (!isFlag(a)) targets.push(a);
    }
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
  if (inlineCodeTouchesSecret(command)) return true;
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
  if (SEARCH_TOOLS.has(tool)) {
    // path es opcional (por defecto el cwd); si viene, debe ser string.
    for (const key of ['path', 'glob', 'pattern']) {
      const v = ti[key];
      if (v === undefined || v === null) continue;
      if (typeof v !== 'string')
        block('Bloqueado por política MADA: parámetros de búsqueda ilegibles (fail-closed).');
    }
    if (ti.path && isSecretSearchPath(ti.path)) block(MSG);
    if (ti.glob && isSecretGlob(ti.glob)) block(MSG);
    // En Glob, `pattern` es el glob de archivos; en Grep es una regex de contenido (no se evalúa).
    if (tool === 'Glob' && ti.pattern && isSecretGlob(ti.pattern)) block(MSG);
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
