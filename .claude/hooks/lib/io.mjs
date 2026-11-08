// Utilidades de E/S para hooks de Claude Code (solo builtins de Node).
// Contrato: JSON por stdin; exit 0 = permitir; exit 2 = bloquear (stderr se muestra a Claude).
import { readFileSync } from 'node:fs';

/** Lee stdin completo y lo parsea como JSON. Lanza si no es JSON válido (los hooks deciden fail-closed). */
export function readStdinJson() {
  const raw = readFileSync(0, 'utf8');
  if (!raw || !raw.trim()) throw new Error('stdin vacío');
  const data = JSON.parse(raw);
  if (data === null || typeof data !== 'object') throw new Error('stdin no es un objeto JSON');
  return data;
}

/** Bloquea la acción: mensaje en español a stderr y exit 2. */
export function block(msg) {
  process.stderr.write(String(msg).trimEnd() + '\n');
  process.exit(2);
}

/** Permite la acción: exit 0. */
export function allow() {
  process.exit(0);
}

/** Directorio del proyecto según el contrato de los hooks. */
export function projectDir(input) {
  return process.env.CLAUDE_PROJECT_DIR || (input && input.cwd) || process.cwd();
}

/** Divide una línea de comandos en segmentos por &&, ||, ;, | y saltos de línea. */
export function splitSegments(command) {
  return String(command)
    .split(/&&|\|\||;|\||\r?\n/)
    .map((s) => s.trim())
    .filter(Boolean);
}

/**
 * Tokenizador simple estilo shell. Devuelve [{ value, quoted }].
 * Las comillas se eliminan; un token entre comillas conserva quoted=true.
 */
export function tokenize(segment) {
  const out = [];
  const re = /"((?:[^"\\]|\\.)*)"|'([^']*)'|(\S+)/g;
  let m;
  while ((m = re.exec(segment)) !== null) {
    if (m[1] !== undefined) out.push({ value: m[1], quoted: true });
    else if (m[2] !== undefined) out.push({ value: m[2], quoted: true });
    else out.push({ value: m[3].replace(/^["']|["']$/g, ''), quoted: false });
  }
  return out;
}
