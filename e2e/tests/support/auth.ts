import { test as base } from '@playwright/test';
import { TOKEN_KEY } from './auth-files';

// Sesiones reutilizadas (storageState) por el rate limiting del login en nginx (ADR-006).
export { ABOGADO_STATE, SOCIA_STATE } from './auth-files';

/**
 * `test` con el puente storageState → sessionStorage: Playwright restaura el token en localStorage
 * (storageState no soporta sessionStorage) y este script lo mueve a sessionStorage, donde lo
 * espera la app, antes de que cargue cualquier página. Tras el primer documento localStorage
 * queda vacío, así que "Cerrar sesión" sigue funcionando.
 */
export const test = base.extend({
  page: async ({ page }, use) => {
    await page.addInitScript((key: string) => {
      try {
        const token = window.localStorage.getItem(key);
        if (token !== null) {
          window.sessionStorage.setItem(key, token);
          window.localStorage.removeItem(key);
        }
      } catch {
        // Almacenamiento no disponible: la prueba fallará al no ver la sesión.
      }
    }, TOKEN_KEY);
    await use(page);
  },
});

export { expect } from '@playwright/test';
