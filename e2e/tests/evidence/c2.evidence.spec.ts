import { mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { expect, test, type Page } from '@playwright/test';
import { ABOGADO_STATE, SOCIA_STATE } from '../support/auth-files';
import { createClient, createMatter, runId } from '../support/session';
import { isolatedWorkDate, registerHours, rolePage, rowWith } from '../support/time-entries';

// Evidencia visual de C2 (tarea 4.4). Fuera de la corrida por defecto: solo con
// `npm run evidence -w e2e` (playwright.evidence.config.ts). Capturas en
// docs/evidence/add-time-entries/. Sesiones por rol desde el storageState del global setup.
const OUT_DIR = fileURLToPath(new URL('../../../docs/evidence/add-time-entries/', import.meta.url));

async function capture(page: Page, name: string) {
  mkdirSync(OUT_DIR, { recursive: true });
  await page.screenshot({ path: `${OUT_DIR}${name}.png`, fullPage: true });
}

test('evidencia C2: registrar, mis registros, edición, límite diario y aprobaciones @evidencia', async ({
  browser,
}) => {
  const id = runId();
  const workDate = isolatedWorkDate();
  const listQuery = `?from=${workDate}&to=${workDate}`;
  const first = `Revisión de contrato de evidencia ${id}`;
  const second = `Redacción de demanda de evidencia ${id}`;
  const socia = await rolePage(browser, SOCIA_STATE);
  const abogado = await rolePage(browser, ABOGADO_STATE);

  await socia.goto('/');
  const clientName = `Cliente Evidencia Horas ${id}`;
  await createClient(socia, clientName, `EVH-${id}`);
  const title = `Asunto de evidencia de horas ${id}`;
  const code = await createMatter(socia, clientName, title);
  const matterLabel = `${code} — ${title}`;

  // 01 — formulario "Registrar horas" con coma decimal.
  await abogado.goto('/registrar-horas');
  const form = abogado.getByRole('region', { name: 'Nuevo registro de horas' });
  await form.getByRole('combobox', { name: 'Asunto' }).selectOption({ label: matterLabel });
  await form.getByLabel('Fecha de trabajo').fill(workDate);
  await form.getByRole('textbox', { name: 'Horas' }).fill('1,5');
  await form.getByRole('textbox', { name: 'Descripción' }).fill(first);
  await capture(abogado, '01-registrar-horas');
  await form.getByRole('button', { name: 'Guardar', exact: true }).click();
  await expect(abogado.getByRole('status')).toContainText('Registro guardado: 1,5 h');
  await capture(abogado, '02-registro-guardado');

  await registerHours(abogado, { matterLabel, workDate, hours: '20', description: second });
  await expect(abogado.getByRole('status')).toContainText('Registro guardado: 20 h');

  // 03 — error de incremento junto al campo.
  await registerHours(abogado, { matterLabel, workDate, hours: '1,25', description: 'Incremento' });
  await expect(abogado.getByRole('textbox', { name: 'Horas' })).toHaveAccessibleDescription(
    /incrementos de 0,1/,
  );
  await capture(abogado, '03-error-incremento');

  // 04 — límite diario con horas ya cargadas y restantes.
  await registerHours(abogado, { matterLabel, workDate, hours: '5', description: 'Exceso' });
  await expect(abogado.getByRole('alert')).toContainText('te quedan 2,5 h');
  await capture(abogado, '04-error-limite-diario');

  // 05–06 — "Mis registros" y edición.
  await abogado.goto(`/mis-registros${listQuery}`);
  await expect(rowWith(abogado, 'Mis registros de horas', first)).toContainText('Pendiente');
  await capture(abogado, '05-mis-registros');
  await rowWith(abogado, 'Mis registros de horas', first)
    .getByRole('button', { name: /^Editar/ })
    .click();
  const edit = abogado.getByRole('region', { name: /^Editar registro del/ });
  await edit.getByRole('textbox', { name: 'Horas' }).fill('2,5');
  await capture(abogado, '06-editar-registro');
  await edit.getByRole('button', { name: 'Guardar cambios' }).click();
  await expect(abogado.getByText(/^Registro actualizado: 2,5 h/)).toBeVisible();

  // 07–08 — "Aprobaciones": cola y diálogo de rechazo.
  await socia.goto(`/aprobaciones${listQuery}`);
  const table = 'Registros pendientes de aprobación';
  await expect(rowWith(socia, table, first)).toContainText('2,5');
  await capture(socia, '07-aprobaciones');
  await rowWith(socia, table, first)
    .getByRole('button', { name: /^Aprobar/ })
    .click();
  await expect(socia.getByText(/^Registro aprobado:/)).toBeVisible();
  await rowWith(socia, table, second)
    .getByRole('button', { name: /^Rechazar/ })
    .click();
  const dialog = socia.getByRole('dialog', { name: 'Rechazar registro' });
  await dialog
    .getByRole('textbox', { name: 'Motivo del rechazo' })
    .fill('Descripción insuficiente: detallar las piezas redactadas.');
  await capture(socia, '08-dialogo-rechazo');
  await dialog.getByRole('button', { name: 'Confirmar rechazo' }).click();
  await expect(socia.getByText('No hay registros pendientes de aprobación')).toBeVisible();

  // 09 — el abogado ve los estados resueltos y el motivo.
  await abogado.goto(`/mis-registros${listQuery}`);
  await expect(rowWith(abogado, 'Mis registros de horas', second)).toContainText('Rechazada');
  await expect(rowWith(abogado, 'Mis registros de horas', first)).toContainText('Aprobada');
  await capture(abogado, '09-mis-registros-estados');

  await socia.context().close();
  await abogado.context().close();
});
