import { describe, expect, it, vi } from 'vitest';
import { mockFetch, jsonResponse, problemResponse } from '../test/utils';
import { createApiClient } from './client';
import { ApiProblem } from './problem';
import { getToken, setToken, TOKEN_KEY, UNAUTHORIZED_EVENT } from './token';

const user = {
  id: '00000000-0000-4000-8000-000000000001',
  email: 'socia@asuntos.test',
  fullName: 'Socia Demo',
  role: 'PARTNER',
};

describe('cliente de la API', () => {
  it('agrega Authorization Bearer con el token de sessionStorage', async () => {
    sessionStorage.setItem(TOKEN_KEY, 'token-de-prueba');
    const { calls } = mockFetch({ 'GET /api/me': () => jsonResponse(user) });

    const { data } = await createApiClient().GET('/api/me');

    expect(data).toEqual(user);
    expect(calls[0]?.headers.get('Authorization')).toBe('Bearer token-de-prueba');
  });

  it('no envía Authorization sin sesión', async () => {
    const { calls } = mockFetch({
      'GET /api/features': () => jsonResponse({ descriptionAssistant: false }),
    });

    await createApiClient().GET('/api/features');

    expect(calls[0]?.headers.has('Authorization')).toBe(false);
  });

  it('lanza ApiProblem y cierra la sesión ante un 401 autenticado', async () => {
    setToken('token-vencido');
    const listener = vi.fn();
    window.addEventListener(UNAUTHORIZED_EVENT, listener);
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

    await expect(createApiClient().GET('/api/me')).rejects.toMatchObject({
      name: 'ApiProblem',
      status: 401,
      code: 'unauthenticated',
    });
    expect(getToken()).toBeNull();
    expect(listener).toHaveBeenCalledTimes(1);
    window.removeEventListener(UNAUTHORIZED_EVENT, listener);
  });

  it('un 401 del login no dispara el cierre de sesión', async () => {
    const listener = vi.fn();
    window.addEventListener(UNAUTHORIZED_EVENT, listener);
    mockFetch({
      'POST /api/auth/login': () =>
        problemResponse({
          type: 'https://asuntos-horas.example/problems/invalid-credentials',
          title: 'Credenciales inválidas',
          status: 401,
          detail: 'El email o la contraseña no son correctos.',
          code: 'invalid-credentials',
          traceId: 'abc',
        }),
    });

    const call = createApiClient().POST('/api/auth/login', {
      body: { email: 'socia@asuntos.test', password: 'incorrecta' },
    });

    await expect(call).rejects.toBeInstanceOf(ApiProblem);
    expect(listener).not.toHaveBeenCalled();
    window.removeEventListener(UNAUTHORIZED_EVENT, listener);
  });
});
