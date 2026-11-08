// Candado entre procesos y worktrees (bitácora #22): varios worktrees comparten el proyecto
// compose `asuntos-horas`, la etiqueta de imagen `:local` y el puerto 8080, así que dos E2E
// simultáneos se pisan. Se usa mkdir atómico en el directorio temporal del sistema.
import { mkdirSync, rmSync, writeFileSync, readFileSync, statSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const sleep = (ms) => Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms);

export function lockPath(name, dir = tmpdir()) {
  return join(dir, `asuntos-horas-${name}.lock`);
}

function pidAlive(pid) {
  try {
    process.kill(pid, 0);
    return true;
  } catch (err) {
    return err.code === 'EPERM';
  }
}

/** ¿El candado existente está abandonado (proceso muerto o más viejo que staleMs)? */
export function isStale(path, staleMs) {
  try {
    const info = JSON.parse(readFileSync(join(path, 'owner.json'), 'utf8'));
    if (!pidAlive(info.pid)) return true;
    return Date.now() - Date.parse(info.since) > staleMs;
  } catch {
    try {
      return Date.now() - statSync(path).mtimeMs > staleMs;
    } catch {
      return true;
    }
  }
}

/**
 * Adquiere el candado `name`; espera hasta `timeoutMs`. Devuelve una función para liberarlo.
 * Lanza un error si no lo consigue a tiempo.
 */
export function acquire(name, opts = {}) {
  const {
    timeoutMs = 45 * 60_000,
    pollMs = 5_000,
    staleMs = 60 * 60_000,
    dir = tmpdir(),
    onWait = () => {},
  } = opts;
  const path = lockPath(name, dir);
  const start = Date.now();
  let warned = false;
  for (;;) {
    try {
      mkdirSync(path);
      writeFileSync(
        join(path, 'owner.json'),
        JSON.stringify({ pid: process.pid, cwd: process.cwd(), since: new Date().toISOString() }),
      );
      return () => rmSync(path, { recursive: true, force: true });
    } catch (err) {
      if (err.code !== 'EEXIST') throw err;
      if (isStale(path, staleMs)) {
        rmSync(path, { recursive: true, force: true });
        continue;
      }
      if (Date.now() - start > timeoutMs)
        throw new Error(`no se obtuvo el candado ${name} en ${Math.round(timeoutMs / 60000)} min`);
      if (!warned) {
        onWait(path);
        warned = true;
      }
      sleep(pollMs);
    }
  }
}
