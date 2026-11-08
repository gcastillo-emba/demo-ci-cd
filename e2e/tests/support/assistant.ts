import { expect, type Page } from '@playwright/test';

// Utilidades E2E de C4 (add-description-assistant). Datos 100 % sintéticos.

/** Región del formulario de alta en "Registrar horas". */
export function newEntryForm(page: Page) {
  return page.getByRole('region', { name: 'Nuevo registro de horas' });
}

/** Panel con la sugerencia del asistente (región `aria-live`). */
export function suggestionPanel(page: Page) {
  return page.getByRole('region', { name: 'Sugerencia del asistente' });
}

/**
 * Escribe la nota en "Descripción", pulsa "Sugerir descripción" y devuelve la sugerencia mostrada
 * (proveedor `fake` determinista en el perfil `demo`).
 */
export async function requestSuggestion(page: Page, note: string): Promise<string> {
  const form = newEntryForm(page);
  await form.getByRole('textbox', { name: 'Descripción' }).fill(note);
  await form.getByRole('button', { name: 'Sugerir descripción' }).click();
  const panel = suggestionPanel(page);
  await expect(panel).toBeVisible();
  const text = (await panel.locator('.assistant__text').textContent()) ?? '';
  expect(text.trim()).not.toBe('');
  expect([...text].length).toBeLessThanOrEqual(300);
  return text;
}

/** Simula `GET /api/features` con el asistente apagado (solo para la interfaz). */
export async function featuresWithAssistantOff(page: Page) {
  await page.route('**/api/features', (route) =>
    route.fulfill({ json: { descriptionAssistant: false } }),
  );
}
