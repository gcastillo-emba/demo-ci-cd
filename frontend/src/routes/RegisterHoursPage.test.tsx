import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import type { Matter } from '../api/clientsMatters';
import { addDays, todayInLaPaz } from '../api/hours';
import type { TimeEntryCreate } from '../api/timeEntries';
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
const openMatter = makeMatter('AS-2026-0012', 'Due diligence adquisición planta', andinas);
const otherOpen = makeMatter('AS-2026-0007', 'Registro de marca', andinas);
const closedMatter = makeMatter('AS-2026-0003', 'Contrato cerrado', andinas, 'CLOSED');

/** Asuntos simulados filtrados por `status` como la API. */
function mattersHandler(matters: Matter[] = [openMatter, otherOpen, closedMatter]) {
  return (request: Request) => {
    const url = new URL(request.url);
    const status = url.searchParams.get('status');
    return jsonResponse(
      pageOf(
        matters.filter((m) => !status || m.status === status),
        url,
      ),
    );
  };
}

function field(name: string) {
  return screen.getByRole('textbox', { name: new RegExp(`^${name}`) });
}

async function fillForm(
  user: ReturnType<typeof userEvent.setup>,
  values: { matter?: string; date?: string; hours: string; description?: string },
) {
  const matter = await screen.findByRole('option', { name: values.matter ?? /AS-2026-0012/ });
  await user.selectOptions(screen.getByRole('combobox', { name: /^Asunto/ }), matter);
  if (values.date !== undefined) {
    fireEvent.change(screen.getByLabelText(/^Fecha de trabajo/), {
      target: { value: values.date },
    });
  }
  await user.clear(field('Horas'));
  await user.type(field('Horas'), values.hours);
  await user.clear(field('Descripción'));
  await user.type(field('Descripción'), values.description ?? 'Revisión de contrato.');
}

