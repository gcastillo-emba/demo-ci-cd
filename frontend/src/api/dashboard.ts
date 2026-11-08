import { useQuery } from '@tanstack/react-query';
import { api } from './client';
import type { components } from './generated/schema';

export type DashboardSummary = components['schemas']['DashboardSummary'];
export type HoursTotals = components['schemas']['HoursTotals'];
export type MatterHoursRow = components['schemas']['MatterHoursRow'];
export type LawyerHoursRow = components['schemas']['LawyerHoursRow'];

export const dashboardKeys = {
  summary: ['dashboard', 'summary'] as const,
};

/**
 * Resumen del tablero SIN `from`/`to`: la ventana fija de 30 días la decide el servidor en
 * `America/La_Paz` y la pantalla la muestra con los `from`/`to` de la respuesta (design D5).
 */
export async function fetchDashboardSummary(): Promise<DashboardSummary> {
  const { data } = await api.GET('/api/dashboard/summary');
  if (data === undefined) throw new Error('Respuesta vacía de getDashboardSummary');
  return data;
}

export function useDashboardSummary() {
  return useQuery({ queryKey: dashboardKeys.summary, queryFn: fetchDashboardSummary });
}

const HOURS_FORMAT = new Intl.NumberFormat('es-BO', {
  minimumFractionDigits: 0,
  maximumFractionDigits: 1,
});

/** Horas con coma decimal y sin ",0" en enteros: 3.5 → "3,5 h"; 4 → "4 h"; 0 → "0 h" (Supuesto 10). */
export function formatDashboardHours(hours: number): string {
  return `${HOURS_FORMAT.format(hours)} h`;
}

/** `true` si el período no tiene horas contadas (estado vacío del Tablero). */
export function isEmptySummary(summary: DashboardSummary): boolean {
  return summary.totals.approvedHours === 0 && summary.totals.pendingHours === 0;
}

/** Ancho porcentual de un segmento de barra respecto del máximo de la tabla (0 si el máximo es 0). */
export function barPercent(hours: number, max: number): number {
  if (max <= 0 || hours <= 0) return 0;
  return Math.min(100, (hours / max) * 100);
}
