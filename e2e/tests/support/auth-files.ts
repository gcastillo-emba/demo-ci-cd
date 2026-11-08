import { fileURLToPath } from 'node:url';
import { ABOGADO, SOCIA } from './session';

// Rutas de storageState por rol (en .gitignore). Las escribe e2e/global-setup.ts con UN login por
// rol, por el rate limiting del login en nginx (ADR-006). Sin `test.extend` aquí: este módulo
// también lo importa el global setup.

/** Clave del token en el SPA (frontend/src/api/token.ts). */
export const TOKEN_KEY = 'ah.token';

const authFile = (name: string) =>
  fileURLToPath(new URL(`../../.auth/${name}.json`, import.meta.url));

export const ROLE_STATES = {
  socia: { user: SOCIA, path: authFile('socia') },
  abogado1: { user: ABOGADO, path: authFile('abogado1') },
} as const;

export const SOCIA_STATE = ROLE_STATES.socia.path;
export const ABOGADO_STATE = ROLE_STATES.abogado1.path;
