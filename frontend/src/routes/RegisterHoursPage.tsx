import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { formatDate, formatHours } from '../api/hours';
import { timeEntryStatusLabel, useCreateTimeEntry } from '../api/timeEntries';
import { Layout } from '../ui/Layout';
import { TimeEntryForm, type TimeEntryFormValues } from './TimeEntryForm';

/** Pantalla **Registrar horas** del abogado (spec "Pantalla Registrar horas"). */
export function RegisterHoursPage() {
  const createEntry = useCreateTimeEntry();
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    document.title = 'Registrar horas — Asuntos & Horas';
  }, []);

  async function handleSubmit(values: TimeEntryFormValues) {
    setNotice(null);
    const entry = await createEntry.mutateAsync(values);
    setNotice(
      `Registro guardado: ${formatHours(entry.hours)} h en ${entry.matterCode} el ` +
        `${formatDate(entry.workDate)}, estado ${timeEntryStatusLabel(entry.status)}.`,
    );
  }

  return (
    <Layout>
      <section className="card" aria-labelledby="register-hours-title">
        <div className="page-header">
          <h1 id="register-hours-title">Registrar horas</h1>
          <Link to="/mis-registros">Ver mis registros</Link>
        </div>
        {notice ? (
          <p role="status" className="notice">
            {notice}
          </p>
        ) : null}
        <TimeEntryForm
          title="Nuevo registro de horas"
          submitLabel="Guardar"
          resetOnSuccess
          onSubmit={handleSubmit}
        />
      </section>
    </Layout>
  );
}
