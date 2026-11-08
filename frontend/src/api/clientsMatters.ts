import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './client';
import type { components } from './generated/schema';
import { ApiProblem } from './problem';
import { usePagedQuery } from './usePagedQuery';

export type Client = components['schemas']['Client'];
export type ClientCreate = components['schemas']['ClientCreate'];
export type Matter = components['schemas']['Matter'];
export type MatterCreate = components['schemas']['MatterCreate'];
// El generado compone `Page` (items: unknown[]) con items tipados; se normaliza a `items: T[]`.
type Paged<P, T> = Omit<P, 'items'> & { items: T[] };
export type ClientPage = Paged<components['schemas']['ClientPage'], Client>;
export type MatterPage = Paged<components['schemas']['MatterPage'], Matter>;
export type MatterStatus = components['schemas']['MatterStatus'];

/** Tamaño de página por defecto de la API (spec "Paginación de listados"). */
export const PAGE_SIZE = 20;
/** Máximo de clientes en los selectores (design D9, Supuesto S5): `size` máximo de la API. */
export const CLIENT_OPTIONS_SIZE = 100;

export interface ClientListParams {
  q?: string;
  page?: number;
  size?: number;
}

export interface MatterListParams {
  q?: string;
  clientId?: string;
  status?: MatterStatus;
  page?: number;
  size?: number;
}

export const clientsMattersKeys = {
  clients: ['clients'] as const,
  clientList: (params: ClientListParams) => ['clients', params] as const,
  matters: ['matters'] as const,
  matterList: (params: MatterListParams) => ['matters', params] as const,
  matter: (matterId: string) => ['matter', matterId] as const,
};

/** Quita los filtros vacíos para no enviarlos como `?q=`. */
function compact<T extends object>(params: T): T {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== undefined && value !== ''),
  ) as T;
}

function required<T>(data: T | undefined, operation: string): T {
  if (data === undefined) throw new Error(`Respuesta vacía de ${operation}`);
  return data;
}

export async function fetchClients(params: ClientListParams): Promise<ClientPage> {
  const { data } = await api.GET('/api/clients', { params: { query: compact(params) } });
  return required(data, 'listClients');
}

export async function fetchMatters(params: MatterListParams): Promise<MatterPage> {
  const { data } = await api.GET('/api/matters', { params: { query: compact(params) } });
  return required(data, 'listMatters');
}

export async function fetchMatter(matterId: string): Promise<Matter> {
  const { data } = await api.GET('/api/matters/{matterId}', {
    params: { path: { matterId } },
  });
  return required(data, 'getMatter');
}

export function useClients(params: ClientListParams) {
  return usePagedQuery(clientsMattersKeys.clientList(params), () => fetchClients(params));
}

/** Clientes para los selectores de Asuntos (filtro y formulario). */
export function useClientOptions() {
  const params: ClientListParams = { page: 0, size: CLIENT_OPTIONS_SIZE };
  return useQuery({
    queryKey: clientsMattersKeys.clientList(params),
    queryFn: () => fetchClients(params),
    staleTime: 60 * 1000,
  });
}

export function useMatters(params: MatterListParams) {
  return usePagedQuery(clientsMattersKeys.matterList(params), () => fetchMatters(params));
}

export function useMatter(matterId: string) {
  return useQuery({
    queryKey: clientsMattersKeys.matter(matterId),
    queryFn: () => fetchMatter(matterId),
  });
}

export function useCreateClient() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: ClientCreate) => {
      const { data } = await api.POST('/api/clients', { body });
      return required(data, 'createClient');
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: clientsMattersKeys.clients }),
  });
}

export function useCreateMatter() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: MatterCreate) => {
      const { data } = await api.POST('/api/matters', { body });
      return required(data, 'createMatter');
    },
    onSuccess: (matter) => {
      queryClient.setQueryData(clientsMattersKeys.matter(matter.id), matter);
      return queryClient.invalidateQueries({ queryKey: clientsMattersKeys.matters });
    },
  });
}

/**
 * Cierra un asunto. Ante `409 matter-already-closed` (otro Socio lo cerró antes) vuelve a leer el
 * asunto para mostrarlo `Cerrado` (spec "Asunto cerrado por otro Socio mientras tanto").
 */
export function useCloseMatter(matterId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      const { data } = await api.POST('/api/matters/{matterId}/close', {
        params: { path: { matterId } },
      });
      return required(data, 'closeMatter');
    },
    onSuccess: (matter) => {
      queryClient.setQueryData(clientsMattersKeys.matter(matterId), matter);
      return queryClient.invalidateQueries({ queryKey: clientsMattersKeys.matters });
    },
    onError: (error) => {
      if (error instanceof ApiProblem && error.code === 'matter-already-closed') {
        return Promise.all([
          queryClient.invalidateQueries({ queryKey: clientsMattersKeys.matter(matterId) }),
          queryClient.invalidateQueries({ queryKey: clientsMattersKeys.matters }),
        ]);
      }
      return undefined;
    },
  });
}

const STATUS_LABELS: Record<MatterStatus, string> = {
  OPEN: 'Abierto',
  CLOSED: 'Cerrado',
};

export function matterStatusLabel(status: MatterStatus): string {
  return STATUS_LABELS[status];
}

export function isMatterStatus(value: string): value is MatterStatus {
  return value === 'OPEN' || value === 'CLOSED';
}

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function isUuid(value: string): boolean {
  return UUID_PATTERN.test(value);
}

const DATE_TIME_FORMAT = new Intl.DateTimeFormat('es-BO', {
  dateStyle: 'medium',
  timeStyle: 'short',
  timeZone: 'America/La_Paz',
});

/** Fecha y hora en la zona de la firma (`America/La_Paz`). */
export function formatDateTime(iso: string): string {
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? iso : DATE_TIME_FORMAT.format(date);
}
