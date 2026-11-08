import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import type { Matter, MatterCreate } from '../api/clientsMatters';
import {
  abogado,
  includesIgnoringCase,
  makeClient,
  makeMatter,
  pageOf,
  signIn,
  socia,
} from '../test/fixtures';
import { jsonResponse, mockFetch, problemResponse, renderApp } from '../test/utils';

const andinas = makeClient('Inversiones Andinas S.A.', '0000000001');
const illimani = makeClient('Comercial Illimani');

function seedMatters(): Matter[] {
  return [
    makeMatter('AS-2026-0007', 'Due diligence adquisición planta', andinas, 'OPEN'),
    makeMatter('AS-2026-0009', 'Contrato de suministro', andinas, 'CLOSED'),
    makeMatter('AS-2026-0012', 'Registro de marca', illimani, 'OPEN'),
  ];
}

/** API simulada: clientes fijos y asuntos filtrados por clientId/status/q, orden por código desc. */
function mattersApi(initial: Matter[] = seedMatters()) {
  const store = [...initial];
  let next = 13;
  return {
    clients: (request: Request) => jsonResponse(pageOf([illimani, andinas], new URL(request.url))),
    list: (request: Request) => {
      const url = new URL(request.url);
      const clientId = url.searchParams.get('clientId');
      const status = url.searchParams.get('status');
      const q = url.searchParams.get('q');
      const items = store
        .filter((m) => clientId === null || m.clientId === clientId)
        .filter((m) => status === null || m.status === status)
        .filter((m) => includesIgnoringCase(m.title, q) || includesIgnoringCase(m.code, q))
        .sort((a, b) => b.code.localeCompare(a.code));
      return jsonResponse(pageOf(items, url));
    },
    create: async (request: Request) => {
      const body = (await request.clone().json()) as MatterCreate;
      const client = [andinas, illimani].find((c) => c.id === body.clientId) ?? andinas;
      const matter = makeMatter(`AS-2026-${String(next++).padStart(4, '0')}`, body.title, client);
      store.push(matter);
      return jsonResponse(matter, 201);
    },
  };
}

function rows() {
  return within(screen.getByRole('table', { name: 'Lista de asuntos' }))
    .getAllByRole('row')
    .slice(1);
}

