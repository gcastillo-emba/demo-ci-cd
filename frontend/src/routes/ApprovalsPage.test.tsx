import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import type { TimeEntry } from '../api/timeEntries';
import {
  abogado,
  makeClient,
  makeMatter,
  makeTimeEntry,
  pageOf,
  problem,
  signIn,
  socia,
} from '../test/fixtures';
import { jsonResponse, mockFetch, problemResponse, renderApp } from '../test/utils';

const andinas = makeClient('Inversiones Andinas S.A.');
const matter = makeMatter('AS-2026-0012', 'Due diligence adquisición planta', andinas);

/** Cola simulada: solo `PENDING` (el resto se resolvió), como `status=PENDING` en la API. */
function queueApi(initial: TimeEntry[]) {
  const store = [...initial];
  return {
    store,
    list: (request: Request) => {
      const url = new URL(request.url);
      const status = url.searchParams.get('status');
      return jsonResponse(
        pageOf(
          store.filter((e) => !status || e.status === status),
          url,
        ),
      );
    },
    resolve: (id: string, status: TimeEntry['status'], reason: string | null = null) => {
      const index = store.findIndex((e) => e.id === id);
      store[index] = { ...store[index]!, status, rejectionReason: reason };
      return store[index]!;
    },
  };
}

function dialog() {
  return document.querySelector('dialog') as HTMLDialogElement;
}

function queueRows() {
  return within(screen.getByRole('table', { name: 'Registros pendientes de aprobación' }))
    .getAllByRole('row')
    .slice(1);
}

const first = () =>
  makeTimeEntry(matter, {
    description: 'Revisión de contrato',
    hours: 2,
    lawyerName: 'Abogado Uno',
  });
const second = () =>
  makeTimeEntry(matter, {
    description: 'Audiencia preliminar',
    hours: 3.5,
    lawyerName: 'Abogada Dos',
  });

