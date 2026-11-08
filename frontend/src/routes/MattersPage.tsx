import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import {
  isMatterStatus,
  isUuid,
  PAGE_SIZE,
  useClientOptions,
  useMatters,
  type Matter,
} from '../api/clientsMatters';
import { userMessage } from '../api/problemMessages';
import { useAuth } from '../auth/useAuth';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { DataTable, type Column } from '../ui/DataTable';
import { Layout } from '../ui/Layout';
import { Pagination } from '../ui/Pagination';
import { SearchInput } from '../ui/SearchInput';
import { SelectField } from '../ui/SelectField';
import { MatterForm } from './MatterForm';
import { MatterStatusBadge } from './MatterStatusBadge';
import { useListParams } from './useListParams';

const FILTER_KEYS = ['q', 'clientId', 'status'] as const;
const FORM_ID = 'nuevo-asunto';

const STATUS_OPTIONS = [
  { value: '', label: 'Todos' },
  { value: 'OPEN', label: 'Abierto' },
  { value: 'CLOSED', label: 'Cerrado' },
];

const columns: Column<Matter>[] = [
  {
    header: 'Código',
    cell: (m) => <Link to={`/asuntos/${m.id}`}>{m.code}</Link>,
    rowHeader: true,
  },
  { header: 'Título', cell: (m) => m.title },
  { header: 'Cliente', cell: (m) => m.clientName },
  { header: 'Estado', cell: (m) => <MatterStatusBadge status={m.status} /> },
];

function resultsText(total: number): string {
  if (total === 0) return 'No se encontraron asuntos';
  return total === 1 ? '1 asunto encontrado' : `${total} asuntos encontrados`;
}

/** Pantalla **Asuntos** (spec "Pantalla Asuntos" y "Formularios de alta solo para el Socio"). */
export function MattersPage() {
  const { user } = useAuth();
  const isPartner = user?.role === 'PARTNER';
  const { filters, page, update } = useListParams(FILTER_KEYS);
  // Valores de la URL fuera de dominio se ignoran en lugar de provocar un 400.
  const clientId = isUuid(filters.clientId) ? filters.clientId : '';
  const status = isMatterStatus(filters.status) ? filters.status : undefined;
  const matters = useMatters({ q: filters.q, clientId, status, page, size: PAGE_SIZE });
  const clients = useClientOptions();
  const [formOpen, setFormOpen] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    document.title = 'Asuntos — Asuntos & Horas';
  }, []);

  function handleCreated(matter: Matter) {
    setFormOpen(false);
    setNotice(`Asunto creado con el código ${matter.code}.`);
    // Se filtra por el código nuevo para que el asunto aparezca en la lista.
    update({ q: matter.code, clientId: null, status: null });
  }

  const clientOptions = [
    { value: '', label: 'Todos los clientes' },
    ...(clients.data?.items ?? []).map((c) => ({ value: c.id, label: c.name })),
  ];
  const data = matters.data;

  return (
    <Layout>
      <section className="card" aria-labelledby="matters-title">
        <div className="page-header">
          <h1 id="matters-title">Asuntos</h1>
          {isPartner ? (
            <Button
              aria-expanded={formOpen}
              aria-controls={formOpen ? FORM_ID : undefined}
              onClick={() => {
                setFormOpen((open) => !open);
                setNotice(null);
              }}
            >
              Nuevo asunto
            </Button>
          ) : null}
        </div>

        {isPartner && formOpen ? (
          <MatterForm
            id={FORM_ID}
            defaultClientId={clientId}
            onCreated={handleCreated}
            onCancel={() => setFormOpen(false)}
          />
        ) : null}

        {notice ? (
          <p role="status" className="notice">
            {notice}
          </p>
        ) : null}

        <div className="filters" role="search" aria-label="Filtrar asuntos">
          <SelectField
            label="Cliente"
            name="filter-clientId"
            options={clientOptions}
            value={clientId}
            onChange={(e) => update({ clientId: e.target.value })}
          />
          <SelectField
            label="Estado"
            name="filter-status"
            options={STATUS_OPTIONS}
            value={status ?? ''}
            onChange={(e) => update({ status: e.target.value })}
          />
          <SearchInput
            label="Buscar por título o código"
            value={filters.q}
            onSearch={(q) => update({ q })}
          />
        </div>

        {clients.isError ? (
          <Alert title="No se pudo cargar la lista de clientes">{userMessage(clients.error)}</Alert>
        ) : null}
        {matters.isError ? (
          <Alert title="No se pudo cargar la lista de asuntos">{userMessage(matters.error)}</Alert>
        ) : null}

        {matters.isPending ? (
          <p role="status" className="status">
            Cargando asuntos…
          </p>
        ) : null}

        {data ? (
          <>
            <p role="status" className="status results-count">
              {resultsText(data.totalItems)}
            </p>
            {data.items.length > 0 ? (
              <DataTable
                caption="Lista de asuntos"
                columns={columns}
                rows={data.items}
                rowKey={(m) => m.id}
                busy={matters.isFetching}
              />
            ) : null}
            {data.totalItems > 0 ? (
              <Pagination
                label="Paginación de asuntos"
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
