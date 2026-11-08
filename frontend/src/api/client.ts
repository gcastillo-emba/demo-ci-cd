import createClient, { type Middleware } from 'openapi-fetch';
import type { paths } from './generated/schema';
import { problemFromResponse } from './problem';
import { clearToken, getToken, UNAUTHORIZED_EVENT } from './token';

const LOGIN_PATH = '/api/auth/login';

/** Agrega `Authorization: Bearer <token>` si hay sesión. */
export const authMiddleware: Middleware = {
  onRequest({ request }) {
    const token = getToken();
    if (token && !request.headers.has('Authorization')) {
      request.headers.set('Authorization', `Bearer ${token}`);
    }
    return request;
  },
};

/**
 * Convierte toda respuesta de error en `ApiProblem` (lanzada), de modo que los llamadores
 * reciben datos o una excepción tipada. Un 401 fuera del login cierra la sesión local.
 */
export const problemMiddleware: Middleware = {
  async onResponse({ request, response }) {
    if (response.ok) return response;
    const problem = await problemFromResponse(response);
    if (response.status === 401 && !new URL(request.url).pathname.endsWith(LOGIN_PATH)) {
      clearToken();
      window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
    }
    throw problem;
  },
};

export function createApiClient(options: { baseUrl?: string; fetch?: typeof fetch } = {}) {
  const client = createClient<paths>({
    baseUrl: options.baseUrl ?? window.location.origin,
    // Resolución tardía de fetch: permite reemplazarlo en tests (vi.stubGlobal).
    fetch: options.fetch ?? ((input: Request) => globalThis.fetch(input)),
  });
  client.use(authMiddleware, problemMiddleware);
  return client;
}

export const api = createApiClient();
