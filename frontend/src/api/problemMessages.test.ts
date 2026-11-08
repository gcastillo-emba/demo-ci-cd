import { describe, expect, it } from 'vitest';
import { GENERIC_ERROR_DETAIL, NETWORK_ERROR_DETAIL, toApiProblem } from './problem';
import {
  fieldMessage,
  INVALID_FIELD_MESSAGE,
  looksInternal,
  PROBLEM_MESSAGES,
  userMessage,
} from './problemMessages';

const problem = (status: number, code: string, detail: string, extra: object = {}) =>
  toApiProblem(status, { status, code, title: 'Título', detail, traceId: 'abc', ...extra });

describe('userMessage', () => {
  it('traduce cada code de C1 a un mensaje en español', () => {
    expect(userMessage(problem(409, 'client-tax-id-taken', 'x'))).toBe(
      'Ya existe un cliente con esa identificación tributaria.',
    );
    expect(userMessage(problem(409, 'matter-already-closed', 'x'))).toBe(
      'El asunto ya estaba cerrado.',
    );
    expect(userMessage(problem(403, 'forbidden', 'x'))).toBe(PROBLEM_MESSAGES.forbidden);
    expect(userMessage(problem(404, 'not-found', 'x'))).toBe(PROBLEM_MESSAGES['not-found']);
    expect(userMessage(problem(405, 'method-not-allowed', 'x'))).toBe(
      PROBLEM_MESSAGES['method-not-allowed'],
    );
    expect(userMessage(problem(500, 'internal-error', 'x'))).toBe(GENERIC_ERROR_DETAIL);
  });

  it('permite textos propios por pantalla', () => {
    expect(
      userMessage(problem(404, 'not-found', 'x'), { 'not-found': 'El asunto no existe.' }),
    ).toBe('El asunto no existe.');
  });

  it('usa el detail para codes no mapeados, salvo 5xx o detalles internos', () => {
    expect(userMessage(problem(422, 'otra-regla', 'Regla de negocio en español.'))).toBe(
      'Regla de negocio en español.',
    );
    expect(userMessage(problem(503, 'service-unavailable', 'Servicio no disponible.'))).toBe(
      GENERIC_ERROR_DETAIL,
    );
    expect(
      userMessage(
        problem(400, 'otra-regla', 'could not execute statement; SQL [insert into clients ...]'),
      ),
    ).toBe(GENERIC_ERROR_DETAIL);
  });

  it('errores que no son ApiProblem caen en los mensajes del skeleton', () => {
    expect(userMessage(new TypeError('Failed to fetch'))).toBe(NETWORK_ERROR_DETAIL);
    expect(userMessage(new Error('boom'))).toBe(GENERIC_ERROR_DETAIL);
  });
});

describe('looksInternal', () => {
  it.each([
    'java.lang.NullPointerException',
    'org.postgresql.util.PSQLException: ERROR',
    'at com.emba.asuntoshoras.Foo.bar(Foo.java:42)',
    'select * from clients where id = 1',
    'duplicate key value violates unique constraint "ux_clients_tax_id"',
    'Hibernate could not map',
  ])('detecta "%s"', (text) => {
    expect(looksInternal(text)).toBe(true);
  });

  it.each([
    'Ya existe un cliente con esa identificación tributaria.',
    'El nombre no puede superar 200 caracteres.',
    'Selecciona un estado de la lista.',
  ])('no marca textos de usuario: "%s"', (text) => {
    expect(looksInternal(text)).toBe(false);
  });
});

describe('fieldMessage', () => {
  it('devuelve el mensaje del campo y sanea los internos', () => {
    const p = problem(400, 'validation-error', 'x', {
      errors: [
        { field: 'name', message: 'El nombre es obligatorio.' },
        { field: 'taxId', message: 'jakarta.validation.ConstraintViolationException' },
      ],
    });
    expect(fieldMessage(p, 'name')).toBe('El nombre es obligatorio.');
    expect(fieldMessage(p, 'taxId')).toBe(INVALID_FIELD_MESSAGE);
    expect(fieldMessage(p, 'title')).toBeUndefined();
  });
});
