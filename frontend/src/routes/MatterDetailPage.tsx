import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router';
import { formatDateTime, useCloseMatter, useMatter } from '../api/clientsMatters';
import { ApiProblem } from '../api/problem';
import { userMessage } from '../api/problemMessages';
import { useAuth } from '../auth/useAuth';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { ConfirmDialog } from '../ui/ConfirmDialog';
import { Layout } from '../ui/Layout';
import { MatterStatusBadge } from './MatterStatusBadge';

const NOT_FOUND_MESSAGES = {
  'not-found': 'El asunto no existe.',
  'validation-error': 'El asunto no existe.',
};

/**
 * Detalle de un asunto con la acción **Cerrar asunto** (spec "Cierre de asunto con confirmación en
 * la interfaz"): solo Socio y solo asuntos `Abierto`. No existe acción para reabrir ni editar.
 */
export function MatterDetailPage() {
  const { matterId = '' } = useParams();
  const { user } = useAuth();
  const isPartner = user?.role === 'PARTNER';
  const matter = useMatter(matterId);
  const closeMatter = useCloseMatter(matterId);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [closeError, setCloseError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const headingRef = useRef<HTMLHeadingElement>(null);
  const data = matter.data;

  useEffect(() => {
    document.title = `${data ? `Asunto ${data.code}` : 'Asunto'} — Asuntos & Horas`;
  }, [data]);

  async function handleConfirm() {
    setCloseError(null);
    try {
      await closeMatter.mutateAsync();
      setNotice('El asunto se cerró. El cierre es definitivo.');
    } catch (error) {
      const alreadyClosed = error instanceof ApiProblem && error.code === 'matter-already-closed';
      setCloseError(
        alreadyClosed ? 'El asunto ya estaba cerrado por otro usuario.' : userMessage(error),
      );
    } finally {
      setConfirmOpen(false);
      // El botón Cerrar asunto desaparece: el foco pasa al encabezado del asunto.
      headingRef.current?.focus();
    }
  }

  return (
    <Layout>
      <p className="back-link">
        <Link to="/asuntos">← Volver a Asuntos</Link>
      </p>
      {matter.isPending ? (
        <p role="status" className="status">
          Cargando asunto…
        </p>
      ) : null}
      {matter.isError ? (
        <Alert title="No se pudo cargar el asunto">
          {userMessage(matter.error, NOT_FOUND_MESSAGES)}
        </Alert>
      ) : null}
      {data ? (
        <section className="card" aria-labelledby="matter-title">
          <div className="page-header">
            <h1 id="matter-title" ref={headingRef} tabIndex={-1}>
              Asunto {data.code}
            </h1>
            {isPartner && data.status === 'OPEN' ? (
              <Button variant="danger" onClick={() => setConfirmOpen(true)}>
                Cerrar asunto
              </Button>
            ) : null}
          </div>

          {notice ? (
            <p role="status" className="notice">
              {notice}
            </p>
          ) : null}
          {closeError ? <Alert title="No se cerró el asunto">{closeError}</Alert> : null}

          <dl className="details">
            <div>
              <dt>Título</dt>
              <dd>{data.title}</dd>
            </div>
            <div>
              <dt>Cliente</dt>
              <dd>{data.clientName}</dd>
            </div>
            <div>
              <dt>Estado</dt>
              <dd>
                <MatterStatusBadge status={data.status} />
              </dd>
            </div>
            <div>
              <dt>Descripción</dt>
              <dd>{data.description ?? 'Sin descripción'}</dd>
            </div>
            <div>
              <dt>Creado</dt>
              <dd>{formatDateTime(data.createdAt)}</dd>
            </div>
            {data.closedAt ? (
              <div>
                <dt>Cerrado el</dt>
                <dd>{formatDateTime(data.closedAt)}</dd>
              </div>
            ) : null}
          </dl>

          {isPartner ? (
            <ConfirmDialog
              open={confirmOpen}
              title={`¿Cerrar el asunto ${data.code}?`}
              confirmLabel="Sí, cerrar asunto"
              busy={closeMatter.isPending}
              onConfirm={handleConfirm}
              onCancel={() => setConfirmOpen(false)}
            >
              <p>
                El cierre es <strong>irreversible</strong>: el asunto «{data.title}» no podrá
                reabrirse ni editarse.
              </p>
            </ConfirmDialog>
          ) : null}
        </section>
      ) : null}
    </Layout>
  );
}
