// Candado entre procesos y worktrees (bitácora #22). Varios worktrees comparten el proyecto
// compose `asuntos-horas`, la etiqueta de imagen `:local` y el puerto 8080, así que dos E2E
// simultáneos se pisan.
// Seguridad: el candado vive en un directorio POR USUARIO (no en el /tmp compartido) creado con
// modo 0700; owner.json se crea en exclusiva (flag wx, modo 0600).
import {
  mkdirSync,
  rmSync,
  writeFileSync,
  readFileSync,
  renameSync,
  statSync,
  chmodSync,
} from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';

const sleep = (ms) => Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms);

/** Directorio de candados por usuario: %LOCALAPPDATA% en Windows, XDG_CACHE_HOME o ~/.cache. */
export function defaultLockDir(env = process.env) {
  const base =
    process.platform === 'win32' && env.LOCALAPPDATA
      ? env.LOCALAPPDATA
      : env.XDG_CACHE_HOME || join(homedir(), '.cache');
  return join(base, 'asuntos-horas', 'locks');
}

function ensureDir(dir) {
  mkdirSync(dir, { recursive: true, mode: 0o700 });
  if (process.platform !== 'win32') chmodSync(dir, 0o700);
}

export function lockPath(name, dir = defaultLockDir()) {
  return join(dir, `${name}.lock`);
}

function pidAlive(pid) {
  try {
    process.kill(pid, 0);
    return true;
  } catch (err) {
    return err.code === 'EPERM';
  }
}

function readOwner(path) {
  try {
    return JSON.parse(readFileSync(join(path, 'owner.json'), 'utf8'));
  } catch {
    return null;
  }
}

/** ¿El candado existente está abandonado (proceso muerto o más viejo que staleMs)? */
export function isStale(path, staleMs) {
  const info = readOwner(path);
  // Con dueño legible solo cuenta si el proceso murió; la antigüedad se usa solo sin owner.json.
  if (info) return !pidAlive(info.pid);
  try {
    return Date.now() - statSync(path).mtimeMs > staleMs;
  } catch {
    return true;
  }
}

/** Retira un candado abandonado renombrándolo primero (atómico) para no borrar uno nuevo ajeno. */
function evict(path) {
  const grave = `${path}.stale-${process.pid}-${Date.now()}`;
  try {
    renameSync(path, grave);
  } catch {
    return; // otro proceso ya lo retiró o lo recreó
  }
  rmSync(grave, { recursive: true, force: true });
}

/**
 * Adquiere el candado `name`; espera hasta `timeoutMs`. Devuelve una función para liberarlo,
 * que solo borra el candado si sigue siendo de este proceso.
 */
export function acquire(name, opts = {}) {
  const {
    timeoutMs = 45 * 60_000,
    pollMs = 5_000,
    staleMs = 60 * 60_000,
    dir = defaultLockDir(),
    onWait = () => {},
  } = opts;
  ensureDir(dir);
  const path = lockPath(name, dir);
  const start = Date.now();
  let warned = false;
  for (;;) {
    try {
      mkdirSync(path, { mode: 0o700 });
      const since = new Date().toISOString();
      writeFileSync(join(path, 'owner.json'), JSON.stringify({ pid: process.pid, since }), {
        flag: 'wx',
        mode: 0o600,
      });
      return () => {
        const info = readOwner(path);
        if (info && info.pid === process.pid && info.since === since)
          rmSync(path, { recursive: true, force: true });
      };
    } catch (err) {
      if (err.code !== 'EEXIST') throw err;
      if (isStale(path, staleMs)) {
        evict(path);
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
