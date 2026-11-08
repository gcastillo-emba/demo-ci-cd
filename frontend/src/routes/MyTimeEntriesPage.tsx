import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import { PAGE_SIZE } from '../api/clientsMatters';
import { formatDate, formatHours, todayInLaPaz } from '../api/hours';
import { userMessage } from '../api/problemMessages';
import {
  isEditable,
  isStaleEntryError,
  isTimeEntryStatus,
  useTimeEntries,
  useUpdateTimeEntry,
  type TimeEntry,
} from '../api/timeEntries';
import { useAuth } from '../auth/useAuth';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { DataTable, type Column } from '../ui/DataTable';
import { Layout } from '../ui/Layout';
import { Pagination } from '../ui/Pagination';
import { SelectField } from '../ui/SelectField';
import { DateRangeFilter, dateRange } from './DateRangeFilter';
import { TimeEntryForm, type TimeEntryFormValues } from './TimeEntryForm';
import { TimeEntryStatusBadge } from './TimeEntryStatusBadge';
import { useListParams } from './useListParams';

const FILTER_KEYS = ['status', 'from', 'to'] as const;
const EDIT_FORM_ID = 'editar-registro';

const STATUS_OPTIONS = [
  { value: '', label: 'Todos' },
  { value: 'PENDING', label: 'Pendiente' },
  { value: 'APPROVED', label: 'Aprobada' },
  { value: 'REJECTED', label: 'Rechazada' },
];

/** Mensajes propios de esta pantalla para los 409 de la edición. */
const STALE_MESSAGES = {
  'concurrent-modification':
    'El registro cambió mientras lo editabas. Se recargó la lista con los datos vigentes.',
  'entry-not-pending':
    'El registro ya no está pendiente y no se puede editar. Se recargó la lista con los datos vigentes.',
};

function resultsText(total: number): string {
  if (total === 0) return 'No tienes registros con estos filtros';
  return total === 1 ? '1 registro' : `${total} registros`;
}

/** Pantalla **Mis registros** del abogado (spec "Pantalla Mis registros"). */
export function MyTimeEntriesPage() {
  const { user } = useAuth();
  const { filters, page, update } = useListParams(FILTER_KEYS);
  const status = isTimeEntryStatus(filters.status) ? filters.status : undefined;
  const range = dateRange(filters.from, filters.to);
  const entries = useTimeEntries(
    {
      status,
      from: range.from || undefined,
      to: range.to || undefined,
      page,
      size: PAGE_SIZE,
    },
    { enabled: range.error === undefined },
  );
  const updateEntry = useUpdateTimeEntry();
  const [editing, setEditing] = useState<TimeEntry | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const headingRef = useRef<HTMLHeadingElement>(null);
  const today = todayInLaPaz();

  useEffect(() => {
    document.title = 'Mis registros — Asuntos & Horas';
  }, []);

  function startEdit(entry: TimeEntry) {
    setNotice(null);
    setError(null);
    setEditing(entry);
  }

  function closeEdit() {
    setEditing(null);
    headingRef.current?.focus();
  }

  async function handleUpdate(values: TimeEntryFormValues) {
    if (!editing) return;
    try {
      const entry = await updateEntry.mutateAsync({
        id: editing.id,
        body: { ...values, version: editing.version },
      });
      setNotice(
        `Registro actualizado: ${formatHours(entry.hours)} h en ${entry.matterCode} el ` +
          `${formatDate(entry.workDate)}.`,
      );
      closeEdit();
    } catch (failure) {
      // 409: el registro cambió en el servidor; se cierra la edición y la lista ya se recargó.
      if (isStaleEntryError(failure)) {
        setError(userMessage(failure, STALE_MESSAGES));
        closeEdit();
        return;
      }
      throw failure;
    }
  }

  const columns: Column<TimeEntry>[] = [
    { header: 'Fecha', cell: (e) => formatDate(e.workDate), rowHeader: true },
    { header: 'Asunto', cell: (e) => e.matterCode },
    { header: 'Horas', cell: (e) => formatHours(e.hours) },
    { header: 'Descripción', cell: (e) => e.description },
    { header: 'Estado', cell: (e) => <TimeEntryStatusBadge status={e.status} /> },
    { header: 'Motivo de rechazo', cell: (e) => e.rejectionReason ?? '—' },
    {
      header: 'Acciones',
      cell: (e) =>
        user && isEditable(e, user.id, today) ? (
          <Button
            variant="outline"
            aria-label={`Editar registro del ${formatDate(e.workDate)} en ${e.matterCode}`}
            aria-expanded={editing?.id === e.id}
            aria-controls={editing?.id === e.id ? EDIT_FORM_ID : undefined}
            onClick={() => startEdit(e)}
          >
            Editar
          </Button>
        ) : (
          <span className="muted">No editable</span>
        ),
    },
  ];

  const data = entries.data;

  return (
    <Layout>
      <section className="card" aria-labelledby="my-entries-title">
        <div className="page-header">
          <h1 id="my-entries-title" ref={headingRef} tabIndex={-1}>
            Mis registros
          </h1>
          <Link to="/registrar-horas">Registrar horas</Link>
        </div>

        {notice ? (
          <p role="status" className="notice">
            {notice}
          </p>
        ) : null}
        {error ? <Alert title="No se guardó la edición">{error}</Alert> : null}

        {editing ? (
          <TimeEntryForm
            key={`${editing.id}-${editing.version}`}
            id={EDIT_FORM_ID}
            title={`Editar registro del ${formatDate(editing.workDate)}`}
            submitLabel="Guardar cambios"
            initial={editing}
            currentMatter={{ value: editing.matterId, label: editing.matterCode }}
            notFoundMessage="El registro o el asunto ya no existe."
            onSubmit={handleUpdate}
            onCancel={closeEdit}
          />
        ) : null}

        <div className="filters" role="search" aria-label="Filtrar mis registros">
          <SelectField
            label="Estado"
            name="filter-status"
            options={STATUS_OPTIONS}
            value={status ?? ''}
            onChange={(e) => update({ status: e.target.value })}
          />
          <DateRangeFilter
            from={range.from}
            to={range.to}
            error={range.error}
            onChange={(changes) => update(changes)}
          />
        </div>

        {entries.isError ? (
          <Alert title="No se pudo cargar la lista de registros">
            {userMessage(entries.error)}
          </Alert>
        ) : null}
        {entries.isPending && range.error === undefined ? (
          <p role="status" className="status">
            Cargando registros…
          </p>
        ) : null}

        {data && range.error === undefined ? (
          <>
            <p role="status" className="status results-count">
              {resultsText(data.totalItems)}
            </p>
            {data.items.length > 0 ? (
              <DataTable
                caption="Mis registros de horas"
                columns={columns}
                rows={data.items}
                rowKey={(e) => e.id}
                busy={entries.isFetching}
              />
            ) : null}
            {data.totalItems > 0 ? (
              <Pagination
                label="Paginación de mis registros"
                page={data.page}
                totalPages={data.totalPages}
                onPageChange={(next) => update({ page: next })}
              />
            ) : null}
          </>
        ) : null}
      </section>
    </Layout>
  );
}
