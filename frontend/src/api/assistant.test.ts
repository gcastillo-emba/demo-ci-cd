import { describe, expect, it } from 'vitest';
import {
  assistantErrorMessage,
  MATTER_INVALID_MESSAGE,
  NOTE_EMPTY_MESSAGE,
  NOTE_TOO_LONG_MESSAGE,
  noteLength,
  validateNote,
} from './assistant';
import { ApiProblem, toApiProblem } from './problem';

function validationProblem(errors: unknown[]): ApiProblem {
  return toApiProblem(400, {
    status: 400,
    code: 'validation-error',
    title: 'Datos inválidos',
    detail: 'Hay datos inválidos.',
    errors,
  });
}

describe('nota del asistente', () => {
  it('mide en caracteres Unicode tras el recorte', () => {
    expect(noteLength('  hola  ')).toBe(4);
    expect(noteLength('😀'.repeat(500))).toBe(500);
  });

  it('valida vacía, solo espacios, 500 y 501 caracteres', () => {
    expect(validateNote('')).toBe(NOTE_EMPTY_MESSAGE);
    expect(validateNote('   \n ')).toBe(NOTE_EMPTY_MESSAGE);
    expect(validateNote(` ${'a'.repeat(500)} `)).toBeUndefined();
    expect(validateNote('a'.repeat(501))).toBe(NOTE_TOO_LONG_MESSAGE);
  });
});

describe('errores del asistente', () => {
  it('conserva errors[].code en kebab-case y descarta códigos no estables', () => {
    const problem = validationProblem([
      { field: 'note', code: 'assistant-note-empty', message: 'Escribe una nota.' },
      { field: 'matterId', code: 'Not Kebab', message: 'Formato inválido.' },
    ]);
    expect(problem.fieldErrorCode('note')).toBe('assistant-note-empty');
    expect(problem.fieldErrorCode('matterId')).toBeUndefined();
    expect(problem.errors[1]).toEqual({ field: 'matterId', message: 'Formato inválido.' });
  });

  it('traduce los códigos de campo de la nota', () => {
    expect(
      assistantErrorMessage(
        validationProblem([{ field: 'note', code: 'assistant-note-too-long', message: 'x' }]),
      ),
    ).toBe(NOTE_TOO_LONG_MESSAGE);
    expect(
      assistantErrorMessage(
        validationProblem([{ field: 'note', code: 'assistant-note-empty', message: 'x' }]),
      ),
    ).toBe(NOTE_EMPTY_MESSAGE);
  });

  it('un matterId inválido (sin código) da un mensaje propio', () => {
    expect(
      assistantErrorMessage(validationProblem([{ field: 'matterId', message: 'Invalid UUID' }])),
    ).toBe(MATTER_INVALID_MESSAGE);
  });

  it('otro 400 sin código conocido usa el mensaje genérico de validación', () => {
    expect(assistantErrorMessage(validationProblem([]))).toBe(
      'Hay datos inválidos. Revisa los campos marcados.',
    );
  });

  it('403 y 503 usan los mensajes por código', () => {
    const forbidden = toApiProblem(403, { code: 'forbidden', detail: 'Access denied' });
    const unavailable = toApiProblem(503, { code: 'assistant-unavailable', detail: 'x' });
    expect(assistantErrorMessage(forbidden)).toBe('Tu rol no permite realizar esta acción.');
    expect(assistantErrorMessage(unavailable)).toMatch(/^El asistente no está disponible/);
  });

  it('un error de red da el mensaje de conexión', () => {
    expect(assistantErrorMessage(new TypeError('Failed to fetch'))).toMatch(
      /^No se pudo conectar con el servidor/,
    );
  });
});
