import { test as base, type Browser, type BrowserContext, type Page } from '@playwright/test';
import { TOKEN_KEY } from './auth-files';

// Sesiones reutilizadas (storageState) por el rate limiting del login en nginx (ADR-006).
export { ABOGADO_STATE, SOCIA_STATE } from './auth-files';

/**
 * Puente storageState → sessionStorage: Playwright restaura el token en localStorage
 * (storageState no soporta sessionStorage) y este script lo mueve a sessionStorage, donde lo
 * espera la app, antes de que cargue cualquier página. Tras el primer documento localStorage
 * queda vacío, así que "Cerrar sesión" sigue funcionando.
 */
export async function installSessionBridge(target: Page | BrowserContext): Promise<void> {
  await target.addInitScript((key: string) => {
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
}

/** `test` cuya `page` ya trae instalado el puente de sesión. */
export const test = base.extend({
  page: async ({ page }, use) => {
    await installSessionBridge(page);
    await use(page);
  },
});

/**
 * Página con la sesión de un rol en un contexto propio (pruebas con dos roles a la vez), con el
 * mismo puente de sesión que el fixture `test`.
 */
export async function rolePage(browser: Browser, storageState: string): Promise<Page> {
  const context = await browser.newContext({ storageState });
  await installSessionBridge(context);
  return context.newPage();
}

export { expect } from '@playwright/test';