describe('Pantalla Asuntos', () => {
  it('lista código, título, cliente y estado en español', async () => {
    const apiSim = mattersApi();
    mockFetch({
      'GET /api/me': signIn(abogado),
      'GET /api/clients': apiSim.clients,
      'GET /api/matters': apiSim.list,
    });
    renderApp('/asuntos');

    expect(await screen.findByRole('heading', { level: 1, name: 'Asuntos' })).toBeVisible();
    await screen.findByText('Registro de marca');
    const [first, second] = rows();
    expect(first).toHaveTextContent('AS-2026-0012');
    expect(first).toHaveTextContent('Comercial Illimani');
    expect(first).toHaveTextContent('Abierto');
    expect(second).toHaveTextContent('Cerrado');
    expect(within(first!).getByRole('link', { name: 'AS-2026-0012' })).toHaveAttribute(
      'href',
      expect.stringMatching(/^\/asuntos\/a0000000-/),
    );
    expect(screen.getByText('3 asuntos encontrados')).toBeVisible();
  });

  // Scenario: Filtrar asuntos abiertos de un cliente
  it('al elegir el cliente y el estado Abierto muestra solo los asuntos abiertos de ese cliente', async () => {
    const apiSim = mattersApi();
    const { calls } = mockFetch({
      'GET /api/me': signIn(abogado),
      'GET /api/clients': apiSim.clients,
      'GET /api/matters': apiSim.list,
    });
    const user = userEvent.setup();
    const { router } = renderApp('/asuntos');

    await screen.findByText('Registro de marca');
    const clientFilter = screen.getByRole('combobox', { name: 'Cliente' });
    await screen.findByRole('option', { name: 'Inversiones Andinas S.A.' });
    await user.selectOptions(clientFilter, 'Inversiones Andinas S.A.');
    await user.selectOptions(screen.getByRole('combobox', { name: 'Estado' }), 'Abierto');

    await waitFor(() => expect(rows()).toHaveLength(1));
    expect(rows()[0]).toHaveTextContent('AS-2026-0007');
    expect(rows()[0]).toHaveTextContent('Abierto');
    const last = new URL(calls.filter((c) => c.url.includes('/api/matters')).at(-1)!.url);
    expect(last.searchParams.get('clientId')).toBe(andinas.id);
    expect(last.searchParams.get('status')).toBe('OPEN');
    expect(router.state.location.search).toContain('status=OPEN');
  });

  // Scenario: Buscar por código (interfaz)
  it('al escribir AS-2026-0012 muestra solo ese asunto', async () => {
    const apiSim = mattersApi();
    mockFetch({
      'GET /api/me': signIn(socia),
      'GET /api/clients': apiSim.clients,
      'GET /api/matters': apiSim.list,
    });
    const user = userEvent.setup();
    renderApp('/asuntos');

    await screen.findByText('Registro de marca');
    await user.type(
      screen.getByRole('searchbox', { name: 'Buscar por título o código' }),
      'AS-2026-0012',
    );

    await waitFor(() => expect(rows()).toHaveLength(1));
    expect(rows()[0]).toHaveTextContent('AS-2026-0012');
  });

  it('el filtro Estado Cerrado y una búsqueda sin coincidencias muestran el mensaje vacío', async () => {
    const apiSim = mattersApi();
    mockFetch({
      'GET /api/me': signIn(socia),
      'GET /api/clients': apiSim.clients,
      'GET /api/matters': apiSim.list,
    });
    renderApp('/asuntos?status=CLOSED&q=marca');

    expect(await screen.findByText('No se encontraron asuntos')).toBeVisible();
    expect(screen.getByRole('combobox', { name: 'Estado' })).toHaveValue('CLOSED');
  });

  it('ignora un estado inválido en la URL en lugar de enviarlo a la API', async () => {
    const apiSim = mattersApi();
    const { calls } = mockFetch({
      'GET /api/me': signIn(socia),
      'GET /api/clients': apiSim.clients,
      'GET /api/matters': apiSim.list,
    });
    renderApp('/asuntos?status=ARCHIVED&clientId=no-es-uuid');

    await screen.findByText('Registro de marca');
    const matterCalls = calls.filter((c) => c.url.includes('/api/matters'));
    expect(matterCalls.every((c) => !new URL(c.url).searchParams.has('status'))).toBe(true);
    expect(matterCalls.every((c) => !new URL(c.url).searchParams.has('clientId'))).toBe(true);
  });

  it('pagina los asuntos con Siguiente y Anterior', async () => {
    const many = Array.from({ length: 25 }, (_, i) =>
      makeMatter(`AS-2026-${String(i + 1).padStart(4, '0')}`, `Asunto ${i + 1}`, andinas),
    );
    const apiSim = mattersApi(many);
    mockFetch({
      'GET /api/me': signIn(socia),
      'GET /api/clients': apiSim.clients,
      'GET /api/matters': apiSim.list,
    });
    const user = userEvent.setup();
    renderApp('/asuntos');

    expect(await screen.findByText('Página 1 de 2')).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'Siguiente' }));
    expect(await screen.findByText('Página 2 de 2')).toBeVisible();
    await waitFor(() => expect(rows()).toHaveLength(5));
    await user.click(screen.getByRole('button', { name: 'Anterior' }));
    expect(await screen.findByText('Página 1 de 2')).toBeVisible();
  });
});

