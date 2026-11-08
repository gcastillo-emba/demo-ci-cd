import type { components } from './generated/schema';

export type Problem = components['schemas']['Problem'];
export type FieldError = components['schemas']['FieldError'];

export const GENERIC_ERROR_DETAIL =
  'Ocurrió un error inesperado. Inténtalo de nuevo en unos minutos.';
export const NETWORK_ERROR_DETAIL =
  'No se pudo conectar con el servidor. Revisa tu conexión e inténtalo de nuevo.';
/** Respaldo del 429 de nginx (rate limiting del login) si el cuerpo no llega como problem+json. */
export const TOO_MANY_REQUESTS_DETAIL =
  'Demasiados intentos de inicio de sesión. Espera un minuto e inténtalo de nuevo.';

/** Error de la API construido a partir de una respuesta `application/problem+json` (RFC 9457). */
export class ApiProblem extends Error {
  readonly status: number;
  readonly code: string;
  readonly title: string;
  readonly detail: string;
  readonly errors: FieldError[];
  readonly traceId?: string;
  /**
   * Miembros de extensión numéricos (RFC 9457 §3.2), p. ej. `limitHours` y `alreadyLoggedHours` de
   * `daily-hours-exceeded`. Solo se conservan números finitos: nunca texto libre del servidor.
   */
  readonly extensions: Readonly<Record<string, number>>;

  constructor(init: {
    status: number;
    code: string;
    title: string;
    detail: string;
    errors?: FieldError[];
    traceId?: string;
    extensions?: Record<string, number>;
  }) {
    super(init.detail);
    this.name = 'ApiProblem';
    this.status = init.status;
    this.code = init.code;
    this.title = init.title;
    this.detail = init.detail;
    this.errors = init.errors ?? [];
    this.traceId = init.traceId;
    this.extensions = { ...init.extensions };
  }

  /** Valor numérico de un miembro de extensión, si el servidor lo informó. */
  numberExtension(name: string): number | undefined {
    return Object.hasOwn(this.extensions, name) ? this.extensions[name] : undefined;
  }

  /** Mensaje del campo indicado, si el servidor lo informó en `errors[]`. */
  fieldError(field: string): string | undefined {
    return this.errors.find((e) => e.field === field)?.message;
  }

  /** Código estable del error del campo (`errors[].code`), si la operación lo define (C4). */
  fieldErrorCode(field: string): string | undefined {
    return this.errors.find((e) => e.field === field && e.code !== undefined)?.code;
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function asString(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() !== '' ? value : undefined;
}

/** `errors[].code` es un código estable en kebab-case (C4); cualquier otra cosa se descarta. */
const FIELD_CODE_PATTERN = /^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$/;

function parseFieldErrors(value: unknown): FieldError[] {
  if (!Array.isArray(value)) return [];
  return value.flatMap((item) => {
    if (!isRecord(item)) return [];
    const field = asString(item.field);
    const message = asString(item.message);
    if (!field || !message) return [];
    const code = asString(item.code);
    return code && FIELD_CODE_PATTERN.test(code)
      ? [{ field, code, message }]
      : [{ field, message }];
  });
}

const STANDARD_MEMBERS = new Set([
  'type',
  'title',
  'status',
  'detail',
  'instance',
  'code',
  'traceId',
  'errors',
]);

function parseNumericExtensions(body: Record<string, unknown>): Record<string, number> {
  const extensions: Record<string, number> = {};
  for (const [key, value] of Object.entries(body)) {
    if (!STANDARD_MEMBERS.has(key) && typeof value === 'number' && Number.isFinite(value)) {
      extensions[key] = value;
    }
  }
  return extensions;
}

/** Convierte un cuerpo arbitrario en `ApiProblem`, con textos de respaldo en español. */
export function toApiProblem(status: number, body: unknown): ApiProblem {
  const b = isRecord(body) ? body : {};
  const tooMany = status === 429;
  return new ApiProblem({
    status: typeof b.status === 'number' ? b.status : status,
    code:
      asString(b.code) ??
      (tooMany ? 'too-many-requests' : status >= 500 ? 'internal-error' : 'unknown-error'),
    title: asString(b.title) ?? (tooMany ? 'Demasiados intentos' : 'Error'),
    detail: asString(b.detail) ?? (tooMany ? TOO_MANY_REQUESTS_DETAIL : GENERIC_ERROR_DETAIL),
    errors: parseFieldErrors(b.errors),
    traceId: asString(b.traceId),
    extensions: parseNumericExtensions(b),
  });
}

export function isProblemResponse(response: Response): boolean {
  const type = response.headers.get('content-type') ?? '';
  return type.toLowerCase().includes('application/problem+json');
}

/** Lee una respuesta de error (problem+json o no) y la convierte en `ApiProblem`. */
export async function problemFromResponse(response: Response): Promise<ApiProblem> {
  let body: unknown = undefined;
  if (isProblemResponse(response)) {
    try {
      body = await response.clone().json();
    } catch {
      body = undefined;
    }
  }
  return toApiProblem(response.status, body);
}

/** Mensaje en español apto para mostrar al usuario, sea cual sea el error. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiProblem) return error.detail;
  if (error instanceof TypeError) return NETWORK_ERROR_DETAIL;
  return GENERIC_ERROR_DETAIL;
}
