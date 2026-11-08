import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import type { DashboardSummary } from '../api/dashboard';
import { TOKEN_KEY } from '../api/token';
import { abogado, internalErrorProblem, problem, signIn, socia } from '../test/fixtures';
import { jsonResponse, mockFetch, problemResponse, renderApp } from '../test/utils';

// Datos 100 % sintéticos. Los totales son consistentes con las filas (spec "Totales del período").
const partnerSummary: DashboardSummary = {
  from: '2026-10-09',
  to: '2026-11-07',
  totals: { approvedHours: 3.5, pendingHours: 4.5 },
  byMatter: [
    {
      matterId: 'a0000000-0000-4000-8000-000000000001',
      matterCode: 'AS-2026-0001',
      matterTitle: 'Constitución de sociedad ficticia',
      approvedHours: 3.5,
      pendingHours: 0.5,
    },
    {
      matterId: 'a0000000-0000-4000-8000-000000000002',
      matterCode: 'AS-2026-0002',
      matterTitle: 'Revisión de contrato de suministro',
      approvedHours: 0,
      pendingHours: 4,
    },
  ],
  byLawyer: [
    {
      lawyerId: '00000000-0000-4000-8000-000000000003',
      lawyerName: 'Abogada Dos',
      approvedHours: 3.5,
      pendingHours: 0.5,
    },
    {
      lawyerId: '00000000-0000-4000-8000-000000000004',
      lawyerName: 'Abogado Tres',
      approvedHours: 0,
      pendingHours: 4,
    },
  ],
};

const lawyerSummary: DashboardSummary = {
  from: '2026-10-09',
  to: '2026-11-07',
  totals: { approvedHours: 3, pendingHours: 0 },
  byMatter: [
    {
      matterId: 'a0000000-0000-4000-8000-000000000001',
      matterCode: 'AS-2026-0001',
      matterTitle: 'Constitución de sociedad ficticia',
      approvedHours: 3,
      pendingHours: 0,
    },
  ],
  byLawyer: [
    { lawyerId: abogado.id, lawyerName: abogado.fullName, approvedHours: 3, pendingHours: 0 },
  ],
};

const emptyPartnerSummary: DashboardSummary = {
  from: '2026-10-09',
  to: '2026-11-07',
  totals: { approvedHours: 0, pendingHours: 0 },
  byMatter: [],
  byLawyer: [],
};

const emptyLawyerSummary: DashboardSummary = {
  ...emptyPartnerSummary,
  byLawyer: [
    { lawyerId: abogado.id, lawyerName: abogado.fullName, approvedHours: 0, pendingHours: 0 },
  ],
};

function summaryHandler(summary: DashboardSummary) {
  return () => jsonResponse(summary);
}

function table(name: string) {
  return screen.getByRole('table', { name });
}

function rowFor(tableName: string, text: string) {
  const row = within(table(tableName))
    .getAllByRole('row')
    .find((r) => r.textContent?.includes(text));
  if (!row) throw new Error(`Fila no encontrada: ${text}`);
  return row;
}

function segmentWidth(row: HTMLElement, segment: 'approved' | 'pending'): number {
  const element = row.querySelector<HTMLElement>(`[data-segment="${segment}"]`);
  if (!element) throw new Error(`Segmento no encontrado: ${segment}`);
  return Number.parseFloat(element.style.width);
}

