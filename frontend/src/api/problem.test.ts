import { describe, expect, it } from 'vitest';
import {
  ApiProblem,
  errorMessage,
  GENERIC_ERROR_DETAIL,
  NETWORK_ERROR_DETAIL,
  problemFromResponse,
  toApiProblem,
  TOO_MANY_REQUESTS_DETAIL,
} from './problem';

const invalidCredentials = {
  type: 'https://asuntos-horas.example/problems/invalid-credentials',
  title: 'Credenciales inválidas',
  status: 401,
  detail: 'El email o la contraseña no son correctos.',
  code: 'invalid-credentials',
  traceId: '4bf92f3577b34da6a3ce929d0e0e4736',
};

// Cuerpo exacto que devuelve frontend/nginx.conf (location @too_many_requests).
const tooManyRequests = {
  type: 'https://asuntos-horas.example/problems/too-many-requests',
  title: 'Demasiados intentos',
  status: 429,
  detail: 'Demasiados intentos de inicio de sesión. Espera un minuto e inténtalo de nuevo.',
  code: 'too-many-requests',
  traceId: '',
};

describe('ApiProblem', () => {
  it('parsea una respuesta application/problem+json completa', async () => {
    const response = new Response(JSON.stringify(invalidCredentials), {
      status: 401,
      headers: { 'Content-Type': 'application/problem+json' },
    });

    const problem = await problemFromResponse(response);

    expect(problem).toBeInstanceOf(ApiProblem);
    expect(problem).toBeInstanceOf(Error);
    expect(problem.status).toBe(401);
    expect(problem.code).toBe('invalid-credentials');
    expect(problem.title).toBe('Credenciales inválidas');
    expect(problem.detail).toBe('El email o la contraseña no son correctos.');
    expect(problem.message).toBe(problem.detail);
    expect(problem.traceId).toBe('4bf92f3577b34da6a3ce929d0e0e4736');
    expect(problem.errors).toEqual([]);
  });

  it('conserva los errores por campo y descarta los mal formados', () => {
    const problem = toApiProblem(400, {
      status: 400,
      code: 'validation-error',
      title: 'Solicitud inválida',
      detail: 'La solicitud tiene campos inválidos.',
      errors: [{ field: 'email', message: 'Debe ser un email válido.' }, { field: 'x' }, 'basura'],
    });

    expect(problem.errors).toEqual([{ field: 'email', message: 'Debe ser un email válido.' }]);
    expect(problem.fieldError('email')).toBe('Debe ser un email válido.');
    expect(problem.fieldError('password')).toBeUndefined();
  });

  it('usa textos de respaldo en español si la respuesta no es problem+json', async () => {
    const response = new Response('<html>Bad Gateway</html>', {
      status: 502,
      headers: { 'Content-Type': 'text/html' },
    });

    const problem = await problemFromResponse(response);

    expect(problem.status).toBe(502);
    expect(problem.code).toBe('internal-error');
    expect(problem.detail).toBe(GENERIC_ERROR_DETAIL);
  });

  it('tolera un cuerpo problem+json inválido', async () => {
    const response = new Response('{no-json', {
      status: 400,
      headers: { 'Content-Type': 'application/problem+json; charset=utf-8' },
    });

    const problem = await problemFromResponse(response);

    expect(problem.status).toBe(400);
    expect(problem.code).toBe('unknown-error');
    expect(problem.detail).toBe(GENERIC_ERROR_DETAIL);
  });

  it('parsea el 429 problem+json del rate limiting de nginx (traceId vacío)', async () => {
    const response = new Response(JSON.stringify(tooManyRequests), {
      status: 429,
      headers: { 'Content-Type': 'application/problem+json; charset=utf-8', 'Retry-After': '60' },
    });

    const problem = await problemFromResponse(response);

    expect(problem.status).toBe(429);
    expect(problem.code).toBe('too-many-requests');
    expect(problem.detail).toBe(TOO_MANY_REQUESTS_DETAIL);
    expect(problem.traceId).toBeUndefined();
    expect(errorMessage(problem)).toBe(TOO_MANY_REQUESTS_DETAIL);
  });

  it('un 429 sin cuerpo problem+json muestra igualmente el mensaje en español', async () => {
    const response = new Response('<html>429 Too Many Requests</html>', {
      status: 429,
      headers: { 'Content-Type': 'text/html' },
    });

    const problem = await problemFromResponse(response);

    expect(problem.code).toBe('too-many-requests');
    expect(problem.title).toBe('Demasiados intentos');
    expect(problem.detail).toBe(TOO_MANY_REQUESTS_DETAIL);
  });

  it('errorMessage traduce cualquier error a un mensaje en español', () => {
    expect(errorMessage(toApiProblem(401, invalidCredentials))).toBe(invalidCredentials.detail);
    expect(errorMessage(new TypeError('Failed to fetch'))).toBe(NETWORK_ERROR_DETAIL);
    expect(errorMessage('otra cosa')).toBe(GENERIC_ERROR_DETAIL);
  });
});
