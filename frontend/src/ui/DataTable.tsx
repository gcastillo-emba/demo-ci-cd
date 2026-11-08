import type { ReactNode } from 'react';

export interface Column<T> {
  header: string;
  cell: (row: T) => ReactNode;
  /** La celda de esta columna encabeza la fila (`<th scope="row">`). */
  rowHeader?: boolean;
}

export interface DataTableProps<T> {
  caption: string;
  columns: Column<T>[];
  rows: T[];
  rowKey: (row: T) => string;
  busy?: boolean;
}

/** Tabla accesible con `<caption>` y encabezados con `scope` (design D9). */
export function DataTable<T>({ caption, columns, rows, rowKey, busy = false }: DataTableProps<T>) {
  return (
    <div className="table-wrap">
      <table className="data-table" aria-busy={busy || undefined}>
        <caption className="data-table__caption">{caption}</caption>
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column.header} scope="col">
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={rowKey(row)}>
              {columns.map((column) =>
                column.rowHeader ? (
                  <th key={column.header} scope="row">
                    {column.cell(row)}
                  </th>
                ) : (
                  <td key={column.header}>{column.cell(row)}</td>
                ),
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
