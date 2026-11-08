import { ApiProblem, errorMessage, GENERIC_ERROR_DETAIL } from './problem';

/**
 * Mensajes en español por `code` estable de Problem Details (design D9). El `detail` del servidor
 * solo se usa como respaldo para códigos no mapeados y nunca si parece contener detalles internos
 * (spec "Errores sin detalles internos": la interfaz tampoco debe mostrar trazas, clases ni SQL).
 */
export const PROBLEM_MESSAGES: Readonly<Record<string, string>> = {
  'validation-error': 'Hay datos inválidos. Revisa los campos marcados.',
  unauthenticated: 'Tu sesión expiró. Inicia sesión de nuevo.',
  forbidden: 'Tu rol no permite realizar esta acción.',
  'not-found': 'El recurso solicitado no existe.',
  'method-not-allowed': 'La operación solicitada no está permitida.',
  'client-tax-id-taken': 'Ya existe un cliente con esa identificación tributaria.',
  'matter-already-closed': 'El asunto ya estaba cerrado.',
  'internal-error': GENERIC_ERROR_DETAIL,
};

export const INVALID_FIELD_MESSAGE = 'El valor no es válido.';

const INTERNAL_PATTERNS: readonly RegExp[] = [
  /exception/i,
  /stack\s?trace/i,
  /\bat\s+[\w$.]+\([^)]*\)/, // línea de traza "at com.x.Y(Y.java:1)"
  /\b(?:java|javax|jakarta|org|com|sun)\.[a-z]\w*\./, // nombres de clase calificados
  /\b(?:select|insert|update|delete)\b[\s\S]*\b(?:from|into|set|where)\b/i, // SQL
  /\b(?:sql|jdbc|hibernate|postgres(?:ql)?|psql)\b/i,
  /\bconstraint\b/i,
];

/** `true` si el texto parece filtrar detalles internos (traza, clase, SQL, base de datos). */
export function looksInternal(text: string): boolean {
  return INTERNAL_PATTERNS.some((pattern) => pattern.test(text));
}

/**
 * Mensaje apto para el usuario. `overrides` permite un texto propio de la pantalla para un `code`
 * (p. ej. `not-found` → "El asunto no existe.").
 */
export function userMessage(
  error: unknown,
  overrides: Readonly<Record<string, string>> = {},
): string {
  if (!(error instanceof ApiProblem)) return errorMessage(error);
  const mapped = overrides[error.code] ?? PROBLEM_MESSAGES[error.code];
  if (mapped) return mapped;
  if (error.status >= 500 || looksInternal(error.detail)) return GENERIC_ERROR_DETAIL;
  return error.detail;
}

/** Mensaje del campo informado en `errors[]`, saneado con la misma regla. */
export function fieldMessage(problem: ApiProblem, field: string): string | undefined {
  const message = problem.fieldError(field);
  if (message === undefined) return undefined;
  return looksInternal(message) ? INVALID_FIELD_MESSAGE : message;
}
