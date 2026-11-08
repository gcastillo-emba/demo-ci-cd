#!/usr/bin/env node
// PreToolUse (Bash|PowerShell). Bloquea force push, pushes a main/master, pushes de tags,
// y `gh pr merge` fuera de scripts/merge-si-verde.mjs. Fail-closed ante entrada ilegible.
import { execFileSync } from 'node:child_process';
import { readStdinJson, block, allow, splitSegments, tokenize, projectDir } from './lib/io.mjs';

const PROTECTED = new Set(['main', 'master']);
const MSG_FORCE =
  'Bloqueado por política MADA: force push / mirror prohibido. Crea un commit nuevo y haz push normal a tu rama.';
const MSG_MAIN =
  'Bloqueado por política MADA: push directo a main/master prohibido. Trabaja en una rama y abre un PR.';
const MSG_DELETE = 'Bloqueado por política MADA: borrar main/master en el remoto está prohibido.';
const MSG_TAG =
  'Bloqueado por política MADA: Push de tag de release: acción externa irreversible; la confirma y ejecuta el humano.';
const MSG_BRANCH =
  'Bloqueado por política MADA: no se pudo determinar la rama actual para un push sin refspec (fail-closed). Indica la rama explícitamente: git push origin <rama>.';
const MSG_MERGE =
  'Bloqueado por política MADA: el merge de PRs solo se ejecuta vía scripts/merge-si-verde.mjs (node scripts/merge-si-verde.mjs <pr>).';
const MSG_ADMIN =
  'Bloqueado por política MADA: `gh pr merge --admin` salta las protecciones de la rama; prohibido.';

// Opciones de git push que consumen un valor como token siguiente.
const PUSH_OPTS_WITH_VALUE = new Set(['--repo', '-o', '--push-option', '--receive-pack', '--exec']);
// Opciones globales de git (antes del subcomando) que consumen un valor.
const GIT_GLOBAL_WITH_VALUE = new Set([
  '-C',
  '-c',
  '--git-dir',
  '--work-tree',
  '--namespace',
  '--exec-path',
]);

/**
 * Un refspec es de tag solo si algún lado es `refs/tags/...` o parece una versión (`v1.2...`).
 * Así una rama como `v2-feature` no se confunde con un tag.
 */
export function isTagRefspec(r) {
  if (r.includes('refs/tags/')) return true;
  return r
    .replace(/^\+/, '')
    .split(':')
    .some((p) => /^v\d+\.\d+/.test(p));
}

function stripRefsHeads(ref) {
  return ref.replace(/^refs\/heads\//, '');
}

function currentBranch(cwd) {
  try {
    return execFileSync('git', ['rev-parse', '--abbrev-ref', 'HEAD'], {
      cwd,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'ignore'],
      timeout: 10000,
    }).trim();
  } catch {
    return null;
  }
}

/** Analiza `git ... push ...`. Devuelve mensaje de bloqueo o null. */
function checkGitPush(tokens, cwd) {
  const gi = tokens.findIndex((t) => /(^|[\\/])git(\.exe)?$/i.test(t));
  if (gi < 0) return null;
  let i = gi + 1;
  let gitCwd = cwd;
  while (i < tokens.length && tokens[i].startsWith('-')) {
    const t = tokens[i];
    if (GIT_GLOBAL_WITH_VALUE.has(t)) {
      if (t === '-C' && tokens[i + 1]) gitCwd = tokens[i + 1];
      i += 2;
    } else i += 1;
  }
  if (tokens[i] !== 'push') return null;

  const args = tokens.slice(i + 1);
  const positional = [];
  let del = false;
  for (let k = 0; k < args.length; k++) {
    const a = args[k];
    if (a === '--') {
      positional.push(...args.slice(k + 1));
      break;
    }
    if (/^--(force|force-with-lease|force-if-includes)(=.*)?$/.test(a)) return MSG_FORCE;
    if (a === '--mirror') return MSG_FORCE;
    if (a === '--tags' || a === '--follow-tags') return MSG_TAG;
    if (a === '--delete') {
      del = true;
      continue;
    }
    if (PUSH_OPTS_WITH_VALUE.has(a)) {
      k++;
      continue;
    }
    if (/^-[A-Za-z]+$/.test(a)) {
      if (a.includes('f')) return MSG_FORCE;
      if (a.includes('d')) del = true;
      continue;
    }
    if (a.startsWith('-')) continue;
    positional.push(a);
  }

  const refspecs = positional.slice(1); // positional[0] = remoto
  for (const r of refspecs) {
    if (r.startsWith('+')) return MSG_FORCE;
    if (isTagRefspec(r)) return MSG_TAG;
  }
  for (const r of refspecs) {
    const dst = stripRefsHeads(r.includes(':') ? r.slice(r.lastIndexOf(':') + 1) : r);
    if (PROTECTED.has(dst)) return del ? MSG_DELETE : MSG_MAIN;
    if (dst === 'HEAD' || dst === '@') {
      const b = currentBranch(gitCwd);
      if (b === null) return MSG_BRANCH;
      if (PROTECTED.has(b)) return MSG_MAIN;
    }
  }
  if (refspecs.length === 0) {
    const b = currentBranch(gitCwd);
    if (b === null) return MSG_BRANCH;
    if (PROTECTED.has(b)) return MSG_MAIN;
  }
  return null;
}

