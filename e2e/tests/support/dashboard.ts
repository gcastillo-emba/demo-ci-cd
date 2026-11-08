import { ABOGADO } from './session';
import { addDays, todayInLaPaz } from './time-entries';

// Utilidades E2E de C3 (add-hours-dashboard). Datos 100 % sintéticos.

export const MATTER_TABLE = 'Horas por asunto';
export const LAWYER_TABLE = 'Horas por abogado';

/** Abogados sintéticos del seed de demo distintos de `abogado1` (R__seed_demo_users). */
export const OTHER_LAWYERS = ['Abogada Dos', 'Abogado Tres'] as const;

/** "Últimos 30 días: dd/mm/aaaa – dd/mm/aaaa" (spec "Pantalla Tablero"). */
export const PERIOD_PATTERN = /^Últimos 30 días: \d{2}\/\d{2}\/\d{4} – \d{2}\/\d{2}\/\d{4}$/;

/** Id sintético de `abogado1` en el seed de demo. */
const ABOGADO_ID = '00000000-0000-4000-8000-000000000002';

/**
 * Resumen vacío de un abogado (Supuesto 1: su única fila con ceros) para la presentación del estado
 * vacío; el seed de demo siempre tiene horas en la ventana, así que se simula solo esta respuesta.
 */
export function emptyLawyerSummary() {
  const to = todayInLaPaz();
  return {
    from: addDays(to, -29),
    to,
    totals: { approvedHours: 0, pendingHours: 0 },
    byMatter: [],
    byLawyer: [
      { lawyerId: ABOGADO_ID, lawyerName: ABOGADO.fullName, approvedHours: 0, pendingHours: 0 },
    ],
  };
}
