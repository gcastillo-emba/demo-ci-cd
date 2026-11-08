// Reglas de presentación y validación previa de horas y fechas de C2 (design D8). El servidor sigue
// siendo la autoridad: estas funciones solo evitan viajes inútiles y dan mensajes junto al campo.

/** Límite de horas por abogado y día (spec "Límite diario de 24 horas por abogado"). */
export const MAX_DAILY_HOURS = 24;
/** Longitud máxima de la descripción tras recortar (spec "Validación de formato del registro"). */
export const DESCRIPTION_MAX = 2000;
/** Longitud máxima del motivo de rechazo tras recortar (spec "Rechazo ... con motivo obligatorio"). */
export const REJECTION_REASON_MAX = 500;
/** Zona horaria de la firma para "hoy" (design D2). */
export const FIRM_TIME_ZONE = 'America/La_Paz';

export const HOURS_FORMAT_MESSAGE = 'Ingresa las horas como un número, por ejemplo 1,5.';
export const HOURS_INCREMENT_MESSAGE =
  'Las horas deben ir en incrementos de 0,1 (por ejemplo, 1,5).';
export const HOURS_RANGE_MESSAGE = `Las horas deben ser mayores que 0 y no superar ${MAX_DAILY_HOURS}.`;

export type HoursParseResult = { ok: true; value: number } | { ok: false; message: string };

const HOURS_PATTERN = /^(\d+)(?:[.,](\d+))?$/;

/**
 * Convierte el texto del campo horas en número aceptando coma o punto decimal ("1,5" y "1.5" → 1.5).
 * Rechaza más de un decimal significativo ("1,25") y valores fuera de 0 < h ≤ 24, en ese orden
 * (el mismo que el servidor: incremento y después rango, design D1).
 */
export function parseHours(text: string): HoursParseResult {
  const match = HOURS_PATTERN.exec(text.trim());
  if (!match) return { ok: false, message: HOURS_FORMAT_MESSAGE };
  const integerPart = match[1] ?? '0';
  const fraction = (match[2] ?? '').replace(/0+$/, '');
  if (fraction.length > 1) return { ok: false, message: HOURS_INCREMENT_MESSAGE };
  const tenths = Number(integerPart) * 10 + Number(fraction || '0');
  if (tenths < 1 || tenths > MAX_DAILY_HOURS * 10) {
    return { ok: false, message: HOURS_RANGE_MESSAGE };
  }
  return { ok: true, value: tenths / 10 };
}

/** Horas con coma decimal y a lo sumo un decimal: 1.5 → "1,5"; 8 → "8". */
export function formatHours(hours: number): string {
  const rounded = Math.round(hours * 10) / 10;
  return Number.isInteger(rounded) ? String(rounded) : rounded.toFixed(1).replace('.', ',');
}

const ISO_DATE_FORMAT = new Intl.DateTimeFormat('en-CA', {
  timeZone: FIRM_TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

/** Fecha de hoy en `America/La_Paz` como `YYYY-MM-DD` (spec "Fecha de trabajo no futura"). */
export function todayInLaPaz(now: Date = new Date()): string {
  return ISO_DATE_FORMAT.format(now);
}

/** Suma `days` días a una fecha `YYYY-MM-DD` (aritmética de calendario, sin zona horaria). */
export function addDays(isoDate: string, days: number): string {
  const date = new Date(`${isoDate}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

const ISO_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

/** `true` si el texto es una fecha de calendario válida `YYYY-MM-DD`. */
export function isIsoDate(value: string): boolean {
  if (!ISO_DATE_PATTERN.test(value)) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value;
}

const DATE_FORMAT = new Intl.DateTimeFormat('es-BO', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  timeZone: 'UTC',
});

/** Fecha de calendario `YYYY-MM-DD` para mostrar (dd/mm/aaaa). */
export function formatDate(isoDate: string): string {
  return isIsoDate(isoDate) ? DATE_FORMAT.format(new Date(`${isoDate}T00:00:00Z`)) : isoDate;
}
