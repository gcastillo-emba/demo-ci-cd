import { keepPreviousData, useQuery, type QueryKey } from '@tanstack/react-query';

/**
 * Consulta paginada (design D9): conserva la página anterior mientras llega la siguiente, para que
 * la tabla no parpadee al cambiar de página o de filtro. La reutilizan C2 y C3.
 */
export function usePagedQuery<T>(queryKey: QueryKey, fetcher: () => Promise<T>) {
  return useQuery({ queryKey, queryFn: fetcher, placeholderData: keepPreviousData });
}
