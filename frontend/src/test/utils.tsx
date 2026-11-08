import { render } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { vi } from 'vitest';
import { AppProviders, createQueryClient } from '../App';
import { routes } from '../routes/routes';

/** Renderiza la app completa en memoria, empezando en `path`. */
export function renderApp(path = '/') {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  const client = createQueryClient();
  const utils = render(
    <AppProviders client={client}>
      <RouterProvider router={router} />
    </AppProviders>,
  );
  return { ...utils, router, client };
}

export function jsonResponse(body: unknown, status = 200, contentType = 'application/json') {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': contentType },
  });
}

export function problemResponse(body: Record<string, unknown>) {
  const status = typeof body.status === 'number' ? body.status : 500;
  return jsonResponse(body, status, 'application/problem+json');
}

type Handler = (request: Request) => Response | Promise<Response>;

/** Reemplaza fetch global con un enrutador mínimo por "MÉTODO /ruta". */
export function mockFetch(handlers: Record<string, Handler>) {
  const calls: Request[] = [];
  const fn = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const request = input instanceof Request ? input : new Request(input, init);
    calls.push(request);
    const key = `${request.method} ${new URL(request.url).pathname}`;
    const handler = handlers[key];
    if (!handler) throw new Error(`fetch no simulado: ${key}`);
    return handler(request);
  });
  vi.stubGlobal('fetch', fn);
  return { fn, calls };
}
