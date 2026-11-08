// Helper de spawn multiplataforma.
// Node >= 18.20.2 rechaza ejecutar .cmd/.bat con shell:false en Windows (CVE-2024-27980, EINVAL)
// y la ruta del repo puede contener espacios; por eso usamos shell:true citando cada argumento.
import { spawnSync, spawn } from 'node:child_process';

export const isWin = process.platform === 'win32';

export function quote(arg) {
  const s = String(arg);
  if (s === '') return '""';
  if (!/[\s"'&|<>^%!$`;()*?]/.test(s)) return s;
  if (isWin) return `"${s.replace(/"/g, '""')}"`;
  return `'${s.replace(/'/g, `'\''`)}'`;
}

export function commandLine(cmd, args = []) {
  return [cmd, ...args].map(quote).join(' ');
}

/** Ejecuta un comando de forma síncrona heredando stdio. Devuelve el código de salida. */
export function run(cmd, args = [], opts = {}) {
  const res = spawnSync(commandLine(cmd, args), {
    stdio: 'inherit',
    shell: true,
    ...opts,
  });
  if (res.error) throw res.error;
  return res.status ?? 1;
}

/** Ejecuta y captura stdout (string). */
export function capture(cmd, args = [], opts = {}) {
  const res = spawnSync(commandLine(cmd, args), {
    encoding: 'utf8',
    shell: true,
    ...opts,
  });
  return { status: res.status ?? 1, stdout: res.stdout ?? '', stderr: res.stderr ?? '' };
}

/** Ejecuta asíncronamente; resuelve con el código de salida. */
export function runAsync(cmd, args = [], opts = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(commandLine(cmd, args), { stdio: 'inherit', shell: true, ...opts });
    child.on('error', reject);
    child.on('close', (code) => resolve(code ?? 1));
  });
}

/** Wrapper de Maven: usa mvnw.cmd en Windows y ./mvnw en el resto. */
export function mvnw(args, opts = {}) {
  const cmd = isWin ? 'mvnw.cmd' : './mvnw';
  return run(cmd, args, { cwd: new URL('../../backend/', import.meta.url), ...opts });
}
