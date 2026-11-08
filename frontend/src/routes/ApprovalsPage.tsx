import { useEffect, useRef, useState } from 'react';
import { PAGE_SIZE } from '../api/clientsMatters';
import { formatDate, formatHours } from '../api/hours';
import { ApiProblem } from '../api/problem';
import { userMessage } from '../api/problemMessages';
import {
  isStaleEntryError,
  useApproveTimeEntry,
  useRejectTimeEntry,
  useTimeEntries,
  type TimeEntry,
} from '../api/timeEntries';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { DataTable, type Column } from '../ui/DataTable';
import { Layout } from '../ui/Layout';
import { Pagination } from '../ui/Pagination';
import { DateRangeFilter, dateRange } from './DateRangeFilter';
import { RejectDialog } from './RejectDialog';
import { useListParams } from './useListParams';

const FILTER_KEYS = ['from', 'to'] as const;

/** Mensajes propios de la cola (spec "Registro ya resuelto por otro socio"). */
const QUEUE_MESSAGES = {
  'entry-not-pending':
    'El registro ya no está pendiente: otro socio lo resolvió. La cola se recargó.',
  'not-found': 'El registro ya no existe. La cola se recargó.',
};

function describe(entry: TimeEntry): string {
  return (
    `${entry.lawyerName}, ${formatHours(entry.hours)} h en ${entry.matterCode} ` +
    `el ${formatDate(entry.workDate)}`
  );
}

function resultsText(total: number): string {
  if (total === 0) return 'No hay registros pendientes de aprobación';
  return total === 1 ? '1 registro pendiente' : `${total} registros pendientes`;
}

/** Pantalla **Aprobaciones** del socio (spec "Pantalla Aprobaciones"). */
export function ApprovalsPage() {
  const { filters, page, update } = useListParams(FILTER_KEYS);
  const range = dateRange(filters.from, filters.to);
  const queue = useTimeEntries(
    {
      status: 'PENDING',
      sort: 'createdAt,asc',
      from: range.from || undefined,
      to: range.to || undefined,
      page,
      size: PAGE_SIZE,
    },
    { enabled: range.error === undefined },
  );
  const approve = useApproveTimeEntry();
  const reject = useRejectTimeEntry();
  const [approvingId, setApprovingId] = useState<string | null>(null);
  const [rejecting, setRejecting] = useState<TimeEntry | null>(null);
  const [rejectError, setRejectError] = useState<string | undefined>(undefined);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const headingRef = useRef<HTMLHeadingElement>(null);
  // La fila resuelta desaparece: el foco pasa al encabezado. Los efectos del diálogo (hijo) corren
  // antes que este, así que gana sobre la devolución de foco al botón "Rechazar".
  const focusHeading = useRef(false);

  useEffect(() => {
    document.title = 'Aprobaciones — Asuntos & Horas';
  }, []);

  useEffect(() => {
    if (focusHeading.current) {
      focusHeading.current = false;
      headingRef.current?.focus();
    }
  });

  async function handleApprove(entry: TimeEntry) {
    if (approvingId) return;
    setNotice(null);
    setError(null);
    setApprovingId(entry.id);
    try {
      await approve.mutateAsync(entry.id);
      setNotice(`Registro aprobado: ${describe(entry)}.`);
    } catch (failure) {
      setError(userMessage(failure, QUEUE_MESSAGES));
    } finally {
      setApprovingId(null);
      focusHeading.current = true;
    }
  }

  function openReject(entry: TimeEntry) {
    setNotice(null);
    setError(null);
    setRejectError(undefined);
    setRejecting(entry);
  }

  async function handleReject(reason: string) {
    if (!rejecting || reject.isPending) return;
    const entry = rejecting;
    setRejectError(undefined);
    try {
      await reject.mutateAsync({ id: entry.id, reason });
      setNotice(`Registro rechazado: ${describe(entry)}.`);
      setRejecting(null);
      focusHeading.current = true;
    } catch (failure) {
      const notFound = failure instanceof ApiProblem && failure.code === 'not-found';
      if (isStaleEntryError(failure) || notFound) {
        // Ya resuelto por otro socio: se cierra el diálogo y la cola ya se recargó.
        setError(userMessage(failure, QUEUE_MESSAGES));
        setRejecting(null);
        focusHeading.current = true;
        return;
      }
      setRejectError(userMessage(failure));
    }
  }

  const columns: Column<TimeEntry>[] = [
    { header: 'Abogado', cell: (e) => e.lawyerName, rowHeader: true },
    { header: 'Asunto', cell: (e) => e.matterCode },
    { header: 'Fecha', cell: (e) => formatDate(e.workDate) },
    { header: 'Horas', cell: (e) => formatHours(e.hours) },
    { header: 'Descripción', cell: (e) => e.description },
    {
      header: 'Acciones',
      cell: (e) => (
        <div className="row-actions">
          <Button
            aria-label={`Aprobar registro de ${describe(e)}`}
            busy={approvingId === e.id}
            onClick={() => handleApprove(e)}
          >
            Aprobar
          </Button>
          <Button
            variant="outline"
            aria-label={`Rechazar registro de ${describe(e)}`}
            disabled={approvingId === e.id}
            onClick={() => openReject(e)}
          >
            Rechazar
          </Button>
        </div>
      ),
    },
  ];

  const data = queue.data;

  return (
    <Layout>
      <section className="card" aria-labelledby="approvals-title">
        <div className="page-header">
          <h1 id="approvals-title" ref={headingRef} tabIndex={-1}>
            Aprobaciones
          </h1>
        </div>
        <p className="field__hint">
          Registros pendientes de todos los abogados, del más antiguo al más reciente.
        </p>

        {notice ? (
          <p role="status" className="notice">
            {notice}
          </p>
        ) : null}
        {error ? <Alert title="No se completó la acción">{error}</Alert> : null}

        <div className="filters" role="search" aria-label="Filtrar aprobaciones">
          <DateRangeFilter
            from={range.from}
            to={range.to}
            error={range.error}
            onChange={(changes) => update(changes)}
          />
        </div>

        {queue.isError ? (
          <Alert title="No se pudo cargar la cola de aprobaciones">
            {userMessage(queue.error)}
          </Alert>
        ) : null}
        {queue.isPending && range.error === undefined ? (
          <p role="status" className="status">
            Cargando registros pendientes…
          </p>
        ) : null}

        {data && range.error === undefined ? (
          <>
            <p role="status" className="status results-count">
              {resultsText(data.totalItems)}
            </p>
            {data.items.length > 0 ? (
              <DataTable
                caption="Registros pendientes de aprobación"
                columns={columns}
                rows={data.items}
                rowKey={(e) => e.id}
                busy={queue.isFetching}
              />
            ) : null}
            {data.totalItems > 0 ? (
              <Pagination
                label="Paginación de aprobaciones"
                page={data.page}
                totalPages={data.totalPages}
                onPageChange={(next) => update({ page: next })}
              />
            ) : null}
          </>
        ) : null}

        <RejectDialog
          open={rejecting !== null}
          summary={rejecting ? `Registro de ${describe(rejecting)}.` : ''}
          busy={reject.isPending}
          error={rejectError}
          onConfirm={handleReject}
          onCancel={() => setRejecting(null)}
        />
      </section>
    </Layout>
  );
}
