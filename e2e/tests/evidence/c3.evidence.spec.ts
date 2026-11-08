import { mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import type { Page } from '@playwright/test';
import { ABOGADO_STATE, expect, rolePage, SOCIA_STATE, test } from '../support/auth';
import { emptyLawyerSummary, LAWYER_TABLE, PERIOD_PATTERN } from '../support/dashboard';

// Evidencia visual de C3 (tarea 4.7). Fuera de la corrida por defecto: solo con
// `npm run evidence -w e2e` (playwright.evidence.config.ts). Capturas en
// docs/evidence/add-hours-dashboard/. Sesiones por rol desde el storageState del global setup.
const OUT_DIR = fileURLToPath(
  new URL('../../../docs/evidence/add-hours-dashboard/', import.meta.url),
);

async function capture(page: Page, name: string) {
  mkdirSync(OUT_DIR, { recursive: true });
  await page.screenshot({ path: `${OUT_DIR}${name}.png`, fullPage: true });
}

async function openDashboard(page: Page) {
  await page.goto('/tablero');
  await expect(page.getByRole('heading', { level: 1, name: 'Tablero' })).toBeVisible();
  await expect(page.getByText(PERIOD_PATTERN)).toBeVisible();
}

test('evidencia C3: tablero del socio, del abogado, vacío y en escala de grises @evidencia', async ({
  browser,
}) => {
  const socia = await rolePage(browser, SOCIA_STATE);
  const abogado = await rolePage(browser, ABOGADO_STATE);

  // 01 — socio: todos los abogados y asuntos.
  await openDashboard(socia);
  await expect(
    socia.getByRole('table', { name: LAWYER_TABLE }).locator('tbody tr'),
  ).not.toHaveCount(0);
  await capture(socia, '01-tablero-socio');

  // 02 — abogado: solo su fila.
  await openDashboard(abogado);
  await expect(abogado.getByRole('table', { name: LAWYER_TABLE }).locator('tbody tr')).toHaveCount(
    1,
  );
  await capture(abogado, '02-tablero-abogado');

  // 03 — estado vacío (respuesta simulada: el seed de demo siempre tiene horas en la ventana).
  await abogado.route('**/api/dashboard/summary', (route) =>
    route.fulfill({ json: emptyLawyerSummary() }),
  );
  await openDashboard(abogado);
  await expect(abogado.getByText('No hay horas registradas en los últimos 30 días.')).toBeVisible();
  await capture(abogado, '03-tablero-vacio');
  await abogado.unroute('**/api/dashboard/summary');

  // 04 — escala de grises: aprobadas (sólido) vs pendientes (rayado) se distinguen sin color.
  await openDashboard(socia);
  // La CSP (default-src 'self') bloquea un <style> inyectado; el CSSOM sí está permitido.
  await socia.evaluate(() => {
    document.documentElement.style.filter = 'grayscale(1)';
  });
  await capture(socia, '04-tablero-escala-grises');

  await socia.context().close();
  await abogado.context().close();
});
