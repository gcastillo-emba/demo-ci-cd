import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router';

export type ListParamChanges<K extends string> = Partial<
  Record<K | 'page', string | number | null>
>;

function parsePage(raw: string | null): number {
  if (raw === null) return 0;
  const page = Number(raw);
  return Number.isInteger(page) && page >= 0 ? page : 0;
}

/**
 * Filtros y página de un listado guardados en la URL (`?q=&page=`, design D9), para que se puedan
 * compartir y sobrevivan a recargar. Cambiar un filtro vuelve a la primera página.
 */
export function useListParams<K extends string>(keys: readonly K[]) {
  const [searchParams, setSearchParams] = useSearchParams();

  const filters = useMemo(
    () =>
      Object.fromEntries(keys.map((key) => [key, searchParams.get(key)?.trim() ?? ''])) as Record<
        K,
        string
      >,
    // `keys` es una constante del llamador; la identidad que importa es la de la URL.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [searchParams],
  );
  const page = parsePage(searchParams.get('page'));

  const update = useCallback(
    (changes: ListParamChanges<K>) => {
      setSearchParams((previous) => {
        const next = new URLSearchParams(previous);
        for (const [key, value] of Object.entries(changes)) {
          const empty = value === null || value === undefined || value === '';
          if (empty || (key === 'page' && value === 0)) next.delete(key);
          else next.set(key, String(value));
        }
        if (!('page' in changes)) next.delete('page');
        return next;
      });
    },
    [setSearchParams],
  );

  return { filters, page, update };
}
