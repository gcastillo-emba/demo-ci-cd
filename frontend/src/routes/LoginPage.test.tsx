import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { TOKEN_KEY } from '../api/token';
import { jsonResponse, mockFetch, problemResponse, renderApp } from '../test/utils';

const socia = {
  id: '00000000-0000-4000-8000-000000000001',
  email: 'socia@asuntos.test',
  fullName: 'Socia Demo',
  role: 'PARTNER',
};

const loginOk = () =>
  jsonResponse({
    accessToken: 'jwt-de-prueba',
    tokenType: 'Bearer',
    expiresIn: 7200,
    user: socia,
  });

describe('LoginPage', () => {
  it('muestra los campos con etiqueta y el botón Ingresar', async () => {
    renderApp('/login');

    expect(await screen.findByRole('heading', { level: 1, name: 'Iniciar sesión' })).toBeVisible();
    expect(screen.getByLabelText(/email/i)).toHaveAttribute('type', 'email');
    expect(screen.getByLabelText(/contraseña/i)).toHaveAttribute('type', 'password');
    expect(screen.getByRole('button', { name: 'Ingresar' })).toBeEnabled();
    expect(document.querySelector('a.skip-link')).toHaveAttribute('href', '#main');
  });

  it('valida en español los campos vacíos sin llamar a la API', async () => {
    const { fn } = mockFetch({});
    const user = userEvent.setup();
    renderApp('/login');

    await user.click(await screen.findByRole('button', { name: 'Ingresar' }));

    const email = screen.getByLabelText(/email/i);
    const password = screen.getByLabelText(/contraseña/i);
    expect(email).toHaveAttribute('aria-invalid', 'true');
    expect(email).toHaveAccessibleDescription('Ingresa tu email.');
    expect(password).toHaveAccessibleDescription('Ingresa tu contraseña.');
    expect(email).toHaveFocus();
    expect(fn).not.toHaveBeenCalled();
  });

  it('valida el formato del email', async () => {
    mockFetch({});
    const user = userEvent.setup();
    renderApp('/login');

    await user.type(await screen.findByLabelText(/email/i), 'no-es-un-email');
    await user.type(screen.getByLabelText(/contraseña/i), 'algo');
    await user.click(screen.getByRole('button', { name: 'Ingresar' }));

    expect(screen.getByLabelText(/email/i)).toHaveAccessibleDescription('Ingresa un email válido.');
  });

  it('muestra el detail del problema ante un 401 y no guarda token', async () => {
    mockFetch({
      'POST /api/auth/login': () =>
        problemResponse({
          type: 'https://asuntos-horas.example/problems/invalid-credentials',
          title: 'Credenciales inválidas',
          status: 401,
          detail: 'El email o la contraseña no son correctos.',
          code: 'invalid-credentials',
          traceId: '4bf92f3577b34da6a3ce929d0e0e4736',
        }),
    });
    const user = userEvent.setup();
    renderApp('/login');

    await user.type(await screen.findByLabelText(/email/i), 'socia@asuntos.test');
    await user.type(screen.getByLabelText(/contraseña/i), 'incorrecta');
    await user.click(screen.getByRole('button', { name: 'Ingresar' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('El email o la contraseña no son correctos.');
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull();
    expect(screen.getByRole('button', { name: 'Ingresar' })).toBeEnabled();
  });

  it('muestra un mensaje genérico en español si el servidor no responde', async () => {
    mockFetch({
      'POST /api/auth/login': () => {
        throw new TypeError('Failed to fetch');
      },
    });
    const user = userEvent.setup();
    renderApp('/login');

    await user.type(await screen.findByLabelText(/email/i), 'socia@asuntos.test');
    await user.type(screen.getByLabelText(/contraseña/i), 'Demo2026!');
    await user.click(screen.getByRole('button', { name: 'Ingresar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /No se pudo conectar con el servidor/,
    );
  });

  it('con credenciales válidas guarda el token y redirige al inicio', async () => {
    const { calls } = mockFetch({
      'POST /api/auth/login': loginOk,
      'GET /api/me': () => jsonResponse(socia),
    });
    const user = userEvent.setup();
    const { router } = renderApp('/login');

    await user.type(await screen.findByLabelText(/email/i), 'socia@asuntos.test');
    await user.type(screen.getByLabelText(/contraseña/i), 'Demo2026!');
    await user.click(screen.getByRole('button', { name: 'Ingresar' }));

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Bienvenido/a, Socia Demo' }),
    ).toBeVisible();
    expect(router.state.location.pathname).toBe('/');
    expect(screen.getByText('Socio')).toBeVisible();
    expect(sessionStorage.getItem(TOKEN_KEY)).toBe('jwt-de-prueba');
    const loginCall = calls.find((c) => c.url.endsWith('/api/auth/login'));
    expect(loginCall?.method).toBe('POST');
  });
});

describe('rutas protegidas', () => {
  it('sin sesión, / redirige a /login', async () => {
    mockFetch({});
    const { router } = renderApp('/');

    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeVisible();
    expect(router.state.location.pathname).toBe('/login');
  });

  it('con sesión carga /api/me, muestra el rol y Cerrar sesión vuelve al login', async () => {
    sessionStorage.setItem(TOKEN_KEY, 'jwt-de-prueba');
    mockFetch({
      'GET /api/me': () => jsonResponse({ ...socia, fullName: 'Abogado Uno', role: 'LAWYER' }),
    });
    const user = userEvent.setup();
    const { router } = renderApp('/');

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Bienvenido/a, Abogado Uno' }),
    ).toBeVisible();
    expect(screen.getByText('Abogado')).toBeVisible();

    await user.click(screen.getByRole('button', { name: 'Cerrar sesión' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'));
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull();
  });

  it('un token vencido (401 en /api/me) devuelve al login', async () => {
    sessionStorage.setItem(TOKEN_KEY, 'jwt-vencido');
    mockFetch({
      'GET /api/me': () =>
        problemResponse({
          type: 'https://asuntos-horas.example/problems/unauthenticated',
          title: 'No autenticado',
          status: 401,
          detail: 'Debes iniciar sesión para acceder a este recurso.',
          code: 'unauthenticated',
          traceId: 'abc',
        }),
    });
    const { router } = renderApp('/');

    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeVisible();
    expect(router.state.location.pathname).toBe('/login');
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull();
  });
});
