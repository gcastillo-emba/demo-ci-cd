import { describe, expect, it } from 'vitest';
import contractYaml from '../../../api/openapi.yaml?raw';
import { GENERIC_ERROR_DETAIL, NETWORK_ERROR_DETAIL, toApiProblem } from './problem';
import {
  dailyLimitMessage,
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

// Códigos de error declarados en el contrato (`code: <slug>` en los ejemplos de Problem Details).
const CONTRACT_CODES = [
  ...new Set([...contractYaml.matchAll(/^\s*code: ([a-z][a-z-]*)\s*$/gm)].map((m) => m[1]!)),
];
// `invalid-credentials` lo resuelve la pantalla de login con el detail del servidor.
// `assistant-note-*` son códigos de campo (`errors[].code`, C4): los traduce
// `assistantErrorMessage` (ver assistant.test.ts).
const SCREEN_HANDLED_CODES = new Set([
  'invalid-credentials',
  'assistant-note-empty',
  'assistant-note-too-long',
]);

const C2_CODES = [
  'hours-invalid-increment',
  'hours-out-of-range',
  'daily-hours-exceeded',
  'future-date',
  'matter-closed',
  'edit-window-expired',
  'entry-not-pending',
  'concurrent-modification',
  'not-entry-owner',
  'rejection-reason-required',
];

describe('mensajes de C2 (tarea 3.1, design Risks)', () => {
  it('cada slug de C2 del contrato tiene un mensaje en español sin el slug', () => {
    for (const code of C2_CODES) {
      expect(CONTRACT_CODES).toContain(code);
      const message = PROBLEM_MESSAGES[code];
      expect(message, code).toBeTruthy();
      expect(message).not.toContain(code);
      expect(userMessage(problem(422, code, 'detail en inglés'))).toBe(message);
    }
  });

  it('todo code del contrato tiene mensaje (salvo los que resuelve su pantalla)', () => {
    expect(CONTRACT_CODES.length).toBeGreaterThan(C2_CODES.length);
    const missing = CONTRACT_CODES.filter(
      (code) => !SCREEN_HANDLED_CODES.has(code) && !PROBLEM_MESSAGES[code],
    );
    expect(missing).toEqual([]);
  });

  // Scenario: Error de límite diario visible
  it('daily-hours-exceeded informa límite, horas ya cargadas y las que quedan', () => {
    const p = problem(422, 'daily-hours-exceeded', 'x', { limitHours: 24, alreadyLoggedHours: 20 });
    const expected =
      'Se supera el límite de 24 horas diarias: ya tienes 20 h registradas ese día y te quedan 4 h.';
    expect(dailyLimitMessage(p)).toBe(expected);
    expect(userMessage(p)).toBe(expected);
    expect(
      dailyLimitMessage(
        problem(422, 'daily-hours-exceeded', 'x', { limitHours: 24, alreadyLoggedHours: 22.5 }),
      ),
    ).toContain('ya tienes 22,5 h registradas ese día y te quedan 1,5 h');
  });

  it('daily-hours-exceeded sin extensiones cae en el mensaje del código', () => {
    expect(userMessage(problem(422, 'daily-hours-exceeded', 'x'))).toBe(
      PROBLEM_MESSAGES['daily-hours-exceeded'],
    );
  });
});
