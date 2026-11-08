import { ABOGADO_STATE, expect, SOCIA_STATE, test } from './support/auth';
import { createClient, createMatter, runId } from './support/session';
import { isolatedWorkDate, registerHours, rowWith } from './support/time-entries';

// C2 add-time-entries — tareas 4.2 y 4.3. Sesiones por rol reutilizadas (storageState del global
// setup, UN login por rol) por el rate limiting del login en nginx. Las pruebas comparten datos y
// corren en serie: socia prepara un asunto, abogado registra y edita, socia resuelve, abogado ve.

test.describe.configure({ mode: 'serial' });

const id = runId();
const workDate = isolatedWorkDate();
const clientName = `Cliente Horas E2E ${id}`;
const matterTitle = `Asunto Horas E2E ${id}`;
const editedDescription = `Revisión de contrato E2E ${id}`;
const longDescription = `Redacción de demanda E2E ${id}`;
const rejectionReason = `Descripción insuficiente E2E ${id}`;
const listQuery = `?from=${workDate}&to=${workDate}`;
let matterLabel = '';

test.describe('Socia — preparación', () => {
  test.use({ storageState: SOCIA_STATE });

  test('crea un asunto abierto para registrar horas', async ({ page }) => {
    await page.goto('/');
    await createClient(page, clientName, `HRS-${id}`);
    const code = await createMatter(page, clientName, matterTitle);
    matterLabel = `${code} — ${matterTitle}`;
  });
});

test.describe('Abogado — registrar y editar', () => {
  test.use({ storageState: ABOGADO_STATE });

  // Scenario: Alta con coma decimal
  test('registra 1,5 h con coma decimal y ve el registro PENDING en "Mis registros"', async ({
    page,
  }) => {
    await registerHours(page, {
      matterLabel,
      workDate,
      hours: '1,5',
      description: editedDescription,
    });
    await expect(page.getByRole('status')).toContainText(
      /Registro guardado: 1,5 h en AS-\d{4}-\d{4}/,
    );
    await expect(page.getByRole('status')).toContainText('estado Pendiente');
    // El formulario queda limpio.
    await expect(page.getByRole('textbox', { name: 'Horas' })).toHaveValue('');

    await registerHours(page, { matterLabel, workDate, hours: '20', description: longDescription });
    await expect(page.getByRole('status')).toContainText('Registro guardado: 20 h');

    await page.goto(`/mis-registros${listQuery}`);
    await expect(page.getByRole('heading', { level: 1, name: 'Mis registros' })).toBeVisible();
    const row = rowWith(page, 'Mis registros de horas', editedDescription);
    await expect(row).toContainText('1,5');
    await expect(row).toContainText('Pendiente');
    await expect(rowWith(page, 'Mis registros de horas', longDescription)).toContainText(
      'Pendiente',
    );
  });

  // Scenario: Editar un registro editable
  test('edita el registro pendiente a 2,5 h', async ({ page }) => {
    await page.goto(`/mis-registros${listQuery}`);
    const row = rowWith(page, 'Mis registros de horas', editedDescription);
    await row.getByRole('button', { name: /^Editar/ }).click();
    const form = page.getByRole('region', { name: /^Editar registro del/ });
    await expect(form.getByRole('textbox', { name: 'Horas' })).toHaveValue('1,5');
    await form.getByRole('textbox', { name: 'Horas' }).fill('2,5');
    await form.getByRole('button', { name: 'Guardar cambios' }).click();

    await expect(page.getByText(/^Registro actualizado: 2,5 h en AS-\d{4}-\d{4}/)).toBeVisible();
    await expect(form).toBeHidden();
    await expect(row).toContainText('2,5');
    await expect(row).toContainText('Pendiente');
  });

  // Scenario: Error de límite diario visible
  test('recibe en español el error de límite diario y conserva los datos', async ({ page }) => {
    await registerHours(page, { matterLabel, workDate, hours: '5', description: `Exceso ${id}` });

    const message =
      'Se supera el límite de 24 horas diarias: ya tienes 22,5 h registradas ese día y te quedan 1,5 h.';
    await expect(page.getByRole('alert')).toContainText(message);
    const hours = page.getByRole('textbox', { name: 'Horas' });
    await expect(hours).toHaveAttribute('aria-invalid', 'true');
    await expect(hours).toHaveValue('5');
    await expect(page.getByRole('textbox', { name: 'Descripción' })).toHaveValue(`Exceso ${id}`);
    await expect(page.getByText('daily-hours-exceeded')).toHaveCount(0);
  });

  // Scenario: Error de incremento de horas
  test('"1,25" muestra el error de incrementos de 0,1 junto al campo', async ({ page }) => {
    await registerHours(page, { matterLabel, workDate, hours: '1,25', description: `Inc ${id}` });
    await expect(page.getByRole('textbox', { name: 'Horas' })).toHaveAccessibleDescription(
      /incrementos de 0,1/,
    );
    await expect(page.getByText('hours-invalid-increment')).toHaveCount(0);
  });

  // Scenario: Abogado no ve la opción
  test('no ve "Aprobaciones" en el menú', async ({ page }) => {
    await page.goto('/');
    const nav = page.getByRole('navigation', { name: 'Principal' });
    await expect(nav.getByRole('link', { name: 'Registrar horas' })).toBeVisible();
    await expect(nav.getByRole('link', { name: 'Aprobaciones' })).toHaveCount(0);
  });
});

