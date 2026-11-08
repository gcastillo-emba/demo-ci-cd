import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { addDays, todayInLaPaz } from '../api/hours';
import type { TimeEntry, TimeEntryUpdate } from '../api/timeEntries';
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

function mattersHandler(request: Request) {
  return jsonResponse(pageOf([matter], new URL(request.url)));
}

/** Listado simulado: filtra por `status`, `from` y `to` y responde páginas como la API. */
function listHandler(store: () => TimeEntry[]) {
  return (request: Request) => {
    const url = new URL(request.url);
    const status = url.searchParams.get('status');
    const from = url.searchParams.get('from');
    const to = url.searchParams.get('to');
    const items = store()
      .filter((e) => !status || e.status === status)
      .filter((e) => !from || e.workDate >= from)
      .filter((e) => !to || e.workDate <= to);
    return jsonResponse(pageOf(items, url));
  };
}

function table() {
  return screen.getByRole('table', { name: 'Mis registros de horas' });
}

function rowFor(text: string) {
  const row = within(table())
    .getAllByRole('row')
    .find((r) => r.textContent?.includes(text));
  if (!row) throw new Error(`Fila no encontrada: ${text}`);
  return row;
}

describe('Pantalla Mis registros', () => {
  // Scenario: Editar un registro editable
  it('edita un PENDING creado ayer a "2,5" y muestra la lista actualizada y una confirmación', async () => {
    let entry = makeTimeEntry(matter, { hours: 1.5, version: 3 });
    const bodies: TimeEntryUpdate[] = [];
    mockFetch({
      'GET /api/me': signIn(abogado),
      'GET /api/matters': mattersHandler,
      'GET /api/time-entries': listHandler(() => [entry]),
      [`PUT /api/time-entries/${entry.id}`]: async (request) => {
        const body = (await request.clone().json()) as TimeEntryUpdate;
        bodies.push(body);
        entry = { ...entry, ...body, version: entry.version + 1 };
        return jsonResponse(entry);
      },
    });
    const user = userEvent.setup();
    renderApp('/mis-registros');

    expect(await screen.findByRole('heading', { level: 1, name: 'Mis registros' })).toBeVisible();
    await screen.findByRole('table', { name: 'Mis registros de horas' });
    expect(rowFor('AS-2026-0012')).toHaveTextContent('1,5');
    expect(rowFor('AS-2026-0012')).toHaveTextContent('Pendiente');

    await user.click(within(rowFor('AS-2026-0012')).getByRole('button', { name: /^Editar/ }));
    const form = screen.getByRole('region', { name: /^Editar registro del/ });
    const hours = within(form).getByRole('textbox', { name: /^Horas/ });
    expect(hours).toHaveValue('1,5');
    await screen.findByRole('option', { name: /AS-2026-0012 — Due diligence/ });
    await user.clear(hours);
    await user.type(hours, '2,5');
    await user.click(within(form).getByRole('button', { name: 'Guardar cambios' }));

    expect(await screen.findByText(/^Registro actualizado: 2,5 h en AS-2026-0012/)).toBeVisible();
    expect(screen.queryByRole('region', { name: /^Editar registro del/ })).not.toBeInTheDocument();
    await waitFor(() => expect(rowFor('AS-2026-0012')).toHaveTextContent('2,5'));
    expect(bodies).toEqual([
      {
        matterId: matter.id,
        workDate: entry.workDate,
        hours: 2.5,
        description: 'Revisión de contrato de compraventa.',
        version: 3,
      },
    ]);
    expect(screen.getByRole('heading', { level: 1 })).toHaveFocus();
  });

  // Scenario: Registros no editables
  it('APPROVED, REJECTED y PENDING de hace 8 días no muestran "Editar"; el rechazado muestra el motivo', async () => {
    const today = todayInLaPaz();
    const eightDaysAgo = addDays(today, -8);
    const entries = [
      makeTimeEntry(matter, { status: 'APPROVED', description: 'Aprobado' }),
      makeTimeEntry(matter, {
        status: 'REJECTED',
        description: 'Rechazado',
        rejectionReason: 'Descripción insuficiente',
      }),
      makeTimeEntry(matter, {
        description: 'Vencido',
        workDate: eightDaysAgo,
        createdAt: `${eightDaysAgo}T14:00:00Z`,
        editableUntil: addDays(eightDaysAgo, 7),
      }),
      makeTimeEntry(matter, { description: 'Último día', editableUntil: today }),
    ];
    mockFetch({
      'GET /api/me': signIn(abogado),
      'GET /api/time-entries': listHandler(() => entries),
    });
    renderApp('/mis-registros');

    await screen.findByRole('table', { name: 'Mis registros de horas' });
    for (const text of ['Aprobado', 'Rechazado', 'Vencido']) {
      expect(within(rowFor(text)).queryByRole('button', { name: /^Editar/ })).toBeNull();
      expect(rowFor(text)).toHaveTextContent('No editable');
    }
    expect(rowFor('Aprobado')).toHaveTextContent('Aprobada');
    expect(rowFor('Rechazado')).toHaveTextContent('Rechazada');
    expect(rowFor('Rechazado')).toHaveTextContent('Descripción insuficiente');
    // Scenario: Último día de la ventana (editableUntil = hoy en La Paz): sigue editable.
    expect(within(rowFor('Último día')).getByRole('button', { name: /^Editar/ })).toBeVisible();
    expect(screen.getByText('4 registros')).toBeVisible();
  });

  // Scenario: Conflicto de concurrencia al editar
  it.each([
    ['concurrent-modification', 'El registro cambió mientras lo editabas.'],
    ['entry-not-pending', 'El registro ya no está pendiente y no se puede editar.'],
  ])(
    'ante 409 %s avisa en español y recarga la lista con los datos vigentes',
    async (code, text) => {
      const entry = makeTimeEntry(matter, { hours: 1.5, version: 1 });
      let current: TimeEntry = entry;
      let listCalls = 0;
      mockFetch({
        'GET /api/me': signIn(abogado),
        'GET /api/matters': mattersHandler,
        'GET /api/time-entries': (request) => {
          listCalls += 1;
          return listHandler(() => [current])(request);
        },
        [`PUT /api/time-entries/${entry.id}`]: () => {
          // Otro cambio llegó antes: el servidor ya tiene datos nuevos.
          current = {
            ...entry,
            hours: 4,
            version: 2,
            status: code === 'entry-not-pending' ? 'APPROVED' : 'PENDING',
          };
          return problemResponse(problem(409, code, 'Conflict'));
        },
      });
      const user = userEvent.setup();
      renderApp('/mis-registros');

      await screen.findByRole('table', { name: 'Mis registros de horas' });
      await user.click(within(rowFor('AS-2026-0012')).getByRole('button', { name: /^Editar/ }));
      const hours = screen.getByRole('textbox', { name: /^Horas/ });
      await user.clear(hours);
      await user.type(hours, '2');
      await user.click(screen.getByRole('button', { name: 'Guardar cambios' }));

      expect(await screen.findByRole('alert')).toHaveTextContent(text);
      expect(screen.getByRole('alert')).toHaveTextContent('Se recargó la lista');
      expect(document.body).not.toHaveTextContent(code);
      expect(
        screen.queryByRole('region', { name: /^Editar registro del/ }),
      ).not.toBeInTheDocument();
      await waitFor(() => expect(rowFor('AS-2026-0012')).toHaveTextContent('4'));
      expect(listCalls).toBeGreaterThanOrEqual(2);
    },
  );

  it('una regla de negocio en la edición (edit-window-expired) se muestra sin cerrar el formulario', async () => {
    const entry = makeTimeEntry(matter);
    mockFetch({
      'GET /api/me': signIn(abogado),
      'GET /api/matters': mattersHandler,
      'GET /api/time-entries': listHandler(() => [entry]),
      [`PUT /api/time-entries/${entry.id}`]: () =>
        problemResponse(problem(422, 'edit-window-expired', 'Edit window expired')),
    });
    const user = userEvent.setup();
    renderApp('/mis-registros');

    await screen.findByRole('table', { name: 'Mis registros de horas' });
    await user.click(within(rowFor('AS-2026-0012')).getByRole('button', { name: /^Editar/ }));
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Venció el plazo de 7 días para editar este registro.',
    );
    expect(screen.getByRole('region', { name: /^Editar registro del/ })).toBeVisible();
  });

  it('cancelar la edición cierra el formulario sin enviar nada', async () => {
    const entry = makeTimeEntry(matter);
    const { calls } = mockFetch({
      'GET /api/me': signIn(abogado),
      'GET /api/matters': mattersHandler,
      'GET /api/time-entries': listHandler(() => [entry]),
    });
    const user = userEvent.setup();
    renderApp('/mis-registros');

    await screen.findByRole('table', { name: 'Mis registros de horas' });
    await user.click(within(rowFor('AS-2026-0012')).getByRole('button', { name: /^Editar/ }));
    await user.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(screen.queryByRole('region', { name: /^Editar registro del/ })).not.toBeInTheDocument();
    expect(calls.some((c) => c.method === 'PUT')).toBe(false);
  });

  it('filtra por estado y rango de fechas en la URL; un rango invertido no consulta la API', async () => {
    const today = todayInLaPaz();
    const entries = [
      makeTimeEntry(matter, { status: 'APPROVED', description: 'Aprobado' }),
      makeTimeEntry(matter, { description: 'Pendiente viejo', workDate: addDays(today, -20) }),
    ];
    const { calls } = mockFetch({
      'GET /api/me': signIn(abogado),
      'GET /api/time-entries': listHandler(() => entries),
    });
    const user = userEvent.setup();
    const { router } = renderApp('/mis-registros');

    await screen.findByRole('table', { name: 'Mis registros de horas' });
    await user.selectOptions(screen.getByRole('combobox', { name: 'Estado' }), 'Aprobada');
    await waitFor(() => expect(router.state.location.search).toBe('?status=APPROVED'));
    await waitFor(() => expect(screen.getByText('1 registro')).toBeVisible());
    const last = calls.filter((c) => new URL(c.url).pathname === '/api/time-entries').at(-1)!;
    expect(new URL(last.url).searchParams.get('status')).toBe('APPROVED');

    fireEvent.change(screen.getByLabelText('Desde'), { target: { value: today } });
    fireEvent.change(screen.getByLabelText('Hasta'), { target: { value: addDays(today, -1) } });
    expect(
      await screen.findByText('La fecha "Desde" no puede ser posterior a "Hasta".'),
    ).toBeVisible();
    const requested = calls
      .filter((c) => new URL(c.url).pathname === '/api/time-entries')
      .map((c) => new URL(c.url).searchParams);
    expect(requested.some((p) => p.get('from') === today && p.get('to') !== null)).toBe(false);
    expect(screen.queryByRole('table', { name: 'Mis registros de horas' })).not.toBeInTheDocument();
  });

  it('el socio no puede abrir "Mis registros"', async () => {
    mockFetch({ 'GET /api/me': signIn(socia) });
    renderApp('/mis-registros');

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Acceso no permitido' }),
    ).toBeVisible();
  });
});
