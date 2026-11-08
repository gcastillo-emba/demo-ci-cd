import { Button } from './Button';

export interface PaginationProps {
  /** Página actual (base 0, como la API). */
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  label?: string;
}

/** Controles Anterior / Siguiente con indicador "Página X de Y" (base 1 para el usuario). */
export function Pagination({
  page,
  totalPages,
  onPageChange,
  label = 'Paginación',
}: PaginationProps) {
  const total = Math.max(totalPages, 1);
  const isFirst = page <= 0;
  const isLast = page + 1 >= total;

  return (
    <nav aria-label={label} className="pagination">
      <Button variant="outline" disabled={isFirst} onClick={() => onPageChange(page - 1)}>
        Anterior
      </Button>
      <p className="pagination__status" aria-live="polite">
        Página {page + 1} de {total}
      </p>
      <Button variant="outline" disabled={isLast} onClick={() => onPageChange(page + 1)}>
        Siguiente
      </Button>
    </nav>
  );
}