test.describe('Socia — aprobaciones', () => {
  test.use({ storageState: SOCIA_STATE });

  // Scenario: Aprobar desde la cola / Rechazar exige motivo
  test('aprueba un registro y rechaza otro con motivo desde "Aprobaciones"', async ({ page }) => {
    await page.goto('/');
    const nav = page.getByRole('navigation', { name: 'Principal' });
    // Scenario: Socio no ve la opción
    await expect(nav.getByRole('link', { name: 'Registrar horas' })).toHaveCount(0);
    await nav.getByRole('link', { name: 'Aprobaciones' }).click();
    await expect(page.getByRole('heading', { level: 1, name: 'Aprobaciones' })).toBeVisible();

    await page.goto(`/aprobaciones${listQuery}`);
    const table = 'Registros pendientes de aprobación';
    const toApprove = rowWith(page, table, editedDescription);
    const toReject = rowWith(page, table, longDescription);
    await expect(toApprove).toContainText('Abogado Uno');
    await expect(toApprove).toContainText('2,5');
    await expect(toReject).toContainText('20');

    await toApprove.getByRole('button', { name: /^Aprobar/ }).click();
    await expect(page.getByText(/^Registro aprobado: Abogado Uno, 2,5 h/)).toBeVisible();
    await expect(toApprove).toHaveCount(0);

    await toReject.getByRole('button', { name: /^Rechazar/ }).click();
    const dialog = page.getByRole('dialog', { name: 'Rechazar registro' });
    await expect(dialog).toBeVisible();
    const confirm = dialog.getByRole('button', { name: 'Confirmar rechazo' });
    await expect(confirm).toBeDisabled();
    await expect(dialog.getByText('0/500 caracteres')).toBeVisible();
    await dialog.getByRole('textbox', { name: 'Motivo del rechazo' }).fill(rejectionReason);
    await expect(confirm).toBeEnabled();
    await confirm.click();

    await expect(dialog).toBeHidden();
    await expect(page.getByText(/^Registro rechazado: Abogado Uno, 20 h/)).toBeVisible();
    await expect(toReject).toHaveCount(0);
    await expect(page.getByText('No hay registros pendientes de aprobación')).toBeVisible();
  });
});

test.describe('Abogado — estados resueltos', () => {
  test.use({ storageState: ABOGADO_STATE });

  // Scenario: Registros no editables
  test('ve el registro aprobado, el rechazado con su motivo y ya no puede editarlos', async ({
    page,
  }) => {
    await page.goto(`/mis-registros${listQuery}`);
    const approved = rowWith(page, 'Mis registros de horas', editedDescription);
    const rejected = rowWith(page, 'Mis registros de horas', longDescription);
    await expect(approved).toContainText('Aprobada');
    await expect(rejected).toContainText('Rechazada');
    await expect(rejected).toContainText(rejectionReason);
    await expect(approved.getByRole('button', { name: /^Editar/ })).toHaveCount(0);
    await expect(rejected.getByRole('button', { name: /^Editar/ })).toHaveCount(0);
  });
});
