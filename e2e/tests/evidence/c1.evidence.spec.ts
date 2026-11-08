import { mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { expect, test, type Page } from '@playwright/test';
import { createClient, createMatter, login, runId, SOCIA } from '../support/session';

// Evidencia visual de C1 (tarea 4.4). Fuera de la corrida por defecto: solo con
// `npm run evidence -w e2e` (playwright.evidence.config.ts). Capturas en docs/evidence/add-clients-matters/.
const OUT_DIR = fileURLToPath(
  new URL('../../../docs/evidence/add-clients-matters/', import.meta.url),
);

async function capture(page: Page, name: string) {
  mkdirSync(OUT_DIR, { recursive: true });
  await page.screenshot({ path: `${OUT_DIR}${name}.png`, fullPage: true });
}

test('evidencia C1: Clientes, Asuntos, alta, diálogo de cierre y asunto cerrado @evidencia', async ({
  page,
}) => {
  const id = runId();
  const clientName = `Cliente Evidencia ${id}`;
  await login(page, SOCIA);

  await createClient(page, clientName, `EVID-${id}`);
  await page.goto('/clientes');
  await expect(page.getByRole('table', { name: 'Lista de clientes' })).toBeVisible();
  await capture(page, '01-clientes');

  const code = await createMatter(page, clientName, `Asunto de evidencia ${id}`);
  await page.goto('/asuntos');
  await expect(page.getByRole('table', { name: 'Lista de asuntos' })).toBeVisible();
  await capture(page, '02-asuntos');

  await page.getByRole('button', { name: 'Nuevo asunto' }).click();
  await expect(page.getByRole('region', { name: 'Nuevo asunto' })).toBeVisible();
  await capture(page, '04-formulario-nuevo-asunto');

  await page.goto(`/asuntos?q=${code}`);
  await page.getByRole('link', { name: code }).click();
  await page.getByRole('button', { name: 'Cerrar asunto' }).click();
  const dialog = page.getByRole('dialog', { name: `¿Cerrar el asunto ${code}?` });
  await expect(dialog).toBeVisible();
  await capture(page, '05-dialogo-cierre');

  await dialog.getByRole('button', { name: 'Sí, cerrar asunto' }).click();
  await expect(page.locator('.details').getByText('Cerrado', { exact: true })).toBeVisible();
  await capture(page, '03-asunto-cerrado');
});
