import type { Page } from '@playwright/test';
import { ABOGADO_STATE, expect, SOCIA_STATE, test } from './support/auth';
import { ABOGADO } from './support/session';
import {
  emptyLawyerSummary,
  LAWYER_TABLE,
  MATTER_TABLE,
  OTHER_LAWYERS,
  PERIOD_PATTERN,
} from './support/dashboard';

// C3 add-hours-dashboard — tarea 4.5. Sesiones por rol reutilizadas (storageState del global
// setup, UN login por rol) por el rate limiting del login en nginx (ADR-006). Datos: seed `R__`
// del perfil demo (tres abogados con horas en los últimos 30 días). Solo lectura: no altera datos.

/** Entra a "Tablero" desde la navegación principal y espera el resumen. */
async function openDashboard(page: Page) {
  await page.goto('/');
  await page
    .getByRole('navigation', { name: 'Principal' })
    .getByRole('link', { name: 'Tablero' })
    .click();
  await expect(page.getByRole('heading', { level: 1, name: 'Tablero' })).toBeVisible();
  await expect(page.getByText(PERIOD_PATTERN)).toBeVisible();
}

test.describe('Socia — Tablero', () => {
  test.use({ storageState: SOCIA_STATE });

  // Scenario: El socio abre el Tablero
  // Scenario: El socio ve a todos los abogados
  test('ve período, totales, dos tablas accesibles y a varios abogados', async ({ page }) => {
    await openDashboard(page);

    const totals = page.getByRole('region', { name: 'Totales del período' });
    await expect(totals.getByText(/^Aprobadas: \d+(,\d)? h$/)).toBeVisible();
    await expect(totals.getByText(/^Pendientes: \d+(,\d)? h$/)).toBeVisible();
    // Sin controles para cambiar el rango.
    await expect(page.locator('main input, main select')).toHaveCount(0);

    // Scenario: Estructura accesible de la tabla por asunto / por abogado
    for (const name of [MATTER_TABLE, LAWYER_TABLE]) {
      const table = page.getByRole('table', { name });
      await expect(table.locator('caption')).toHaveText(name);
      await expect(table.locator('thead th[scope="col"]')).toHaveCount(4);
      await expect(table.locator('tbody th[scope="row"]').first()).toBeVisible();
    }

    const lawyers = page.getByRole('table', { name: LAWYER_TABLE });
    await expect(lawyers.getByRole('rowheader', { name: ABOGADO.fullName })).toBeVisible();
    for (const name of OTHER_LAWYERS) {
      await expect(lawyers.getByRole('rowheader', { name })).toBeVisible();
    }
    expect(await lawyers.locator('tbody tr').count()).toBeGreaterThanOrEqual(3);
    expect(
      await page.getByRole('table', { name: MATTER_TABLE }).locator('tbody tr').count(),
    ).toBeGreaterThanOrEqual(2);

    // Scenario: Diferencia entre aprobadas y pendientes visible en escala de grises
    await expect(lawyers.locator('tbody tr').first()).toContainText(
      /Aprobadas: \d+(,\d)? h · Pendientes: \d+(,\d)? h/,
    );
    await expect(lawyers.locator('[aria-hidden="true"]').first()).toBeAttached();
  });
});

test.describe('Abogado — Tablero', () => {
  test.use({ storageState: ABOGADO_STATE });

  // Scenario: El abogado ve solo su fila
  test('ve una sola fila con su nombre y ningún nombre de otro abogado', async ({ page }) => {
    await openDashboard(page);

    const lawyers = page.getByRole('table', { name: LAWYER_TABLE });
    await expect(lawyers.locator('tbody tr')).toHaveCount(1);
    await expect(lawyers.getByRole('rowheader')).toHaveText(ABOGADO.fullName);
    for (const name of OTHER_LAWYERS) {
      await expect(page.getByText(name)).toHaveCount(0);
    }
  });

  // Scenario: Abogado sin horas en el Tablero (presentación; el vacío real lo cubren los IT 4.3)
  test('con un resumen vacío muestra el mensaje y su fila propia en cero', async ({ page }) => {
    await page.route('**/api/dashboard/summary', (route) =>
      route.fulfill({ json: emptyLawyerSummary() }),
    );
    await openDashboard(page);

    await expect(page.getByText('No hay horas registradas en los últimos 30 días.')).toBeVisible();
    await expect(page.getByRole('table', { name: MATTER_TABLE })).toHaveCount(0);
    const row = page.getByRole('table', { name: LAWYER_TABLE }).locator('tbody tr');
    await expect(row).toHaveCount(1);
    await expect(row).toContainText('Aprobadas: 0 h · Pendientes: 0 h');
  });
});

test.describe('Sin sesión', () => {
  // Scenario: Sesión expirada en el Tablero
  test('abrir /tablero sin sesión lleva al inicio de sesión sin datos', async ({ page }) => {
    await page.goto('/tablero');

    await expect(page.getByRole('heading', { name: 'Iniciar sesión' })).toBeVisible();
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByRole('table')).toHaveCount(0);
  });
});