describe('Pantalla Aprobaciones', () => {
  // Scenario: Aprobar desde la cola
  it('aprobar quita el registro de la cola y confirma en español', async () => {
    const entries = [first(), second()];
    const sim = queueApi(entries);
    const { calls } = mockFetch({
      'GET /api/me': signIn(socia),
      'GET /api/time-entries': sim.list,
      [`POST /api/time-entries/${entries[0]!.id}/approve`]: () =>
        jsonResponse(sim.resolve(entries[0]!.id, 'APPROVED')),
    });
    const user = userEvent.setup();
    renderApp('/aprobaciones');

    expect(await screen.findByRole('heading', { level: 1, name: 'Aprobaciones' })).toBeVisible();
    await screen.findByRole('table', { name: 'Registros pendientes de aprobación' });
    expect(queueRows()).toHaveLength(2);
    expect(queueRows()[0]).toHaveTextContent('Abogado Uno');
    expect(queueRows()[1]).toHaveTextContent('3,5');
    // Cola: PENDING del más antiguo al más reciente.
    const listUrl = new URL(calls.find((c) => c.url.includes('/api/time-entries'))!.url);
    expect(listUrl.searchParams.get('status')).toBe('PENDING');
    expect(listUrl.searchParams.get('sort')).toBe('createdAt,asc');

    await user.click(within(queueRows()[0]!).getByRole('button', { name: /^Aprobar/ }));

    expect(
      await screen.findByText(/^Registro aprobado: Abogado Uno, 2 h en AS-2026-0012/),
    ).toBeVisible();
    await waitFor(() => expect(queueRows()).toHaveLength(1));
    expect(queueRows()[0]).toHaveTextContent('Audiencia preliminar');
    expect(screen.getByRole('heading', { level: 1 })).toHaveFocus();
  });

  // Scenario: Rechazar exige motivo
  it('"Confirmar rechazo" está deshabilitado sin motivo; con motivo el registro sale de la cola', async () => {
    const entries = [first()];
    const sim = queueApi(entries);
    const bodies: unknown[] = [];
    mockFetch({
      'GET /api/me': signIn(socia),
      'GET /api/time-entries': sim.list,
      [`POST /api/time-entries/${entries[0]!.id}/reject`]: async (request) => {
        const body = (await request.clone().json()) as { reason: string };
        bodies.push(body);
        return jsonResponse(sim.resolve(entries[0]!.id, 'REJECTED', body.reason));
      },
    });
    const user = userEvent.setup();
    renderApp('/aprobaciones');

    await screen.findByRole('table', { name: 'Registros pendientes de aprobación' });
    const trigger = within(queueRows()[0]!).getByRole('button', { name: /^Rechazar/ });
    await user.click(trigger);

    expect(dialog()).toHaveAttribute('open');
    expect(dialog()).toHaveAccessibleName('Rechazar registro');
    expect(dialog()).toHaveAccessibleDescription(/Abogado Uno, 2 h en AS-2026-0012/);
    const reason = screen.getByRole('textbox', { name: /^Motivo del rechazo/ });
    expect(reason).toHaveFocus();
    expect(screen.getByText('0/500 caracteres')).toBeVisible();
    const confirm = screen.getByRole('button', { name: 'Confirmar rechazo' });
    expect(confirm).toBeDisabled();

    await user.type(reason, '   ');
    expect(confirm).toBeDisabled();
    await user.clear(reason);
    await user.type(reason, 'Descripción insuficiente');
    expect(screen.getByText('24/500 caracteres')).toBeVisible();
    expect(confirm).toBeEnabled();
    expect(reason).toHaveAttribute('maxlength', '500');
    await user.click(confirm);

    expect(await screen.findByText(/^Registro rechazado: Abogado Uno/)).toBeVisible();
    expect(bodies).toEqual([{ reason: 'Descripción insuficiente' }]);
    expect(dialog()).not.toHaveAttribute('open');
    await waitFor(() =>
      expect(screen.getByText('No hay registros pendientes de aprobación')).toBeVisible(),
    );
    expect(screen.getByRole('heading', { level: 1 })).toHaveFocus();
  });

  it('cancelar el rechazo cierra el diálogo, no envía nada y devuelve el foco', async () => {
    const entries = [first()];
    const sim = queueApi(entries);
    const { calls } = mockFetch({
      'GET /api/me': signIn(socia),
      'GET /api/time-entries': sim.list,
    });
    const user = userEvent.setup();
    renderApp('/aprobaciones');

    await screen.findByRole('table', { name: 'Registros pendientes de aprobación' });
    const trigger = within(queueRows()[0]!).getByRole('button', { name: /^Rechazar/ });
    await user.click(trigger);
    await user.type(screen.getByRole('textbox', { name: /^Motivo/ }), 'Borrador');
    fireEvent(dialog(), new Event('cancel', { cancelable: true }));

    await waitFor(() => expect(dialog()).not.toHaveAttribute('open'));
    expect(trigger).toHaveFocus();
    expect(calls.some((c) => c.method === 'POST')).toBe(false);
    // Al reabrir, el motivo empieza vacío.
    await user.click(trigger);
    expect(screen.getByRole('textbox', { name: /^Motivo/ })).toHaveValue('');
  });

  it('un 422 rejection-reason-required se muestra junto al motivo sin cerrar el diálogo', async () => {
    const entries = [first()];
    const sim = queueApi(entries);
    mockFetch({
      'GET /api/me': signIn(socia),
      'GET /api/time-entries': sim.list,
      [`POST /api/time-entries/${entries[0]!.id}/reject`]: () =>
        problemResponse(problem(422, 'rejection-reason-required', 'Reason required')),
    });
    const user = userEvent.setup();
    renderApp('/aprobaciones');

    await screen.findByRole('table', { name: 'Registros pendientes de aprobación' });
    await user.click(within(queueRows()[0]!).getByRole('button', { name: /^Rechazar/ }));
    const reason = screen.getByRole('textbox', { name: /^Motivo/ });
    await user.type(reason, 'x');
    await user.click(screen.getByRole('button', { name: 'Confirmar rechazo' }));

    await waitFor(() =>
      expect(reason).toHaveAccessibleDescription(
        expect.stringContaining('Indica el motivo del rechazo (de 1 a 500 caracteres).'),
      ),
    );
    expect(dialog()).toHaveAttribute('open');
    expect(document.body).not.toHaveTextContent('rejection-reason-required');
  });

  // Scenario: Registro ya resuelto por otro socio
  it('ante 409 entry-not-pending al aprobar avisa en español y recarga la cola', async () => {
    const entries = [first(), second()];
    const sim = queueApi(entries);
    mockFetch({
      'GET /api/me': signIn(socia),
      'GET /api/time-entries': sim.list,
      [`POST /api/time-entries/${entries[0]!.id}/approve`]: () => {
        sim.resolve(entries[0]!.id, 'APPROVED'); // otro socio lo aprobó antes
        return problemResponse(problem(409, 'entry-not-pending', 'Entry is not pending'));
      },
    });
    const user = userEvent.setup();
    renderApp('/aprobaciones');

    await screen.findByRole('table', { name: 'Registros pendientes de aprobación' });
    await user.click(within(queueRows()[0]!).getByRole('button', { name: /^Aprobar/ }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'El registro ya no está pendiente: otro socio lo resolvió. La cola se recargó.',
    );
    await waitFor(() => expect(queueRows()).toHaveLength(1));
    expect(document.body).not.toHaveTextContent('entry-not-pending');
  });

  it('ante 409 entry-not-pending al rechazar cierra el diálogo y recarga la cola', async () => {
    const entries = [first()];
    const sim = queueApi(entries);
    mockFetch({
      'GET /api/me': signIn(socia),
      'GET /api/time-entries': sim.list,
      [`POST /api/time-entries/${entries[0]!.id}/reject`]: () => {
        sim.resolve(entries[0]!.id, 'APPROVED');
        return problemResponse(problem(409, 'entry-not-pending', 'Entry is not pending'));
      },
    });
    const user = userEvent.setup();
    renderApp('/aprobaciones');

    await screen.findByRole('table', { name: 'Registros pendientes de aprobación' });
    await user.click(within(queueRows()[0]!).getByRole('button', { name: /^Rechazar/ }));
    await user.type(screen.getByRole('textbox', { name: /^Motivo/ }), 'Sin detalle');
    await user.click(screen.getByRole('button', { name: 'Confirmar rechazo' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('ya no está pendiente');
    expect(dialog()).not.toHaveAttribute('open');
    await waitFor(() =>
      expect(screen.getByText('No hay registros pendientes de aprobación')).toBeVisible(),
    );
  });

  // Scenario: Abogado no ve la opción
  it('el abogado no ve "Aprobaciones" en el menú y la ruta le niega el acceso', async () => {
    const { calls } = mockFetch({ 'GET /api/me': signIn(abogado) });
    renderApp('/aprobaciones');

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Acceso no permitido' }),
    ).toBeVisible();
    const nav = screen.getByRole('navigation', { name: 'Principal' });
    expect(within(nav).queryByRole('link', { name: 'Aprobaciones' })).not.toBeInTheDocument();
    expect(calls.some((c) => c.url.includes('/api/time-entries'))).toBe(false);
  });
});
