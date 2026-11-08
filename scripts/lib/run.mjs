// Helper de spawn multiplataforma.
// Node >= 18.20.2 rechaza ejecutar .cmd/.bat con shell:false en Windows (CVE-2024-27980, EINVAL)
// y la ruta del repo puede contener espacios; por eso usamos shell:true citando cada argumento.
import { spawnSync, spawn } from 'node:child_process';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

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

/**
 * Lanza un proceso de larga duración y devuelve el ChildProcess (para leer su salida o detenerlo).
 * En Unix se crea en su propio grupo de procesos para poder matar el árbol con killTree().
 */
export function start(cmd, args = [], opts = {}) {
  return spawn(commandLine(cmd, args), { shell: true, detached: !isWin, ...opts });
}

/** Detiene un proceso lanzado con start() junto con sus hijos (shell → mvnw → java, etc.). */
export function killTree(child) {
  if (!child?.pid || child.exitCode !== null) return;
  if (isWin) {
    spawnSync(`taskkill /pid ${child.pid} /T /F`, { shell: true, stdio: 'ignore' });
  } else {
    try {
      process.kill(-child.pid, 'SIGTERM');
    } catch {
      child.kill('SIGTERM');
    }
  }
}

/**
 * Wrapper de Maven: usa mvnw.cmd en Windows y ./mvnw en el resto, siempre con ruta absoluta
 * (con NoDefaultCurrentDirectoryInExePath=1, cmd.exe no busca en el directorio actual; bitácora #12).
 */
export function mvnwCommand(win = isWin) {
  const backendDir = fileURLToPath(new URL('../../backend/', import.meta.url));
  return { cmd: join(backendDir, win ? 'mvnw.cmd' : 'mvnw'), cwd: backendDir };
}

export function mvnw(args, opts = {}) {
  const { cmd, cwd } = mvnwCommand();
  return run(cmd, args, { cwd, ...opts });
}
