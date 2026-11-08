import { expect, test } from '@playwright/test';
import { ABOGADO, createClient, createMatter, goTo, login, runId, SOCIA } from './support/session';

// C1 add-clients-matters — tareas 4.2 y 4.3. Datos sintéticos con sufijo único por ejecución.

test.describe('Clientes y asuntos — Socio', () => {
  test('crea un cliente y un asunto, lo filtra, pagina y lo cierra con confirmación', async ({
    page,
  }) => {
    const id = runId();
    const clientName = `Cliente E2E ${id}`;
    const title = `Asunto E2E ${id}`;
    await login(page, SOCIA);

    // Scenario: Socio crea un cliente desde la pantalla
    await createClient(page, clientName, `E2E-${id}`);
    await expect(
      page.getByRole('table', { name: 'Lista de clientes' }).getByRole('rowheader', {
        name: clientName,
      }),
    ).toBeVisible();

    // Scenario: Socio crea un asunto y ve su código
    const code = await createMatter(page, clientName, title);
    expect(code).toMatch(/^AS-\d{4}-\d{4}$/);
    const matters = page.getByRole('table', { name: 'Lista de asuntos' });
    const row = matters.getByRole('row').filter({ hasText: code });
    await expect(row).toContainText(title);
    await expect(row).toContainText(clientName);
    await expect(row).toContainText('Abierto');

    // Scenario: Filtrar asuntos abiertos de un cliente
    await page.goto('/asuntos');
    await page.getByRole('combobox', { name: 'Cliente' }).selectOption({ label: clientName });
    await page.getByRole('combobox', { name: 'Estado' }).selectOption({ label: 'Abierto' });
    await expect(page).toHaveURL(/status=OPEN/);
    await expect(matters.getByRole('row')).toHaveCount(2); // encabezado + 1 asunto
    await expect(row).toContainText('Abierto');

    // Scenario: Buscar por código (interfaz)
    await page.goto('/asuntos');
    await page.getByRole('searchbox', { name: 'Buscar por título o código' }).fill(code);
    await expect(page).toHaveURL(new RegExp(`q=${code}`));
    await expect(matters.getByRole('row')).toHaveCount(2);
    await expect(row).toBeVisible();

    // Paginación (spec "Pantalla Asuntos"): con más de 20 asuntos, Siguiente avanza de página.
    await page.goto('/asuntos');
    const indicator = page.getByText(/^Página 1 de \d+$/);
    await expect(indicator).toBeVisible();
    const total = Number((await indicator.textContent())?.match(/de (\d+)/)?.[1] ?? '1');
    if (total > 1) {
      await page.getByRole('button', { name: 'Siguiente' }).click();
      await expect(page.getByText(`Página 2 de ${total}`)).toBeVisible();
      await expect(page).toHaveURL(/page=1/);
    }

    // Scenario: Socio cancela el cierre
    await page.goto(`/asuntos?q=${code}`);
    await row.getByRole('link', { name: code }).click();
    await expect(page.getByRole('heading', { level: 1, name: `Asunto ${code}` })).toBeVisible();
    const status = page.locator('.details').getByText('Abierto', { exact: true });
    await expect(status).toBeVisible();
    await page.getByRole('button', { name: 'Cerrar asunto' }).click();
    const dialog = page.getByRole('dialog', { name: `¿Cerrar el asunto ${code}?` });
    await expect(dialog).toBeVisible();
    await expect(dialog).toContainText('irreversible');
    await dialog.getByRole('button', { name: 'Cancelar' }).click();
    await expect(dialog).toBeHidden();
    await expect(status).toBeVisible();

    // Scenario: Socio confirma el cierre
    await page.getByRole('button', { name: 'Cerrar asunto' }).click();
    await dialog.getByRole('button', { name: 'Sí, cerrar asunto' }).click();
    await expect(dialog).toBeHidden();
    await expect(page.locator('.details').getByText('Cerrado', { exact: true })).toBeVisible();
    await expect(page.getByText('Cerrado el')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Cerrar asunto' })).toHaveCount(0);
    // Scenario: La interfaz no ofrece reabrir
    await expect(page.getByRole('button', { name: /reabrir|editar/i })).toHaveCount(0);

    // El listado refleja el cierre.
    await page.goto(`/asuntos?status=CLOSED&q=${code}`);
    await expect(matters.getByRole('row').filter({ hasText: code })).toContainText('Cerrado');
  });

  // Scenario: Identificación tributaria duplicada en el formulario
  test('una identificación tributaria repetida mantiene el formulario abierto con el mensaje', async ({
    page,
  }) => {
    const id = runId();
    const taxId = `E2E-DUP-${id}`;
    await login(page, SOCIA);
    await createClient(page, `Cliente Duplicado A ${id}`, taxId);

    await page.getByRole('button', { name: 'Nuevo cliente' }).click();
    const form = page.getByRole('region', { name: 'Nuevo cliente' });
    await form.getByRole('textbox', { name: 'Nombre' }).fill(`Cliente Duplicado B ${id}`);
    const taxField = form.getByRole('textbox', { name: 'Identificación tributaria (opcional)' });
    await taxField.fill(taxId);
    await form.getByRole('button', { name: 'Guardar cliente' }).click();

    await expect(
      form.getByText('Ya existe un cliente con esa identificación tributaria.'),
    ).toBeVisible();
    await expect(taxField).toHaveAttribute('aria-invalid', 'true');
    await expect(form).toBeVisible();
  });
});

test.describe('Clientes y asuntos — Abogado', () => {
  // Scenario: Abogado no ve formularios de alta · Abogado no ve la acción de cierre
  test('ve Clientes y Asuntos sin botones de alta ni de cierre, y la API rechaza su POST con 403', async ({
    page,
  }) => {
    await login(page, ABOGADO);

    await goTo(page, 'Clientes');
    await expect(page.getByText(/clientes? encontrados?|No se encontraron clientes/)).toBeVisible();
    await expect(page.getByRole('button', { name: 'Nuevo cliente' })).toHaveCount(0);

    await goTo(page, 'Asuntos');
    await expect(page.getByText(/asuntos? encontrados?|No se encontraron asuntos/)).toBeVisible();
    await expect(page.getByRole('button', { name: 'Nuevo asunto' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Cerrar asunto' })).toHaveCount(0);

    // Detalle de un asunto abierto (si el seed demo o otra prueba dejó alguno).
    await page.goto('/asuntos?status=OPEN');
    await expect(page.getByText(/asuntos? encontrados?|No se encontraron asuntos/)).toBeVisible();
    const firstOpen = page
      .getByRole('table', { name: 'Lista de asuntos' })
      .getByRole('link')
      .first();
    if ((await firstOpen.count()) > 0) {
      await firstOpen.click();
      await expect(page.getByRole('heading', { level: 1, name: /^Asunto AS-/ })).toBeVisible();
      await expect(page.locator('.details').getByText('Abierto', { exact: true })).toBeVisible();
      await expect(page.getByRole('button', { name: 'Cerrar asunto' })).toHaveCount(0);
    }

    // Scenario: Abogado intenta crear un cliente (la API es quien hace cumplir la regla).
    const token = await page.evaluate(() => sessionStorage.getItem('ah.token'));
    expect(token).toBeTruthy();
    const response = await page.request.post('/api/clients', {
      headers: { Authorization: `Bearer ${token}` },
      data: { name: `Cliente prohibido ${runId()}` },
    });
    expect(response.status()).toBe(403);
    expect(response.headers()['content-type']).toContain('application/problem+json');
    expect((await response.json()).code).toBe('forbidden');
  });
});
