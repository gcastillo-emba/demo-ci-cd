import { useQuery } from '@tanstack/react-query';
import { api } from './client';
import type { components } from './generated/schema';

export type Features = components['schemas']['Features'];

export const featuresKeys = {
  all: ['features'] as const,
};

export async function fetchFeatures(): Promise<Features> {
  const { data } = await api.GET('/api/features');
  if (data === undefined) throw new Error('Respuesta vacía de getFeatures');
  return data;
}

/**
 * Feature flags de `GET /api/features`. Mientras cargan, o si fallan, la interfaz trata cada flag
 * como apagado: una funcionalidad detrás de un flag nunca aparece sin la confirmación del servidor.
 */
export function useFeatures() {
  return useQuery({ queryKey: featuresKeys.all, queryFn: fetchFeatures, staleTime: 60 * 1000 });
}
