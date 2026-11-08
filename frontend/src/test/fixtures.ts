// Datos 100 % sintéticos para los tests de Clientes y Asuntos.
import type { Client, Matter } from '../api/clientsMatters';
import { TOKEN_KEY } from '../api/token';
import { jsonResponse } from './utils';

export const socia = {
  id: '00000000-0000-4000-8000-000000000001',
  email: 'socia@asuntos.test',
  fullName: 'Socia Demo',
  role: 'PARTNER',
} as const;

export const abogado = {
  id: '00000000-0000-4000-8000-000000000002',
  email: 'abogado1@asuntos.test',
  fullName: 'Abogado Uno',
  role: 'LAWYER',
} as const;

export function signIn(user: typeof socia | typeof abogado) {
  sessionStorage.setItem(TOKEN_KEY, 'jwt-de-prueba');
  return () => jsonResponse(user);
}

let seq = 0;
function uuid(prefix: string): string {
  seq += 1;
  return `${prefix}-0000-4000-8000-${String(seq).padStart(12, '0')}`;
}

export function makeClient(name: string, taxId: string | null = null): Client {
  return { id: uuid('c0000000'), name, taxId, createdAt: '2026-11-07T14:00:00Z' };
}

export function makeMatter(
  code: string,
  title: string,
  client: Client,
  status: Matter['status'] = 'OPEN',
): Matter {
  return {
    id: uuid('a0000000'),
    code,
    clientId: client.id,
    clientName: client.name,
    title,
    description: null,
    status,
    createdAt: '2026-11-07T14:00:00Z',
    closedAt: status === 'CLOSED' ? '2026-11-07T15:00:00Z' : null,
  };
}

/** Pagina `items` como la API: `page` base 0, `size` por defecto 20. */
export function pageOf<T>(items: T[], url: URL) {
  const page = Number(url.searchParams.get('page') ?? '0');
  const size = Number(url.searchParams.get('size') ?? '20');
  return {
    items: items.slice(page * size, page * size + size),
    page,
    size,
    totalItems: items.length,
    totalPages: Math.ceil(items.length / size),
  };
}

export function includesIgnoringCase(text: string, q: string | null): boolean {
  return q === null || text.toLowerCase().includes(q.toLowerCase());
}

export const internalErrorProblem = {
  type: 'https://asuntos-horas.example/problems/internal-error',
  title: 'Error interno',
  status: 500,
  detail: 'org.postgresql.util.PSQLException: ERROR: relation "clients" does not exist',
  code: 'internal-error',
  traceId: '4bf92f3577b34da6a3ce929d0e0e4736',
};