describe('Pantalla Registrar horas', () => {
  // Scenario: Alta con coma decimal
  it('crea un registro de 1,5 h PENDING, confirma en español y limpia el formulario', async () => {
    const bodies: TimeEntryCreate[] = [];
    const { calls } = mockFetch({
      'GET /api/me': signIn(abogado),
      'GET /api/matters': mattersHandler(),
      'POST /api/time-entries': async (request) => {
        const body = (await request.clone().json()) as TimeEntryCreate;
        bodies.push(body);
        const entry = makeTimeEntry(openMatter, { ...body, createdAt: '2026-11-08T14:00:00Z' });
        return jsonResponse(entry, 201);
      },
    });
    const user = userEvent.setup();
    renderApp('/registrar-horas');

    expect(await screen.findByRole('heading', { level: 1, name: 'Registrar horas' })).toBeVisible();
    // Solo asuntos OPEN (design Supuesto 12): la API recibe status=OPEN.
    await screen.findByRole('option', { name: /AS-2026-0012/ });
    expect(screen.queryByRole('option', { name: /AS-2026-0003/ })).not.toBeInTheDocument();
    const mattersCall = calls.find((c) => new URL(c.url).pathname === '/api/matters');
    expect(new URL(mattersCall!.url).searchParams.get('status')).toBe('OPEN');
    // Fecha por defecto: hoy en La Paz.
    const today = todayInLaPaz();
    expect(screen.getByLabelText(/^Fecha de trabajo/)).toHaveValue(today);
    expect(screen.getByLabelText(/^Fecha de trabajo/)).toHaveAttribute('max', today);

    await fillForm(user, { hours: '1,5', description: '  Revisión de contrato.  ' });
    await user.click(screen.getByRole('button', { name: 'Guardar' }));

    expect(await screen.findByRole('status')).toHaveTextContent(
      /Registro guardado: 1,5 h en AS-2026-0012 .* estado Pendiente\./,
    );
    expect(bodies).toEqual([
      {
        matterId: openMatter.id,
        workDate: today,
        hours: 1.5,
        description: 'Revisión de contrato.',
      },
    ]);
    expect(screen.getByRole('combobox', { name: /^Asunto/ })).toHaveValue('');
    expect(field('Horas')).toHaveValue('');
    expect(field('Descripción')).toHaveValue('');
    expect(screen.getByLabelText(/^Fecha de trabajo/)).toHaveValue(today);
  });

  // Scenario: Error de límite diario visible
  it('muestra el límite diario con horas ya cargadas y restantes, y conserva los datos', async () => {
    const yesterday = addDays(todayInLaPaz(), -1);
    mockFetch({
      'GET /api/me': signIn(abogado),
      'GET /api/matters': mattersHandler(),
      'POST /api/time-entries': () =>
        problemResponse(
          problem(422, 'daily-hours-exceeded', 'Se supera el límite diario de 24 horas.', {
            limitHours: 24,
            alreadyLoggedHours: 20,
          }),
        ),
    });
    const user = userEvent.setup();
    renderApp('/registrar-horas');

    await fillForm(user, { date: yesterday, hours: '5', description: 'Audiencia preliminar.' });
    await user.click(screen.getByRole('button', { name: 'Guardar' }));

    const expected =
      'Se supera el límite de 24 horas diarias: ya tienes 20 h registradas ese día y te quedan 4 h.';
    expect(await screen.findByRole('alert')).toHaveTextContent(expected);
    expect(field('Horas')).toHaveAccessibleDescription(expect.stringContaining(expected));
    expect(field('Horas')).toHaveAttribute('aria-invalid', 'true');
    expect(field('Horas')).toHaveFocus();
    // Los datos ingresados se conservan.
    expect(field('Horas')).toHaveValue('5');
    expect(field('Descripción')).toHaveValue('Audiencia preliminar.');
    expect(screen.getByLabelText(/^Fecha de trabajo/)).toHaveValue(yesterday);
    expect(screen.getByRole('combobox', { name: /^Asunto/ })).toHaveValue(openMatter.id);
    expect(document.body).not.toHaveTextContent('daily-hours-exceeded');
  });

  // Scenario: Error de fecha futura
  it('muestra junto al campo fecha que no se registran horas en fechas futuras (422)', async () => {
    mockFetch({
      'GET /api/me': signIn(abogado),
      'GET /api/matters': mattersHandler(),
      'POST /api/time-entries': () =>
        problemResponse(problem(422, 'future-date', 'Future date not allowed')),
    });
    const user = userEvent.setup();
    renderApp('/registrar-horas');

    await fillForm(user, { hours: '2' });
    await user.click(screen.getByRole('button', { name: 'Guardar' }));

    const date = screen.getByLabelText(/^Fecha de trabajo/);
    await waitFor(() =>
      expect(date).toHaveAccessibleDescription(
        expect.stringContaining('No se pueden registrar horas en fechas futuras.'),
      ),
    );
    expect(document.body).not.toHaveTextContent('future-date');
    expect(document.body).not.toHaveTextContent('Future date not allowed');
  });

  it('valida en el cliente una fecha futura sin llamar a la API', async () => {
    const { calls } = mockFetch({
      'GET /api/me': signIn(abogado),
      'GET /api/matters': mattersHandler(),
    });
    const user = userEvent.setup();
    renderApp('/registrar-horas');

    await fillForm(user, { date: addDays(todayInLaPaz(), 1), hours: '2' });
    await user.click(screen.getByRole('button', { name: 'Guardar' }));

    expect(screen.getByLabelText(/^Fecha de trabajo/)).toHaveAccessibleDescription(
      expect.stringContaining('No se pueden registrar horas en fechas futuras.'),
    );
    expect(calls.some((c) => c.method === 'POST')).toBe(false);
  });

  // Scenario: Error de incremento de horas
  it('"1,25" muestra junto a horas el mensaje de incrementos de 0,1 sin el slug', async () => {
    const { calls } = mockFetch({
      'GET /api/me': signIn(abogado),
      'GET /api/matters': mattersHandler(),
    });
    const user = userEvent.setup();
    renderApp('/registrar-horas');

    await fillForm(user, { hours: '1,25' });
    await user.click(screen.getByRole('button', { name: 'Guardar' }));

    expect(field('Horas')).toHaveAccessibleDescription(
      expect.stringContaining('Las horas deben ir en incrementos de 0,1'),
    );
    expect(field('Horas')).toHaveFocus();
    expect(document.body).not.toHaveTextContent('hours-invalid-increment');
    expect(calls.some((c) => c.method === 'POST')).toBe(false);
  });

  it('si el servidor responde hours-invalid-increment lo muestra junto a horas en español', async () => {
    mockFetch({
      'GET /api/me': signIn(abogado),
      'GET /api/matters': mattersHandler(),
      'POST /api/time-entries': () =>
        problemResponse(problem(422, 'hours-invalid-increment', 'Invalid increment')),
    });
    const user = userEvent.setup();
    renderApp('/registrar-horas');

    await fillForm(user, { hours: '1,5' });
    await user.click(screen.getByRole('button', { name: 'Guardar' }));

    await waitFor(() =>
      expect(field('Horas')).toHaveAccessibleDescription(
        expect.stringContaining('Las horas deben ir en incrementos de 0,1'),
      ),
    );
    expect(document.body).not.toHaveTextContent('hours-invalid-increment');
  });

  it('valida campos obligatorios y rango de horas antes de enviar', async () => {
    const { calls } = mockFetch({
      'GET /api/me': signIn(abogado),
      'GET /api/matters': mattersHandler(),
    });
    const user = userEvent.setup();
    renderApp('/registrar-horas');
    await screen.findByRole('option', { name: /AS-2026-0012/ });

    await user.click(screen.getByRole('button', { name: 'Guardar' }));
    expect(screen.getByRole('combobox', { name: /^Asunto/ })).toHaveAccessibleDescription(
      'Selecciona el asunto.',
    );
    expect(screen.getByRole('combobox', { name: /^Asunto/ })).toHaveFocus();
    expect(field('Descripción')).toHaveAccessibleDescription(
      expect.stringContaining('Describe el trabajo realizado.'),
    );

    await user.type(field('Horas'), '24,5');
    await user.click(screen.getByRole('button', { name: 'Guardar' }));
    expect(field('Horas')).toHaveAccessibleDescription(
      expect.stringContaining('mayores que 0 y no superar 24'),
    );
    expect(calls.some((c) => c.method === 'POST')).toBe(false);
  });

  it('asunto cerrado (422 matter-closed) se muestra junto al asunto', async () => {
    mockFetch({
      'GET /api/me': signIn(abogado),
      'GET /api/matters': mattersHandler(),
      'POST /api/time-entries': () =>
        problemResponse(problem(422, 'matter-closed', 'Matter closed')),
    });
    const user = userEvent.setup();
    renderApp('/registrar-horas');

    await fillForm(user, { hours: '1' });
    await user.click(screen.getByRole('button', { name: 'Guardar' }));

    await waitFor(() =>
      expect(screen.getByRole('combobox', { name: /^Asunto/ })).toHaveAccessibleDescription(
        'El asunto está cerrado: no admite registros de horas.',
      ),
    );
  });

  it('un error 500 muestra un mensaje genérico sin detalles internos', async () => {
    mockFetch({
      'GET /api/me': signIn(abogado),
      'GET /api/matters': mattersHandler(),
      'POST /api/time-entries': () =>
        problemResponse(
          problem(500, 'internal-error', 'org.postgresql.util.PSQLException: relation'),
        ),
    });
    const user = userEvent.setup();
    renderApp('/registrar-horas');

    await fillForm(user, { hours: '1' });
    await user.click(screen.getByRole('button', { name: 'Guardar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/Ocurrió un error inesperado/);
    expect(document.body).not.toHaveTextContent(/PSQLException|postgresql/);
  });

  // Scenario: Socio no ve la opción
  it('el socio no ve "Registrar horas" ni "Mis registros" y la ruta le niega el acceso', async () => {
    const { calls } = mockFetch({
      'GET /api/me': signIn(socia),
    });
    renderApp('/registrar-horas');

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Acceso no permitido' }),
    ).toBeVisible();
    expect(screen.getByRole('alert')).toHaveTextContent(
      'Tu rol no permite acceder a esta pantalla.',
    );
    const nav = screen.getByRole('navigation', { name: 'Principal' });
    expect(within(nav).queryByRole('link', { name: 'Registrar horas' })).not.toBeInTheDocument();
    expect(within(nav).queryByRole('link', { name: 'Mis registros' })).not.toBeInTheDocument();
    expect(within(nav).getByRole('link', { name: 'Aprobaciones' })).toBeVisible();
    expect(calls.some((c) => new URL(c.url).pathname === '/api/matters')).toBe(false);
  });

  it('el abogado ve "Registrar horas" y "Mis registros" en el menú', async () => {
    mockFetch({ 'GET /api/me': signIn(abogado) });
    renderApp('/');

    const nav = await screen.findByRole('navigation', { name: 'Principal' });
    expect(within(nav).getByRole('link', { name: 'Registrar horas' })).toHaveAttribute(
      'href',
      '/registrar-horas',
    );
    expect(within(nav).getByRole('link', { name: 'Mis registros' })).toHaveAttribute(
      'href',
      '/mis-registros',
    );
  });
});
