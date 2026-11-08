import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import type { Client, ClientCreate } from '../api/clientsMatters';
import { GENERIC_ERROR_DETAIL } from '../api/problem';
import {
  abogado,
  includesIgnoringCase,
  internalErrorProblem,
  makeClient,
  pageOf,
  signIn,
  socia,
} from '../test/fixtures';
import { jsonResponse, mockFetch, problemResponse, renderApp } from '../test/utils';

/** API simulada de clientes: filtra por `q`, ordena por nombre y pagina. */
function clientsApi(initial: Client[]) {
  const store = [...initial];
  return {
    store,
    list: (request: Request) => {
      const url = new URL(request.url);
      const q = url.searchParams.get('q');
      const items = store
        .filter((c) => includesIgnoringCase(c.name, q))
        .sort((a, b) => a.name.localeCompare(b.name));
      return jsonResponse(pageOf(items, url));
    },
    create: async (request: Request) => {
      const body = (await request.clone().json()) as ClientCreate;
      const client = makeClient(body.name, body.taxId ?? null);
      store.push(client);
      return jsonResponse(client, 201);
    },
  };
}

function table() {
  return screen.getByRole('table', { name: 'Lista de clientes' });
}

describe('Pantalla Clientes', () => {
  // Scenario: Buscar un cliente
  it('el Abogado busca "andin" y ve solo Inversiones Andinas S.A.', async () => {
    const apiSim = clientsApi([
      makeClient('Inversiones Andinas S.A.', '0000000001'),
      makeClient('Comercial Illimani'),
    ]);
    const { calls } = mockFetch({
      'GET /api/me': signIn(abogado),
      'GET /api/clients': apiSim.list,
    });
    const user = userEvent.setup();
    renderApp('/clientes');

    expect(await screen.findByRole('heading', { level: 1, name: 'Clientes' })).toBeVisible();
    expect(await screen.findByText('Comercial Illimani')).toBeVisible();

    await user.type(screen.getByRole('searchbox', { name: 'Buscar por nombre' }), 'andin');

    await waitFor(() => expect(screen.queryByText('Comercial Illimani')).not.toBeInTheDocument());
    expect(
      within(table()).getByRole('rowheader', { name: 'Inversiones Andinas S.A.' }),
    ).toBeVisible();
    expect(screen.getByText('1 cliente encontrado')).toBeVisible();
    // Debounce: una sola consulta con el texto completo, no una por tecla.
    const searches = calls.filter((c) => new URL(c.url).searchParams.has('q'));
    expect(searches.map((c) => new URL(c.url).searchParams.get('q'))).toEqual(['andin']);
  });

  // Scenario: Navegar a la página siguiente
  it('con 25 clientes, Siguiente muestra los clientes 21 a 25 y "Página 2 de 2"', async () => {
    const many = Array.from({ length: 25 }, (_, i) =>
      makeClient(`Cliente ${String(i + 1).padStart(2, '0')}`),
    );
    mockFetch({ 'GET /api/me': signIn(abogado), 'GET /api/clients': clientsApi(many).list });
    const user = userEvent.setup();
    const { router } = renderApp('/clientes');

    expect(await screen.findByText('Cliente 01')).toBeVisible();
    expect(screen.getByText('Página 1 de 2')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Anterior' })).toBeDisabled();

    await user.click(screen.getByRole('button', { name: 'Siguiente' }));

    expect(await screen.findByText('Página 2 de 2')).toBeVisible();
    expect(await screen.findByText('Cliente 21')).toBeVisible();
    expect(screen.getByText('Cliente 25')).toBeVisible();
    expect(screen.queryByText('Cliente 20')).not.toBeInTheDocument();
    expect(within(table()).getAllByRole('row')).toHaveLength(1 + 5);
    expect(screen.getByRole('button', { name: 'Siguiente' })).toBeDisabled();
    expect(router.state.location.search).toBe('?page=1');
  });

  // Scenario: Búsqueda sin resultados
  it('una búsqueda sin coincidencias muestra "No se encontraron clientes"', async () => {
    const apiSim = clientsApi([makeClient('Comercial Illimani')]);
    mockFetch({ 'GET /api/me': signIn(socia), 'GET /api/clients': apiSim.list });
    const user = userEvent.setup();
    renderApp('/clientes');

    await screen.findByText('Comercial Illimani');
    await user.type(screen.getByRole('searchbox', { name: 'Buscar por nombre' }), 'zzz');

    expect(await screen.findByText('No se encontraron clientes')).toBeVisible();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(
      screen.queryByRole('navigation', { name: 'Paginación de clientes' }),
    ).not.toBeInTheDocument();
  });

  it('los filtros de la URL se aplican al cargar', async () => {
    const apiSim = clientsApi([
      makeClient('Inversiones Andinas S.A.'),
      makeClient('Comercial Illimani'),
    ]);
    mockFetch({ 'GET /api/me': signIn(socia), 'GET /api/clients': apiSim.list });
    renderApp('/clientes?q=illimani');

    expect(await screen.findByText('Comercial Illimani')).toBeVisible();
    expect(screen.getByRole('searchbox', { name: 'Buscar por nombre' })).toHaveValue('illimani');
    expect(screen.queryByText('Inversiones Andinas S.A.')).not.toBeInTheDocument();
  });

  it('un error inesperado muestra un mensaje genérico sin detalles internos', async () => {
    mockFetch({
      'GET /api/me': signIn(socia),
      'GET /api/clients': () => problemResponse(internalErrorProblem),
    });
    renderApp('/clientes');

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(GENERIC_ERROR_DETAIL);
    expect(alert).not.toHaveTextContent(/PSQLException|relation|org\./);
  });
});

describe('Formulario Nuevo cliente', () => {
  // Scenario: Socio crea un cliente desde la pantalla
  it('el Socio crea "Comercial Illimani": el formulario se cierra y el cliente aparece en la lista', async () => {
    const apiSim = clientsApi([makeClient('Inversiones Andinas S.A.')]);
    const { calls } = mockFetch({
      'GET /api/me': signIn(socia),
      'GET /api/clients': apiSim.list,
      'POST /api/clients': apiSim.create,
    });
    const user = userEvent.setup();
    renderApp('/clientes');

    const open = await screen.findByRole('button', { name: 'Nuevo cliente' });
    expect(open).toHaveAttribute('aria-expanded', 'false');
    await user.click(open);
    expect(open).toHaveAttribute('aria-expanded', 'true');
    const name = screen.getByRole('textbox', { name: /Nombre/ });
    expect(name).toHaveFocus();
    await user.type(name, '  Comercial Illimani  ');
    await user.click(screen.getByRole('button', { name: 'Guardar cliente' }));

    expect(await screen.findByText('Cliente creado: Comercial Illimani.')).toBeVisible();
    expect(screen.queryByRole('heading', { name: 'Nuevo cliente' })).not.toBeInTheDocument();
    expect(
      await within(table()).findByRole('rowheader', { name: 'Comercial Illimani' }),
    ).toBeVisible();
    const post = calls.find((c) => c.method === 'POST');
    expect(await post?.json()).toEqual({ name: 'Comercial Illimani' });
  });

  it('valida en el cliente el nombre vacío y no envía la petición', async () => {
    const { calls } = mockFetch({
      'GET /api/me': signIn(socia),
      'GET /api/clients': clientsApi([]).list,
    });
    const user = userEvent.setup();
    renderApp('/clientes');

    await user.click(await screen.findByRole('button', { name: 'Nuevo cliente' }));
    await user.type(screen.getByRole('textbox', { name: /Identificación tributaria/ }), '   ');
    await user.click(screen.getByRole('button', { name: 'Guardar cliente' }));

    const name = screen.getByRole('textbox', { name: /Nombre/ });
    expect(name).toHaveAttribute('aria-invalid', 'true');
    expect(name).toHaveAccessibleDescription(/Ingresa el nombre del cliente\./);
    expect(name).toHaveFocus();
    expect(
      screen.getByRole('textbox', { name: /Identificación tributaria/ }),
    ).toHaveAccessibleDescription(/no puede quedar en blanco/);
    expect(calls.some((c) => c.method === 'POST')).toBe(false);
  });

  it('valida el límite de 200 caracteres del nombre', async () => {
    const { calls } = mockFetch({
      'GET /api/me': signIn(socia),
      'GET /api/clients': clientsApi([]).list,
    });
    const user = userEvent.setup();
    renderApp('/clientes');

    await user.click(await screen.findByRole('button', { name: 'Nuevo cliente' }));
    const name = screen.getByRole('textbox', { name: /Nombre/ });
    await user.click(name);
    await user.paste('x'.repeat(201));
    await user.click(screen.getByRole('button', { name: 'Guardar cliente' }));

    expect(name).toHaveAccessibleDescription(/no puede superar 200 caracteres/);
    expect(calls.some((c) => c.method === 'POST')).toBe(false);
  });

  // Scenario: Identificación tributaria duplicada en el formulario
  it('ante 409 client-tax-id-taken el formulario sigue abierto con el mensaje junto al campo', async () => {
    mockFetch({
      'GET /api/me': signIn(socia),
      'GET /api/clients': clientsApi([makeClient('Inversiones Andinas S.A.', '0000000001')]).list,
      'POST /api/clients': () =>
        problemResponse({
          type: 'https://asuntos-horas.example/problems/client-tax-id-taken',
          title: 'Identificación tributaria ya registrada',
          status: 409,
          detail: 'Ya existe un cliente con la identificación tributaria indicada.',
          code: 'client-tax-id-taken',
          traceId: 'abc',
        }),
    });
    const user = userEvent.setup();
    renderApp('/clientes');

    await user.click(await screen.findByRole('button', { name: 'Nuevo cliente' }));
    await user.type(screen.getByRole('textbox', { name: /Nombre/ }), 'Otra Empresa S.R.L.');
    const taxId = screen.getByRole('textbox', { name: /Identificación tributaria/ });
    await user.type(taxId, '0000000001');
    await user.click(screen.getByRole('button', { name: 'Guardar cliente' }));

    await waitFor(() =>
      expect(taxId).toHaveAccessibleDescription(
        /Ya existe un cliente con esa identificación tributaria\./,
      ),
    );
    expect(taxId).toHaveAttribute('aria-invalid', 'true');
    expect(taxId).toHaveFocus();
    expect(screen.getByRole('heading', { name: 'Nuevo cliente' })).toBeVisible();
  });

  it('muestra los errores por campo que informa la API (400 validation-error)', async () => {
    mockFetch({
      'GET /api/me': signIn(socia),
      'GET /api/clients': clientsApi([]).list,
      'POST /api/clients': () =>
        problemResponse({
          type: 'https://asuntos-horas.example/problems/validation-error',
          title: 'Solicitud inválida',
          status: 400,
          detail: 'La solicitud tiene campos inválidos.',
          code: 'validation-error',
          traceId: 'abc',
          errors: [{ field: 'name', message: 'El nombre es obligatorio.' }],
        }),
    });
    const user = userEvent.setup();
    renderApp('/clientes');

    await user.click(await screen.findByRole('button', { name: 'Nuevo cliente' }));
    await user.type(screen.getByRole('textbox', { name: /Nombre/ }), 'Comercial Illimani');
    await user.click(screen.getByRole('button', { name: 'Guardar cliente' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Revisa los campos marcados');
    expect(screen.getByRole('textbox', { name: /Nombre/ })).toHaveAccessibleDescription(
      /El nombre es obligatorio\./,
    );
  });

  it('ante 403 forbidden muestra el mensaje de rol en español', async () => {
    mockFetch({
      'GET /api/me': signIn(socia),
      'GET /api/clients': clientsApi([]).list,
      'POST /api/clients': () =>
        problemResponse({
          type: 'https://asuntos-horas.example/problems/forbidden',
          title: 'Acción no permitida para su rol',
          status: 403,
          detail: 'Acción no permitida para su rol.',
          code: 'forbidden',
          traceId: 'abc',
        }),
    });
    const user = userEvent.setup();
    renderApp('/clientes');

    await user.click(await screen.findByRole('button', { name: 'Nuevo cliente' }));
    await user.type(screen.getByRole('textbox', { name: /Nombre/ }), 'Comercial Illimani');
    await user.click(screen.getByRole('button', { name: 'Guardar cliente' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Tu rol no permite realizar esta acción.',
    );
  });

  it('Cancelar cierra el formulario sin enviar nada', async () => {
    const { calls } = mockFetch({
      'GET /api/me': signIn(socia),
      'GET /api/clients': clientsApi([]).list,
    });
    const user = userEvent.setup();
    renderApp('/clientes');

    await user.click(await screen.findByRole('button', { name: 'Nuevo cliente' }));
    await user.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(screen.queryByRole('heading', { name: 'Nuevo cliente' })).not.toBeInTheDocument();
    expect(calls.some((c) => c.method === 'POST')).toBe(false);
  });

  // Scenario: Abogado no ve formularios de alta (Clientes)
  it('el Abogado no ve el botón Nuevo cliente', async () => {
    mockFetch({
      'GET /api/me': signIn(abogado),
      'GET /api/clients': clientsApi([makeClient('Comercial Illimani')]).list,
    });
    renderApp('/clientes');

    expect(await screen.findByText('Comercial Illimani')).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Nuevo cliente' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Clientes' })).toHaveAttribute('aria-current', 'page');
  });
});
