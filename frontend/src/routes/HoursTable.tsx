import { formatDashboardHours } from '../api/dashboard';
import { HoursBar } from './HoursBar';

export interface HoursRow {
  key: string;
  /** Texto de la celda que encabeza la fila (`<th scope="row">`). */
  label: string;
  approvedHours: number;
  pendingHours: number;
}

export interface HoursTableProps {
  caption: string;
  /** Encabezado de la primera columna ("Asunto" o "Abogado"). */
  subjectHeader: string;
  rows: HoursRow[];
}

/**
 * Tabla accesible de horas aprobadas vs pendientes (spec "Tablas accesibles por asunto y por
 * abogado"): `<caption>`, `<th scope="col">` y la primera celda de cada fila como `<th scope="row">`.
 */
export function HoursTable({ caption, subjectHeader, rows }: HoursTableProps) {
  const max = rows.reduce((acc, row) => Math.max(acc, row.approvedHours + row.pendingHours), 0);

  return (
    <div className="table-wrap">
      <table className="data-table hours-table">
        <caption className="data-table__caption">{caption}</caption>
        <thead>
          <tr>
            <th scope="col">{subjectHeader}</th>
            <th scope="col" className="hours-table__number">
              Aprobadas
            </th>
            <th scope="col" className="hours-table__number">
              Pendientes
            </th>
            <th scope="col">Distribución</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.key}>
              <th scope="row">{row.label}</th>
              <td className="hours-table__number">{formatDashboardHours(row.approvedHours)}</td>
              <td className="hours-table__number">{formatDashboardHours(row.pendingHours)}</td>
              <td className="hours-table__bar">
                <HoursBar
                  approvedHours={row.approvedHours}
                  pendingHours={row.pendingHours}
                  max={max}
                />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