describe('Pantalla Tablero', () => {
  // Scenario: El socio abre el Tablero
  it('el socio entra desde la navegación y ve título, período de 30 días y totales, sin campos de fecha', async () => {
    const { calls } = mockFetch({
      'GET /api/me': signIn(socia),
      'GET /api/dashboard/summary': summaryHandler(partnerSummary),
    });
    const user = userEvent.setup();
    const { container, router } = renderApp('/');

    const nav = await screen.findByRole('navigation', { name: 'Principal' });
    await user.click(within(nav).getByRole('link', { name: 'Tablero' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Tablero' })).toBeVisible();
    expect(router.state.location.pathname).toBe('/tablero');
    expect(await screen.findByText('Últimos 30 días: 09/10/2026 – 07/11/2026')).toBeVisible();
    const totals = screen.getByRole('region', { name: 'Totales del período' });
    expect(within(totals).getByText('Aprobadas: 3,5 h')).toBeVisible();
    expect(within(totals).getByText('Pendientes: 4,5 h')).toBeVisible();
    // Sin controles para cambiar el rango.
    expect(container.querySelectorAll('input, select')).toHaveLength(0);
    expect(document.title).toBe('Tablero — Asuntos & Horas');
    // La ventana la decide el servidor: la petición no lleva from/to.
    const request = calls.find((c) => new URL(c.url).pathname === '/api/dashboard/summary');
    expect(request).toBeDefined();
    expect(new URL(request!.url).search).toBe('');
    expect(request!.headers.get('Authorization')).toBe('Bearer jwt-de-prueba');
    // El socio ve a varios abogados.
    expect(within(table('Horas por abogado')).getAllByRole('row')).toHaveLength(3);
  });

  // Scenario: El abogado ve solo su fila
  it('la tabla por abogado muestra una sola fila con el nombre del abogado y ningún otro nombre', async () => {
    mockFetch({
      'GET /api/me': signIn(abogado),
      'GET /api/dashboard/summary': summaryHandler(lawyerSummary),
    });
    renderApp('/tablero');

    const lawyers = await screen.findByRole('table', { name: 'Horas por abogado' });
    const rows = within(lawyers).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(1);
    expect(within(rows[0]!).getByRole('rowheader')).toHaveTextContent('Abogado Uno');
    expect(document.body).not.toHaveTextContent('Abogada Dos');
    expect(document.body).not.toHaveTextContent('Abogado Tres');
    expect(screen.getByText('Aprobadas: 3 h')).toBeVisible();
  });

  // Scenario: Sesión expirada en el Tablero
  it('un 401 del resumen lleva al inicio de sesión sin mostrar datos del tablero', async () => {
    mockFetch({
      'GET /api/me': signIn(socia),
      'GET /api/dashboard/summary': () =>
        problemResponse(problem(401, 'unauthenticated', 'Debes iniciar sesión.')),
    });
    const { router } = renderApp('/tablero');

    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeVisible();
    expect(router.state.location.pathname).toBe('/login');
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(document.body).not.toHaveTextContent('Últimos 30 días');
  });

  // Scenario: Error del servidor al cargar
  it('un 500 muestra un error en español con role="alert", sin detalles internos ni tablas', async () => {
    mockFetch({
      'GET /api/me': signIn(socia),
      'GET /api/dashboard/summary': () => problemResponse(internalErrorProblem),
    });
    renderApp('/tablero');

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('No se pudo cargar el tablero');
    expect(alert).toHaveTextContent('Ocurrió un error inesperado.');
    expect(document.body).not.toHaveTextContent('PSQLException');
    expect(document.body).not.toHaveTextContent('internal-error');
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(screen.queryByText(/^Últimos 30 días/)).not.toBeInTheDocument();
  });

  it('un error distinto de 401 (400) también se muestra como alerta en español', async () => {
    mockFetch({
      'GET /api/me': signIn(socia),
      'GET /api/dashboard/summary': () =>
        problemResponse(problem(400, 'validation-error', 'Rango inválido.')),
    });
    renderApp('/tablero');

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Hay datos inválidos. Revisa los campos marcados.',
    );
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('mientras carga anuncia "Cargando tablero…" y marca la sección como ocupada', async () => {
    let release: (response: Response) => void = () => undefined;
    mockFetch({
      'GET /api/me': signIn(socia),
      'GET /api/dashboard/summary': () =>
        new Promise<Response>((resolve) => {
          release = resolve;
        }),
    });
    renderApp('/tablero');

    expect(await screen.findByText('Cargando tablero…')).toHaveAttribute('role', 'status');
    expect(screen.getByRole('region', { name: 'Tablero' })).toHaveAttribute('aria-busy', 'true');
    release(jsonResponse(partnerSummary));
    expect(await screen.findByRole('table', { name: 'Horas por asunto' })).toBeVisible();
    expect(screen.queryByText('Cargando tablero…')).not.toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Tablero' })).not.toHaveAttribute('aria-busy');
  });
});

describe('Navegación del Tablero', () => {
  it.each([
    ['socio', socia],
    ['abogado', abogado],
  ] as const)('el %s ve el enlace "Tablero" en la navegación principal', async (_, who) => {
    mockFetch({ 'GET /api/me': signIn(who) });
    renderApp('/');

    const nav = await screen.findByRole('navigation', { name: 'Principal' });
    expect(within(nav).getByRole('link', { name: 'Tablero' })).toHaveAttribute('href', '/tablero');
  });
});

describe('Tablas accesibles por asunto y por abogado', () => {
  // Scenario: Estructura accesible de la tabla por asunto
  it('la tabla por asunto tiene caption, encabezados scope="col" y el asunto como th scope="row"', async () => {
    mockFetch({
      'GET /api/me': signIn(socia),
      'GET /api/dashboard/summary': summaryHandler(partnerSummary),
    });
    renderApp('/tablero');

    const matters = await screen.findByRole('table', { name: 'Horas por asunto' });
    const headers = within(matters).getAllByRole('columnheader');
    expect(headers.map((h) => h.textContent)).toEqual([
      'Asunto',
      'Aprobadas',
      'Pendientes',
      'Distribución',
    ]);
    headers.forEach((h) => expect(h).toHaveAttribute('scope', 'col'));
    const rowHeaders = within(matters).getAllByRole('rowheader');
    expect(rowHeaders.map((h) => h.textContent)).toEqual([
      'AS-2026-0001 — Constitución de sociedad ficticia',
      'AS-2026-0002 — Revisión de contrato de suministro',
    ]);
    rowHeaders.forEach((h) => expect(h).toHaveAttribute('scope', 'row'));
    // Cada valor queda en su fila y columna como texto numérico.
    const cells = within(rowFor('Horas por asunto', 'AS-2026-0001')).getAllByRole('cell');
    expect(cells[0]).toHaveTextContent('3,5 h');
    expect(cells[1]).toHaveTextContent('0,5 h');
  });

  // Scenario: Estructura accesible de la tabla por abogado
  it('la tabla por abogado tiene caption, encabezados scope="col" y el nombre como th scope="row"', async () => {
    mockFetch({
      'GET /api/me': signIn(socia),
      'GET /api/dashboard/summary': summaryHandler(partnerSummary),
    });
    renderApp('/tablero');

    const lawyers = await screen.findByRole('table', { name: 'Horas por abogado' });
    within(lawyers)
      .getAllByRole('columnheader')
      .forEach((h) => expect(h).toHaveAttribute('scope', 'col'));
    expect(within(lawyers).getAllByRole('columnheader')[0]).toHaveTextContent('Abogado');
    const rowHeaders = within(lawyers).getAllByRole('rowheader');
    expect(rowHeaders.map((h) => h.textContent)).toEqual(['Abogada Dos', 'Abogado Tres']);
    rowHeaders.forEach((h) => expect(h).toHaveAttribute('scope', 'row'));
  });
});

describe('Barras de horas distinguibles sin depender del color', () => {
  // Scenario: Diferencia entre aprobadas y pendientes visible en escala de grises
  it('3,5 h aprobadas y 0,5 h pendientes: segmento aprobado 7 veces más largo y texto visible', async () => {
    mockFetch({
      'GET /api/me': signIn(socia),
      'GET /api/dashboard/summary': summaryHandler(partnerSummary),
    });
    renderApp('/tablero');

    await screen.findByRole('table', { name: 'Horas por asunto' });
    const row = rowFor('Horas por asunto', 'AS-2026-0001');
    const approved = segmentWidth(row, 'approved');
    const pending = segmentWidth(row, 'pending');
    expect(approved / pending).toBeCloseTo(7);
    expect(row).toHaveTextContent('Aprobadas: 3,5 h · Pendientes: 0,5 h');
    // La barra es decorativa: el texto es la fuente de verdad para tecnologías de asistencia.
    expect(within(row).getByTestId('hours-bar')).toHaveAttribute('aria-hidden', 'true');
    // Proporcional al máximo de la tabla (4 h): 3,5 h → 87,5 %.
    expect(approved).toBeCloseTo(87.5);
    // El patrón (sólido vs rayado) acompaña al color.
    expect(row.querySelector('[data-segment="pending"]')).toHaveClass(
      'hours-bar__segment--pending',
    );
  });

  // Scenario: Categoría en cero
  it('una fila con 4 h pendientes y ninguna aprobada muestra "Aprobadas: 0 h" y "Pendientes: 4 h"', async () => {
    mockFetch({
      'GET /api/me': signIn(socia),
      'GET /api/dashboard/summary': summaryHandler(partnerSummary),
    });
    renderApp('/tablero');

    await screen.findByRole('table', { name: 'Horas por asunto' });
    const row = rowFor('Horas por asunto', 'AS-2026-0002');
    expect(row).toHaveTextContent('Aprobadas: 0 h · Pendientes: 4 h');
    expect(segmentWidth(row, 'approved')).toBe(0);
    expect(segmentWidth(row, 'pending')).toBe(100);
  });
});

describe('Estado vacío del Tablero', () => {
  // Scenario: Tablero sin datos
  it('sin horas en el período muestra el mensaje, los totales en cero y ninguna tabla vacía', async () => {
    mockFetch({
      'GET /api/me': signIn(socia),
      'GET /api/dashboard/summary': summaryHandler(emptyPartnerSummary),
    });
    renderApp('/tablero');

    expect(
      await screen.findByText('No hay horas registradas en los últimos 30 días.'),
    ).toBeVisible();
    expect(screen.getByText('Aprobadas: 0 h')).toBeVisible();
    expect(screen.getByText('Pendientes: 0 h')).toBeVisible();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  // Scenario: Abogado sin horas en el Tablero
  it('el abogado sin horas ve el mensaje y su única fila en cero en "Horas por abogado"', async () => {
    mockFetch({
      'GET /api/me': signIn(abogado),
      'GET /api/dashboard/summary': summaryHandler(emptyLawyerSummary),
    });
    renderApp('/tablero');

    expect(
      await screen.findByText('No hay horas registradas en los últimos 30 días.'),
    ).toBeVisible();
    expect(screen.queryByRole('table', { name: 'Horas por asunto' })).not.toBeInTheDocument();
    const lawyers = screen.getByRole('table', { name: 'Horas por abogado' });
    const rows = within(lawyers).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(1);
    expect(rows[0]).toHaveTextContent('Abogado Uno');
    expect(rows[0]).toHaveTextContent('Aprobadas: 0 h · Pendientes: 0 h');
    // Máximo 0: ambos segmentos con ancho 0, queda solo el texto.
    expect(segmentWidth(rows[0]!, 'approved')).toBe(0);
    expect(segmentWidth(rows[0]!, 'pending')).toBe(0);
  });
});
