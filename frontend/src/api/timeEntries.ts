import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './client';
import { clientsMattersKeys, fetchMatters, type MatterListParams } from './clientsMatters';
import type { components } from './generated/schema';
import { ApiProblem } from './problem';
import { usePagedQuery } from './usePagedQuery';

export type TimeEntry = components['schemas']['TimeEntry'];
export type TimeEntryCreate = components['schemas']['TimeEntryCreate'];
export type TimeEntryUpdate = components['schemas']['TimeEntryUpdate'];
export type TimeEntryStatus = components['schemas']['TimeEntryStatus'];
export type TimeEntrySort = components['schemas']['TimeEntrySort'];
// El generado compone `Page` (items: unknown[]) con items tipados; se normaliza a `items: T[]`.
export type TimeEntryPage = Omit<components['schemas']['TimeEntryPage'], 'items'> & {
  items: TimeEntry[];
};

export interface TimeEntryListParams {
  matterId?: string;
  status?: TimeEntryStatus;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
  sort?: TimeEntrySort;
}

/** Máximo de asuntos abiertos en el selector de "Registrar horas" (`size` máximo de la API). */
export const MATTER_OPTIONS_SIZE = 100;

/** 409 que obligan a recargar los datos vigentes (spec "Conflicto de concurrencia al editar"). */
export const STALE_ENTRY_CODES: ReadonlySet<string> = new Set([
  'entry-not-pending',
  'concurrent-modification',
]);

export function isStaleEntryError(error: unknown): error is ApiProblem {
  return error instanceof ApiProblem && STALE_ENTRY_CODES.has(error.code);
}

export const timeEntriesKeys = {
  all: ['time-entries'] as const,
  list: (params: TimeEntryListParams) => ['time-entries', params] as const,
};

function compact<T extends object>(params: T): T {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== undefined && value !== ''),
  ) as T;
}

function required<T>(data: T | undefined, operation: string): T {
  if (data === undefined) throw new Error(`Respuesta vacía de ${operation}`);
  return data;
}

export async function fetchTimeEntries(params: TimeEntryListParams): Promise<TimeEntryPage> {
  const { data } = await api.GET('/api/time-entries', { params: { query: compact(params) } });
  return required(data, 'listTimeEntries');
}

export function useTimeEntries(params: TimeEntryListParams, options: { enabled?: boolean } = {}) {
  return usePagedQuery(timeEntriesKeys.list(params), () => fetchTimeEntries(params), options);
}

/** Asuntos `OPEN` para el selector de "Registrar horas" (design Supuesto 12). */
export function useOpenMatterOptions() {
  const params: MatterListParams = { status: 'OPEN', page: 0, size: MATTER_OPTIONS_SIZE };
  return useQuery({
    queryKey: clientsMattersKeys.matterList(params),
    queryFn: () => fetchMatters(params),
    staleTime: 60 * 1000,
  });
}

export function useCreateTimeEntry() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: TimeEntryCreate) => {
      const { data } = await api.POST('/api/time-entries', { body });
      return required(data, 'createTimeEntry');
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: timeEntriesKeys.all }),
  });
}

/** Invalida los listados cuando el registro cambió o desapareció en el servidor (409 o 404). */
function invalidateOnStale(queryClient: ReturnType<typeof useQueryClient>, error: unknown) {
  const gone = error instanceof ApiProblem && error.code === 'not-found';
  return isStaleEntryError(error) || gone
    ? queryClient.invalidateQueries({ queryKey: timeEntriesKeys.all })
    : undefined;
}

export function useUpdateTimeEntry() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, body }: { id: string; body: TimeEntryUpdate }) => {
      const { data } = await api.PUT('/api/time-entries/{id}', {
        params: { path: { id } },
        body,
      });
      return required(data, 'updateTimeEntry');
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: timeEntriesKeys.all }),
    onError: (error) => invalidateOnStale(queryClient, error),
  });
}

export function useApproveTimeEntry() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: string) => {
      await api.POST('/api/time-entries/{id}/approve', { params: { path: { id } } });
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: timeEntriesKeys.all }),
    onError: (error) => invalidateOnStale(queryClient, error),
  });
}

export function useRejectTimeEntry() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, reason }: { id: string; reason: string }) => {
      await api.POST('/api/time-entries/{id}/reject', {
        params: { path: { id } },
        body: { reason },
      });
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: timeEntriesKeys.all }),
    onError: (error) => invalidateOnStale(queryClient, error),
  });
}

const STATUS_LABELS: Record<TimeEntryStatus, string> = {
  PENDING: 'Pendiente',
  APPROVED: 'Aprobada',
  REJECTED: 'Rechazada',
};

/** Estado en español según `docs/glosario.md`. */
export function timeEntryStatusLabel(status: TimeEntryStatus): string {
  return STATUS_LABELS[status];
}

export function isTimeEntryStatus(value: string): value is TimeEntryStatus {
  return value === 'PENDING' || value === 'APPROVED' || value === 'REJECTED';
}

/**
 * "Editar" solo en registros propios `PENDING` cuyo `editableUntil` (del servidor) no sea anterior
 * a hoy en La Paz (spec "Pantalla Mis registros"). Las fechas `YYYY-MM-DD` se comparan como texto.
 */
export function isEditable(entry: TimeEntry, userId: string, today: string): boolean {
  return entry.status === 'PENDING' && entry.lawyerId === userId && entry.editableUntil >= today;
}
