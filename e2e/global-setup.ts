import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';
import { request, type FullConfig } from '@playwright/test';
import { ROLE_STATES, TOKEN_KEY } from './tests/support/auth-files';

// Global setup de Playwright: UN login por rol vía API y su storageState en e2e/.auth/<rol>.json.
// Motivo: nginx limita POST /api/auth/login a 5 por minuto por IP con ráfaga de 5 (ADR-006) y todos
// los E2E salen de la misma IP. Así la suite hace 2 logins aquí + los del spec de login (≤ 3).
// NUNCA se relaja el límite de producción para que pasen los tests.
//
// La app guarda el token en sessionStorage (ADR-006), que el storageState de Playwright no
// persiste: se guarda en `origins[].localStorage` y el fixture de tests/support/auth.ts lo mueve a
// sessionStorage antes de que cargue la app.

const MAX_ATTEMPTS = 3;

async function loginToken(baseURL: string, email: string, password: string): Promise<string> {
  const api = await request.newContext({ baseURL });
  try {
    for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      const response = await api.post('/api/auth/login', { data: { email, password } });
      if (response.ok()) {
        const body = (await response.json()) as { accessToken?: unknown };
        if (typeof body.accessToken !== 'string' || body.accessToken === '') {
          throw new Error(`Login de ${email} sin accessToken en la respuesta.`);
        }
        return body.accessToken;
      }
      // 429 del rate limiting (p. ej. una corrida anterior hace segundos): esperar Retry-After.
      if (response.status() === 429 && attempt < MAX_ATTEMPTS) {
        const retryAfter = Number(response.headers()['retry-after'] ?? '60');
        const seconds = Number.isFinite(retryAfter) ? Math.min(Math.max(retryAfter, 1), 60) : 60;
        console.log(`[global-setup] 429 en el login de ${email}; reintento en ${seconds} s.`);
        await new Promise((resolve) => setTimeout(resolve, seconds * 1000));
        continue;
      }
      throw new Error(`Login de ${email} falló con HTTP ${response.status()}.`);
    }
    throw new Error(`Login de ${email} agotó ${MAX_ATTEMPTS} intentos.`);
  } finally {
    await api.dispose();
  }
}

export default async function globalSetup(config: FullConfig) {
  const baseURL =
    config.projects[0]?.use.baseURL ?? process.env.BASE_URL ?? 'http://localhost:8080';
  const origin = new URL(baseURL).origin;

  for (const role of Object.values(ROLE_STATES)) {
    const token = await loginToken(baseURL, role.user.email, role.user.password);
    mkdirSync(dirname(role.path), { recursive: true });
    writeFileSync(
      role.path,
      JSON.stringify(
        { cookies: [], origins: [{ origin, localStorage: [{ name: TOKEN_KEY, value: token }] }] },
        null,
        2,
      ),
    );
  }
}