describe('Formulario Nuevo asunto', () => {
  // Scenario: Socio crea un asunto y ve su código
  it('el Socio crea un asunto y lo ve en la lista con su código y estado Abierto', async () => {
    const apiSim = mattersApi();
    const { calls } = mockFetch({
      'GET /api/me': signIn(socia),
      'GET /api/clients': apiSim.clients,
      'GET /api/matters': apiSim.list,
      'POST /api/matters': apiSim.create,
    });
    const user = userEvent.setup();
    renderApp('/asuntos');

    await screen.findByText('Registro de marca');
    await user.click(screen.getByRole('button', { name: 'Nuevo asunto' }));
    const form = screen.getByRole('region', { name: 'Nuevo asunto' });
    const client = within(form).getByRole('combobox', { name: /Cliente/ });
    expect(client).toHaveFocus();
    await within(form).findByRole('option', { name: 'Comercial Illimani' });
    await user.selectOptions(client, 'Comercial Illimani');
    await user.type(
      within(form).getByRole('textbox', { name: /Título/ }),
      'Constitución de sociedad',
    );
    await user.type(
      within(form).getByRole('textbox', { name: /Descripción/ }),
      'Trámite completo.',
    );
    await user.click(within(form).getByRole('button', { name: 'Guardar asunto' }));

    expect(await screen.findByText('Asunto creado con el código AS-2026-0013.')).toBeVisible();
    await waitFor(() => expect(rows()).toHaveLength(1));
    expect(rows()[0]).toHaveTextContent('AS-2026-0013');
    expect(rows()[0]).toHaveTextContent('Constitución de sociedad');
    expect(rows()[0]).toHaveTextContent('Abierto');
    expect(screen.queryByRole('region', { name: 'Nuevo asunto' })).not.toBeInTheDocument();
    const post = calls.find((c) => c.method === 'POST');
    expect(await post?.json()).toEqual({
      clientId: illimani.id,
      title: 'Constitución de sociedad',
      description: 'Trámite completo.',
    });
  });

  // Scenario: Error de validación en el formulario
  it('al guardar sin título muestra el error junto al campo y no envía la petición', async () => {
    const apiSim = mattersApi();
    const { calls } = mockFetch({
      'GET /api/me': signIn(socia),
      'GET /api/clients': apiSim.clients,
      'GET /api/matters': apiSim.list,
    });
    const user = userEvent.setup();
    renderApp('/asuntos');

    await user.click(await screen.findByRole('button', { name: 'Nuevo asunto' }));
    const form = screen.getByRole('region', { name: 'Nuevo asunto' });
    await within(form).findByRole('option', { name: 'Comercial Illimani' });
    await user.selectOptions(
      within(form).getByRole('combobox', { name: /Cliente/ }),
      'Comercial Illimani',
    );
    await user.click(within(form).getByRole('button', { name: 'Guardar asunto' }));

    const title = within(form).getByRole('textbox', { name: /Título/ });
    expect(title).toHaveAttribute('aria-invalid', 'true');
    expect(title).toHaveAccessibleDescription(/Ingresa el título del asunto\./);
    expect(title).toHaveFocus();
    expect(calls.some((c) => c.method === 'POST')).toBe(false);
  });

  it('valida cliente obligatorio y descripción de más de 2000 caracteres', async () => {
    const apiSim = mattersApi();
    const { calls } = mockFetch({
      'GET /api/me': signIn(socia),
      'GET /api/clients': apiSim.clients,
      'GET /api/matters': apiSim.list,
    });
    const user = userEvent.setup();
    renderApp('/asuntos');

    await user.click(await screen.findByRole('button', { name: 'Nuevo asunto' }));
    const form = screen.getByRole('region', { name: 'Nuevo asunto' });
    await user.type(within(form).getByRole('textbox', { name: /Título/ }), 'Título válido');
    await user.click(within(form).getByRole('textbox', { name: /Descripción/ }));
    await user.paste('d'.repeat(2001));
    await user.click(within(form).getByRole('button', { name: 'Guardar asunto' }));

    expect(within(form).getByRole('combobox', { name: /Cliente/ })).toHaveAccessibleDescription(
      /Selecciona el cliente del asunto\./,
    );
    expect(within(form).getByRole('textbox', { name: /Descripción/ })).toHaveAccessibleDescription(
      /no puede superar 2000 caracteres/,
    );
    expect(calls.some((c) => c.method === 'POST')).toBe(false);
  });

  it('ante 404 not-found del cliente marca el campo Cliente', async () => {
    const apiSim = mattersApi();
    mockFetch({
      'GET /api/me': signIn(socia),
      'GET /api/clients': apiSim.clients,
      'GET /api/matters': apiSim.list,
      'POST /api/matters': () =>
        problemResponse({
          type: 'https://asuntos-horas.example/problems/not-found',
          title: 'Recurso no encontrado',
          status: 404,
          detail: 'El recurso solicitado no existe.',
          code: 'not-found',
          traceId: 'abc',
        }),
    });
    const user = userEvent.setup();
    renderApp('/asuntos');

    await user.click(await screen.findByRole('button', { name: 'Nuevo asunto' }));
    const form = screen.getByRole('region', { name: 'Nuevo asunto' });
    await within(form).findByRole('option', { name: 'Comercial Illimani' });
    const client = within(form).getByRole('combobox', { name: /Cliente/ });
    await user.selectOptions(client, 'Comercial Illimani');
    await user.type(within(form).getByRole('textbox', { name: /Título/ }), 'Registro sanitario');
    await user.click(within(form).getByRole('button', { name: 'Guardar asunto' }));

    await waitFor(() =>
      expect(client).toHaveAccessibleDescription(/El cliente seleccionado ya no existe\./),
    );
  });

  // Scenario: Abogado no ve formularios de alta (Asuntos)
  // Scenario: Abogado no ve la acción de cierre (lista)
  it('el Abogado no ve Nuevo asunto ni Cerrar asunto', async () => {
    const apiSim = mattersApi();
    mockFetch({
      'GET /api/me': signIn(abogado),
      'GET /api/clients': apiSim.clients,
      'GET /api/matters': apiSim.list,
    });
    renderApp('/asuntos');

    await screen.findByText('Registro de marca');
    expect(screen.queryByRole('button', { name: 'Nuevo asunto' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Cerrar asunto/ })).not.toBeInTheDocument();
  });
});
