import {
  formatHours,
  HOURS_INCREMENT_MESSAGE,
  HOURS_RANGE_MESSAGE,
  MAX_DAILY_HOURS,
  REJECTION_REASON_MAX,
} from './hours';
import {
  ApiProblem,
  errorMessage,
  GENERIC_ERROR_DETAIL,
  TOO_MANY_REQUESTS_DETAIL,
} from './problem';

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
  // Rate limiting del login en nginx (429, ADR-006).
  'too-many-requests': TOO_MANY_REQUESTS_DETAIL,
  // C2 — registros de horas (design D6 y D8).
  'hours-invalid-increment': HOURS_INCREMENT_MESSAGE,
  'hours-out-of-range': HOURS_RANGE_MESSAGE,
  'daily-hours-exceeded': `Se supera el límite de ${MAX_DAILY_HOURS} horas diarias para ese día.`,
  'future-date': 'No se pueden registrar horas en fechas futuras.',
  'matter-closed': 'El asunto está cerrado: no admite registros de horas.',
  'edit-window-expired': 'Venció el plazo de 7 días para editar este registro.',
  'entry-not-pending': 'El registro ya no está pendiente: otro usuario lo resolvió.',
  'concurrent-modification': 'El registro cambió mientras lo editabas.',
  'not-entry-owner': 'Solo el autor del registro puede editarlo.',
  'rejection-reason-required': `Indica el motivo del rechazo (de 1 a ${REJECTION_REASON_MAX} caracteres).`,
  // C4 — asistente de descripción (design D8).
  'feature-disabled': 'El asistente de descripción no está habilitado.',
  'assistant-unavailable':
    'El asistente no está disponible en este momento; puedes escribir la descripción manualmente.',
  'internal-error': GENERIC_ERROR_DETAIL,
};

/**
 * Mensaje de `daily-hours-exceeded` con los miembros de extensión `limitHours` y
 * `alreadyLoggedHours` (spec "Error de límite diario visible": límite, horas ya cargadas y las que
 * quedan). Si faltan, cae en el mensaje genérico del código.
 */
export function dailyLimitMessage(problem: ApiProblem): string {
  const limit = problem.numberExtension('limitHours') ?? MAX_DAILY_HOURS;
  const already = problem.numberExtension('alreadyLoggedHours');
  if (already === undefined)
    return PROBLEM_MESSAGES['daily-hours-exceeded'] ?? GENERIC_ERROR_DETAIL;
  const remaining = Math.max(limit - already, 0);
  return (
    `Se supera el límite de ${formatHours(limit)} horas diarias: ya tienes ` +
    `${formatHours(already)} h registradas ese día y te quedan ${formatHours(remaining)} h.`
  );
}

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
  if (error.code === 'daily-hours-exceeded' && !overrides[error.code]) {
    return dailyLimitMessage(error);
  }
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
