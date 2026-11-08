import { expect, type Browser, type Page } from '@playwright/test';
import { TOKEN_KEY } from './auth-files';

// Utilidades E2E de C2 (add-time-entries). Datos 100 % sintéticos.

/** Fecha `YYYY-MM-DD` de hoy en America/La_Paz (la zona de la regla "fecha no futura"). */
export function todayInLaPaz(now: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/La_Paz',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(now);
}

export function addDays(isoDate: string, days: number): string {
  const date = new Date(`${isoDate}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

/**
 * Fecha de trabajo pasada y aleatoria (entre 400 y ~3000 días atrás) para que cada corrida tenga
 * su propio día: el límite diario de 24 h suma lo de corridas anteriores del mismo abogado y día.
 */
export function isolatedWorkDate(): string {
  const daysBack = 400 + Math.floor(Math.random() * 2600);
  return addDays(todayInLaPaz(), -daysBack);
}

/**
 * Página con la sesión de un rol en un contexto propio (para pruebas con dos roles a la vez). Mismo
 * puente storageState → sessionStorage que el fixture de support/auth.ts.
 */
export async function rolePage(browser: Browser, storageState: string): Promise<Page> {
  const context = await browser.newContext({ storageState });
  await context.addInitScript((key: string) => {
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
  return context.newPage();
}

/** Registra horas desde "Registrar horas" y espera la confirmación. */
export async function registerHours(
  page: Page,
  entry: { matterLabel: string; workDate: string; hours: string; description: string },
) {
  await page.goto('/registrar-horas');
  await expect(page.getByRole('heading', { level: 1, name: 'Registrar horas' })).toBeVisible();
  await fillTimeEntryForm(page, entry);
  await page.getByRole('button', { name: 'Guardar', exact: true }).click();
}

export async function fillTimeEntryForm(
  page: Page,
  entry: { matterLabel: string; workDate: string; hours: string; description: string },
) {
  const form = page.getByRole('region', { name: 'Nuevo registro de horas' });
  await form.getByRole('combobox', { name: 'Asunto' }).selectOption({ label: entry.matterLabel });
  await form.getByLabel('Fecha de trabajo').fill(entry.workDate);
  await form.getByRole('textbox', { name: 'Horas' }).fill(entry.hours);
  await form.getByRole('textbox', { name: 'Descripción' }).fill(entry.description);
}

/** Fila de una tabla que contiene `text` (descripciones únicas por corrida). */
export function rowWith(page: Page, table: string, text: string) {
  return page.getByRole('table', { name: table }).getByRole('row').filter({ hasText: text });
}
