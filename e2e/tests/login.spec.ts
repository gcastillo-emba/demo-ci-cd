import { expect, test, type Page } from '@playwright/test';

// Usuario sintético sembrado por el perfil `demo` (README / .env.example). Solo para la demo.
const SOCIA = { email: 'socia@asuntos.test', password: 'Demo2026!', fullName: 'Socia Demo' };

async function login(page: Page, email: string, password: string) {
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Contraseña').fill(password);
  await page.getByRole('button', { name: 'Ingresar' }).click();
}

test.describe('Inicio de sesión (walking skeleton)', () => {
  test('la socia inicia sesión y ve su nombre y rol', async ({ page }) => {
    await login(page, SOCIA.email, SOCIA.password);

    await expect(page).toHaveURL(/\/$/);
    await expect(
      page.getByRole('heading', { level: 1, name: `Bienvenido/a, ${SOCIA.fullName}` }),
    ).toBeVisible();
    await expect(page.getByRole('main').getByText('Socio', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Cerrar sesión' })).toBeVisible();
  });

  test('una contraseña incorrecta muestra el error y no inicia sesión', async ({ page }) => {
    await login(page, SOCIA.email, 'Incorrecta2026!');

    await expect(page.getByRole('alert')).toContainText(/no son correctos|inválid/i);
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Iniciar sesión' })).toBeVisible();
  });

  test('cerrar sesión vuelve al login y protege el inicio', async ({ page }) => {
    await login(page, SOCIA.email, SOCIA.password);
    await expect(page.getByRole('heading', { level: 1, name: /Bienvenido\/a/ })).toBeVisible();

    await page.getByRole('button', { name: 'Cerrar sesión' }).click();

    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Iniciar sesión' })).toBeVisible();

    await page.goto('/');
    await expect(page).toHaveURL(/\/login$/);
  });
});
