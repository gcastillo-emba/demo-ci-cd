import { useEffect, useState } from 'react';
import { PAGE_SIZE, useClients, type Client } from '../api/clientsMatters';
import { userMessage } from '../api/problemMessages';
import { useAuth } from '../auth/useAuth';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { DataTable, type Column } from '../ui/DataTable';
import { Layout } from '../ui/Layout';
import { Pagination } from '../ui/Pagination';
import { SearchInput } from '../ui/SearchInput';
import { ClientForm } from './ClientForm';
import { useListParams } from './useListParams';

const FILTER_KEYS = ['q'] as const;
const FORM_ID = 'nuevo-cliente';

const columns: Column<Client>[] = [
  { header: 'Nombre', cell: (c) => c.name, rowHeader: true },
  { header: 'Identificación tributaria', cell: (c) => c.taxId ?? '—' },
];

function resultsText(total: number): string {
  if (total === 0) return 'No se encontraron clientes';
  return total === 1 ? '1 cliente encontrado' : `${total} clientes encontrados`;
}

/** Pantalla **Clientes** (spec "Pantalla Clientes" y "Formularios de alta solo para el Socio"). */
export function ClientsPage() {
  const { user } = useAuth();
  const isPartner = user?.role === 'PARTNER';
  const { filters, page, update } = useListParams(FILTER_KEYS);
  const clients = useClients({ q: filters.q, page, size: PAGE_SIZE });
  const [formOpen, setFormOpen] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    document.title = 'Clientes — Asuntos & Horas';
  }, []);

  function handleCreated(client: Client) {
    setFormOpen(false);
    setNotice(`Cliente creado: ${client.name}.`);
    // Se filtra por el nombre recién creado para que aparezca en la lista aunque no esté en la página 1.
    update({ q: client.name });
  }

  const data = clients.data;

  return (
    <Layout>
      <section className="card" aria-labelledby="clients-title">
        <div className="page-header">
          <h1 id="clients-title">Clientes</h1>
          {isPartner ? (
            <Button
              aria-expanded={formOpen}
              aria-controls={formOpen ? FORM_ID : undefined}
              onClick={() => {
                setFormOpen((open) => !open);
                setNotice(null);
              }}
            >
              Nuevo cliente
            </Button>
          ) : null}
        </div>

        {isPartner && formOpen ? (
          <ClientForm id={FORM_ID} onCreated={handleCreated} onCancel={() => setFormOpen(false)} />
        ) : null}

        {notice ? (
          <p role="status" className="notice">
            {notice}
          </p>
        ) : null}

        <div className="filters" role="search" aria-label="Filtrar clientes">
          <SearchInput
            label="Buscar por nombre"
            value={filters.q}
            onSearch={(q) => update({ q })}
          />
        </div>

        {clients.isError ? (
          <Alert title="No se pudo cargar la lista de clientes">{userMessage(clients.error)}</Alert>
        ) : null}

        {clients.isPending ? (
          <p role="status" className="status">
            Cargando clientes…
          </p>
        ) : null}

        {data ? (
          <>
            <p role="status" className="status results-count">
              {resultsText(data.totalItems)}
            </p>
            {data.items.length > 0 ? (
              <DataTable
                caption="Lista de clientes"
                columns={columns}
                rows={data.items}
                rowKey={(c) => c.id}
                busy={clients.isFetching}
              />
            ) : null}
            {data.totalItems > 0 ? (
              <Pagination
                label="Paginación de clientes"
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
