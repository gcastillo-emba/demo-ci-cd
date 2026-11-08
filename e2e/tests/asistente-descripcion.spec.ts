import {
  featuresWithAssistantOff,
  newEntryForm,
  requestSuggestion,
  suggestionPanel,
} from './support/assistant';
import { ABOGADO_STATE, expect, SOCIA_STATE, test } from './support/auth';
import { createClient, createMatter, runId } from './support/session';
import { isolatedWorkDate, rowWith } from './support/time-entries';

// C4 add-description-assistant — tarea 4.3. Stack con perfil `demo` (asistente encendido y
// proveedor `fake`). Sesiones por rol reutilizadas (storageState del global setup, UN login por
// rol) por el rate limiting del login en nginx. En serie: la socia prepara un asunto abierto.

test.describe.configure({ mode: 'serial' });

const id = runId();
const workDate = isolatedWorkDate();
const clientName = `Cliente Asistente E2E ${id}`;
const matterTitle = `Asunto Asistente E2E ${id}`;
let matterLabel = '';

test.describe('Socia — preparación', () => {
  test.use({ storageState: SOCIA_STATE });

  test('crea un asunto abierto para registrar horas', async ({ page }) => {
    await page.goto('/');
    await createClient(page, clientName, `AST-${id}`);
    const code = await createMatter(page, clientName, matterTitle);
    matterLabel = `${code} — ${matterTitle}`;
  });
});

test.describe('Abogado — asistente de descripción', () => {
  test.use({ storageState: ABOGADO_STATE });

  test.beforeEach(async ({ page }) => {
    await page.goto('/registrar-horas');
    await expect(page.getByRole('heading', { level: 1, name: 'Registrar horas' })).toBeVisible();
  });

  // Scenario: Indicador encendido
  // Scenario: Editar la sugerencia
  // Scenario: Pedir sugerencia no crea registros
  test('pide una sugerencia, la edita y guarda el registro con el texto editado', async ({
    page,
  }) => {
    const form = newEntryForm(page);
    await form.getByRole('combobox', { name: 'Asunto' }).selectOption({ label: matterLabel });
    await form.getByLabel('Fecha de trabajo').fill(workDate);
    await form.getByRole('textbox', { name: 'Horas' }).fill('1,5');

    const note = 'reunion cliente revisar contrato arrendamiento';
    const suggestion = await requestSuggestion(page, note);
    const description = form.getByRole('textbox', { name: 'Descripción' });
    // Mostrar la sugerencia no modifica el campo ni guarda nada.
    await expect(description).toHaveValue(note);
    await expect(page.getByRole('status')).toHaveCount(0);

    await suggestionPanel(page).getByRole('button', { name: 'Editar' }).click();
    await expect(suggestionPanel(page)).toBeHidden();
    await expect(description).toHaveValue(suggestion);
    await expect(description).toBeFocused();
    const edited = `${suggestion} Ref. E2E ${id}`;
    await page.keyboard.type(` Ref. E2E ${id}`);
    await expect(description).toHaveValue(edited);

    await form.getByRole('button', { name: 'Guardar', exact: true }).click();
    await expect(page.getByRole('status')).toContainText('Registro guardado: 1,5 h');

    await page.goto(`/mis-registros?from=${workDate}&to=${workDate}`);
    await expect(rowWith(page, 'Mis registros de horas', `Ref. E2E ${id}`)).toContainText(
      'Pendiente',
    );
  });

  // Scenario: Aceptar la sugerencia
  // Scenario: Guardar después de aceptar
  test('acepta la sugerencia y la registra tal cual', async ({ page }) => {
    const form = newEntryForm(page);
    await form.getByRole('combobox', { name: 'Asunto' }).selectOption({ label: matterLabel });
    await form.getByLabel('Fecha de trabajo').fill(workDate);
    await form.getByRole('textbox', { name: 'Horas' }).fill('0,5');

    const suggestion = await requestSuggestion(page, 'llamada cliente redaccion demanda');
    await suggestionPanel(page).getByRole('button', { name: 'Aceptar' }).click();
    await expect(suggestionPanel(page)).toBeHidden();
    await expect(form.getByRole('textbox', { name: 'Descripción' })).toHaveValue(suggestion);

    await form.getByRole('button', { name: 'Guardar', exact: true }).click();
    await expect(page.getByRole('status')).toContainText('Registro guardado: 0,5 h');
  });

  // Scenario: Descartar la sugerencia
  test('descarta la sugerencia y conserva el texto original', async ({ page }) => {
    const note = `revision escritura ${id}`;
    await requestSuggestion(page, note);
    await suggestionPanel(page).getByRole('button', { name: 'Descartar' }).click();

    await expect(suggestionPanel(page)).toBeHidden();
    await expect(newEntryForm(page).getByRole('textbox', { name: 'Descripción' })).toHaveValue(
      note,
    );
    await expect(page.getByRole('status')).toHaveCount(0);
  });

  // Scenario: Nota vacía en la interfaz
  test('con la descripción vacía pide escribir una nota breve', async ({ page }) => {
    await newEntryForm(page).getByRole('button', { name: 'Sugerir descripción' }).click();
    await expect(page.getByRole('alert')).toContainText(
      'Escribe una nota breve en la descripción antes de pedir una sugerencia.',
    );
    await expect(suggestionPanel(page)).toHaveCount(0);
  });
});

test.describe('Abogado — asistente apagado', () => {
  test.use({ storageState: ABOGADO_STATE });

  // Scenario: Indicador apagado
  test('sin el indicador no aparece "Sugerir descripción"', async ({ page }) => {
    await featuresWithAssistantOff(page);
    const features = page.waitForResponse('**/api/features');
    await page.goto('/registrar-horas');
    await features;
    const form = newEntryForm(page);
    await expect(form.getByRole('textbox', { name: 'Descripción' })).toBeVisible();
    await expect(form.getByRole('button', { name: 'Guardar', exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Sugerir descripción' })).toHaveCount(0);
  });
});
