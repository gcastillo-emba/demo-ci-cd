import { expect, type Page } from '@playwright/test';

// Usuarios sintéticos sembrados por el perfil `demo` (README / .env.example). Solo para la demo.
export const SOCIA = { email: 'socia@asuntos.test', password: 'Demo2026!', fullName: 'Socia Demo' };
export const ABOGADO = {
  email: 'abogado1@asuntos.test',
  password: 'Demo2026!',
  fullName: 'Abogado Uno',
};

export async function login(
  page: Page,
  user: { email: string; password: string; fullName: string },
) {
  await page.goto('/login');
  await page.getByLabel('Email').fill(user.email);
  await page.getByLabel('Contraseña').fill(user.password);
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await expect(
    page.getByRole('heading', { level: 1, name: `Bienvenido/a, ${user.fullName}` }),
  ).toBeVisible();
}

export async function goTo(page: Page, section: 'Clientes' | 'Asuntos') {
  await page
    .getByRole('navigation', { name: 'Principal' })
    .getByRole('link', { name: section })
    .click();
  await expect(page.getByRole('heading', { level: 1, name: section })).toBeVisible();
}

/** Sufijo único por ejecución para no chocar con datos de corridas anteriores. */
export function runId(): string {
  return `${Date.now().toString(36)}${Math.floor(Math.random() * 1296).toString(36)}`.toUpperCase();
}

export async function createClient(page: Page, name: string, taxId?: string) {
  await goTo(page, 'Clientes');
  await page.getByRole('button', { name: 'Nuevo cliente' }).click();
  const form = page.getByRole('region', { name: 'Nuevo cliente' });
  await form.getByRole('textbox', { name: 'Nombre' }).fill(name);
  if (taxId) {
    await form.getByRole('textbox', { name: 'Identificación tributaria (opcional)' }).fill(taxId);
  }
  await form.getByRole('button', { name: 'Guardar cliente' }).click();
  await expect(page.getByText(`Cliente creado: ${name}.`)).toBeVisible();
  await expect(form).toBeHidden();
}

/** Crea un asunto desde la pantalla Asuntos y devuelve el código asignado por el servidor. */
export async function createMatter(page: Page, clientName: string, title: string) {
  await goTo(page, 'Asuntos');
  await page.getByRole('button', { name: 'Nuevo asunto' }).click();
  const form = page.getByRole('region', { name: 'Nuevo asunto' });
  await form.getByRole('combobox', { name: 'Cliente' }).selectOption({ label: clientName });
  await form.getByRole('textbox', { name: 'Título' }).fill(title);
  await form
    .getByRole('textbox', { name: 'Descripción (opcional)' })
    .fill('Asunto sintético de E2E.');
  await form.getByRole('button', { name: 'Guardar asunto' }).click();

  const notice = page.getByText(/^Asunto creado con el código AS-\d{4}-\d{4}\.$/);
  await expect(notice).toBeVisible();
  const code = (await notice.textContent())?.match(/AS-\d{4}-\d{4}/)?.[0];
  if (!code) throw new Error('No se pudo leer el código del asunto creado');
  return code;
}
