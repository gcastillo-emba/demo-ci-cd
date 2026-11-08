import { isIsoDate } from '../api/hours';
import { TextField } from '../ui/TextField';

export const DATE_RANGE_ERROR = 'La fecha "Desde" no puede ser posterior a "Hasta".';

/**
 * Normaliza el rango de fechas de los filtros de la URL: valores que no son fechas se ignoran (en
 * lugar de provocar un 400) y un rango invertido se informa sin consultar la API (la API
 * respondería 400 `validation-error`, spec "Rango de fechas invertido").
 */
export function dateRange(rawFrom: string, rawTo: string) {
  const from = isIsoDate(rawFrom) ? rawFrom : '';
  const to = isIsoDate(rawTo) ? rawTo : '';
  const error = from !== '' && to !== '' && from > to ? DATE_RANGE_ERROR : undefined;
  return { from, to, error };
}

export interface DateRangeFilterProps {
  from: string;
  to: string;
  error?: string;
  onChange: (changes: { from?: string; to?: string }) => void;
}

/** Filtros "Desde" / "Hasta" por fecha de trabajo (inclusivos). */
export function DateRangeFilter({ from, to, error, onChange }: DateRangeFilterProps) {
  return (
    <>
      <TextField
        label="Desde"
        name="filter-from"
        type="date"
        value={from}
        onChange={(e) => onChange({ from: e.target.value })}
      />
      <TextField
        label="Hasta"
        name="filter-to"
        type="date"
        value={to}
        error={error}
        onChange={(e) => onChange({ to: e.target.value })}
      />
    </>
  );
}