// Flags de gh que consumen el token siguiente como valor.
const GH_OPTS_WITH_VALUE = new Set(['-R', '--repo', '--hostname']);
// Subcomandos de `gh pr`: el primero que aparezca tras `pr` es el subcomando real.
const GH_PR_SUBCOMMANDS = new Set([
  'checkout',
  'checks',
  'close',
  'comment',
  'create',
  'diff',
  'edit',
  'list',
  'lock',
  'merge',
  'ready',
  'reopen',
  'review',
  'revert',
  'status',
  'unlock',
  'update-branch',
  'view',
]);

/** Tokens posicionales de una invocación de gh (sin flags ni sus valores conocidos). */
function ghPositionals(args) {
  const out = [];
  for (let k = 0; k < args.length; k++) {
    const a = args[k];
    if (GH_OPTS_WITH_VALUE.has(a)) {
      k++;
      continue;
    }
    if (a.startsWith('-')) continue;
    out.push(a);
  }
  return out;
}

/** Analiza `gh ... pr ... merge ...` (con flags intercalados) y `gh api` de merge. */
function checkGhMerge(tokens) {
  const gi = tokens.findIndex((t) => /(^|[\\/])gh(\.exe)?$/i.test(t));
  if (gi < 0) return null;
  const args = tokens.slice(gi + 1);
  const pos = ghPositionals(args);
  // gh api .../pulls/N/merge (REST) o mutaciones GraphQL de merge: mismo bypass.
  if (pos[0] === 'api') {
    const rest = args.join(' ');
    if (/\/pulls\/[^\s/]+\/merge\b|mergePullRequest|enablePullRequestAutoMerge/i.test(rest))
      return MSG_MERGE;
    return null;
  }
  if (pos[0] !== 'pr') return null;
  const sub = pos.slice(1).find((t) => GH_PR_SUBCOMMANDS.has(t));
  if (sub !== 'merge') return null;
  if (args.includes('--admin')) return MSG_ADMIN;
  // El script invoca gh como proceso hijo (no pasa por el hook): cualquier `gh pr merge`
  // directo del agente, aun con --match-head-commit, se salta la verificación de etiquetas.
  return MSG_MERGE;
}

export function checkCommand(command, cwd, depth = 0) {
  for (const seg of splitSegments(command)) {
    const toks = tokenize(seg);
    const values = toks.map((t) => t.value);
    const msg = checkGitPush(values, cwd) || checkGhMerge(values);
    if (msg) return msg;
    if (depth < 3) {
      for (const tok of toks) {
        if (tok.quoted && /\s/.test(tok.value)) {
          const inner = checkCommand(tok.value, cwd, depth + 1);
          if (inner) return inner;
        }
      }
    }
  }
  return null;
}

function main() {
  let input;
  try {
    input = readStdinJson();
  } catch {
    block('Bloqueado por política MADA: entrada del hook ilegible (fail-closed).');
  }
  const ti = input.tool_input || {};
  if (input.tool_name && !['Bash', 'PowerShell'].includes(input.tool_name)) allow();
  if (typeof ti.command !== 'string')
    block('Bloqueado por política MADA: no se pudo leer el comando (fail-closed).');
  const cwd = input.cwd || projectDir(input);
  const msg = checkCommand(ti.command, cwd);
  if (msg) block(msg);
  allow();
}

main();
