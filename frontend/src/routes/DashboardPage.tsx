import { useEffect } from 'react';
import {
  formatDashboardHours,
  isEmptySummary,
  useDashboardSummary,
  type DashboardSummary,
} from '../api/dashboard';
import { formatDate } from '../api/hours';
import { userMessage } from '../api/problemMessages';
import { Alert } from '../ui/Alert';
import { Layout } from '../ui/Layout';
import { HoursTable, type HoursRow } from './HoursTable';

export const EMPTY_DASHBOARD_MESSAGE = 'No hay horas registradas en los últimos 30 días.';

function matterRows(summary: DashboardSummary): HoursRow[] {
  return summary.byMatter.map((row) => ({
    key: row.matterId,
    label: `${row.matterCode} — ${row.matterTitle}`,
    approvedHours: row.approvedHours,
    pendingHours: row.pendingHours,
  }));
}

function lawyerRows(summary: DashboardSummary): HoursRow[] {
  return summary.byLawyer.map((row) => ({
    key: row.lawyerId,
    label: row.lawyerName,
    approvedHours: row.approvedHours,
    pendingHours: row.pendingHours,
  }));
}

function SummaryContent({ summary }: { summary: DashboardSummary }) {
  const empty = isEmptySummary(summary);
  const byMatter = matterRows(summary);
  const byLawyer = lawyerRows(summary);

  return (
    <>
      <p className="dashboard__period">
        Últimos 30 días: {formatDate(summary.from)} – {formatDate(summary.to)}
      </p>

      <section aria-labelledby="dashboard-totals-title" className="dashboard__totals">
        <h2 id="dashboard-totals-title">Totales del período</h2>
        <ul className="dashboard__totals-list">
          <li>Aprobadas: {formatDashboardHours(summary.totals.approvedHours)}</li>
          <li>Pendientes: {formatDashboardHours(summary.totals.pendingHours)}</li>
        </ul>
      </section>

      {empty ? (
        <p role="status" className="notice">
          {EMPTY_DASHBOARD_MESSAGE}
        </p>
      ) : null}

      {/* Sin tablas vacías sin explicación: cada tabla aparece solo si tiene filas (la fila propia
          en cero del abogado sin horas sí se muestra, design Supuesto 1). */}
      {byMatter.length > 0 ? (
        <HoursTable caption="Horas por asunto" subjectHeader="Asunto" rows={byMatter} />
      ) : null}
      {byLawyer.length > 0 ? (
        <HoursTable caption="Horas por abogado" subjectHeader="Abogado" rows={byLawyer} />
      ) : null}

      <p className="dashboard__legend muted">
        En cada barra, el tramo sólido son las horas aprobadas y el tramo rayado las pendientes; las
        horas rechazadas no se cuentan.
      </p>
    </>
  );
}

/** Pantalla **Tablero** (spec "Pantalla Tablero"): ventana fija de 30 días, sin selector de rango. */
export function DashboardPage() {
  const summary = useDashboardSummary();

  useEffect(() => {
    document.title = 'Tablero — Asuntos & Horas';
  }, []);

  return (
    <Layout>
      <section
        className="card dashboard"
        aria-labelledby="dashboard-title"
        aria-busy={summary.isPending || undefined}
      >
        <h1 id="dashboard-title">Tablero</h1>

        {summary.isPending ? (
          <p role="status" className="status">
            Cargando tablero…
          </p>
        ) : null}

        {/* 401: el manejador global cierra la sesión y lleva al login (client.ts). */}
        {summary.isError ? (
          <Alert title="No se pudo cargar el tablero">{userMessage(summary.error)}</Alert>
        ) : null}

        {summary.data ? <SummaryContent summary={summary.data} /> : null}
      </section>
    </Layout>
  );
}
