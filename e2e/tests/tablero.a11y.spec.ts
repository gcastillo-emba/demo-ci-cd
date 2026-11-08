import AxeBuilder from '@axe-core/playwright';
import { ABOGADO_STATE, expect, SOCIA_STATE, test } from './support/auth';
import { PERIOD_PATTERN } from './support/dashboard';

// C3 add-hours-dashboard — tarea 4.6. Revisión automática de accesibilidad (axe) de "Tablero"
// con las sesiones por rol reutilizadas (storageState, sin logins nuevos). Se exige cero
// violaciones de impacto serio o crítico sobre las reglas WCAG 2.x A/AA.
const WCAG_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'];

for (const [role, storageState] of [
  ['Socia', SOCIA_STATE],
  ['Abogado', ABOGADO_STATE],
] as const) {
  test.describe(`${role} — accesibilidad del Tablero`, () => {
    test.use({ storageState });

    test('axe no reporta violaciones serias ni críticas', async ({ page }, testInfo) => {
      await page.goto('/tablero');
      await expect(page.getByRole('heading', { level: 1, name: 'Tablero' })).toBeVisible();
      await expect(page.getByText(PERIOD_PATTERN)).toBeVisible();

      const results = await new AxeBuilder({ page }).withTags(WCAG_TAGS).analyze();
      await testInfo.attach('axe-results', {
        body: JSON.stringify(results.violations, null, 2),
        contentType: 'application/json',
      });
      const serious = results.violations
        .filter((v) => v.impact === 'serious' || v.impact === 'critical')
        .map((v) => `${v.id} (${v.impact}): ${v.nodes.length} nodo(s)`);
      expect(serious).toEqual([]);
    });
  });
}
