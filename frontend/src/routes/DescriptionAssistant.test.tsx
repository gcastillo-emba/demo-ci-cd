import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import type { DescriptionSuggestionRequest } from '../api/assistant';
import type { TimeEntryCreate } from '../api/timeEntries';
import {
  abogado,
  internalErrorProblem,
  makeClient,
  makeMatter,
  makeTimeEntry,
  pageOf,
  problem,
  signIn,
} from '../test/fixtures';
import { jsonResponse, mockFetch, problemResponse, renderApp } from '../test/utils';

// C4 add-description-assistant — tareas 3.1 a 3.4. Datos 100 % sintéticos.

const andinas = makeClient('Inversiones Andinas S.A.');
const openMatter = makeMatter('AS-2026-0012', 'Due diligence adquisición planta', andinas);
const SUGGESTION = 'Reunión con el cliente para revisar el contrato de arrendamiento.';
const MARKUP = '<b>Revisión</b> de **contrato** <img src=x onerror=alert(1)>';
const UNAVAILABLE =
  'El asistente no está disponible en este momento; puedes escribir la descripción manualmente.';

type Handler = Parameters<typeof mockFetch>[0][string];

function handlers(overrides: Record<string, Handler> = {}) {
  return {
    'GET /api/me': signIn(abogado),
    'GET /api/features': () => jsonResponse({ descriptionAssistant: true }),
    'GET /api/matters': (request: Request) =>
      jsonResponse(pageOf([openMatter], new URL(request.url))),
    'POST /api/assistant/description-suggestions': () =>
      jsonResponse({ suggestion: SUGGESTION, provider: 'fake' }),
    ...overrides,
  };
}

function description() {
  return screen.getByRole('textbox', { name: /^Descripción/ });
}

function suggestButton() {
  return screen.findByRole('button', { name: 'Sugerir descripción' });
}

function panel() {
  return screen.findByRole('region', { name: 'Sugerencia del asistente' });
}

function suggestionCalls(calls: Request[]) {
  return calls.filter((c) => new URL(c.url).pathname === '/api/assistant/description-suggestions');
}

function timeEntryPosts(calls: Request[]) {
  return calls.filter(
    (c) => c.method === 'POST' && new URL(c.url).pathname === '/api/time-entries',
  );
}

async function typeNote(user: ReturnType<typeof userEvent.setup>, note: string) {
  await suggestButton();
  await user.clear(description());
  await user.type(description(), note);
}

describe('Asistente de descripción en "Registrar horas"', () => {
  // Scenario: Indicador encendido
  it('muestra "Sugerir descripción" cuando GET /api/features lo habilita', async () => {
    mockFetch(handlers());
    renderApp('/registrar-horas');

    expect(await suggestButton()).toBeVisible();
    expect(screen.getByRole('button', { name: 'Sugerir descripción' })).toHaveAccessibleDescription(
      'Se envía la descripción como nota: 0 de 500 caracteres.',
    );
  });

  // Scenario: Indicador apagado
  it('no muestra el botón cuando el indicador está apagado', async () => {
    const { calls } = mockFetch(
      handlers({ 'GET /api/features': () => jsonResponse({ descriptionAssistant: false }) }),
    );
    renderApp('/registrar-horas');

    expect(await screen.findByRole('heading', { level: 1, name: 'Registrar horas' })).toBeVisible();
    await waitFor(() =>
      expect(calls.some((c) => new URL(c.url).pathname === '/api/features')).toBe(true),
    );
    await screen.findByRole('option', { name: /AS-2026-0012/ });
    expect(screen.queryByRole('button', { name: 'Sugerir descripción' })).not.toBeInTheDocument();
  });

  it('no muestra el botón si GET /api/features falla', async () => {
    mockFetch(handlers({ 'GET /api/features': () => problemResponse(internalErrorProblem) }));
    renderApp('/registrar-horas');

    await screen.findByRole('option', { name: /AS-2026-0012/ });
    expect(screen.queryByRole('button', { name: 'Sugerir descripción' })).not.toBeInTheDocument();
    expect(document.body).not.toHaveTextContent('PSQLException');
  });

  // Scenario: Aceptar la sugerencia
  it('"Aceptar" copia la sugerencia, cierra el panel y no guarda el registro', async () => {
    const bodies: DescriptionSuggestionRequest[] = [];
    const { calls } = mockFetch(
      handlers({
        'POST /api/assistant/description-suggestions': async (request) => {
          bodies.push((await request.clone().json()) as DescriptionSuggestionRequest);
          return jsonResponse({ suggestion: SUGGESTION, provider: 'fake' });
        },
      }),
    );
    const user = userEvent.setup();
    renderApp('/registrar-horas');

    const matter = await screen.findByRole('option', { name: /AS-2026-0012/ });
    await user.selectOptions(screen.getByRole('combobox', { name: /^Asunto/ }), matter);
    await typeNote(user, 'reunion cliente revisar contrato arrendamiento');
    await user.click(await suggestButton());

    const region = await panel();
    expect(within(region).getByText(SUGGESTION)).toBeVisible();
    // La nota es el texto del campo descripción; el asunto viaja solo como contexto.
    expect(bodies).toEqual([
      { note: 'reunion cliente revisar contrato arrendamiento', matterId: openMatter.id },
    ]);
    // Mostrar la sugerencia no toca el campo.
    expect(description()).toHaveValue('reunion cliente revisar contrato arrendamiento');

    await user.click(within(region).getByRole('button', { name: 'Aceptar' }));

    expect(description()).toHaveValue(SUGGESTION);
    expect(screen.queryByRole('region', { name: 'Sugerencia del asistente' })).toBeNull();
    expect(timeEntryPosts(calls)).toHaveLength(0);
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  // Scenario: Editar la sugerencia
  it('"Editar" copia la sugerencia y enfoca la descripción con el cursor al final', async () => {
    const { calls } = mockFetch(handlers());
    const user = userEvent.setup();
    renderApp('/registrar-horas');

    await typeNote(user, 'llamada cliente');
    await user.click(await suggestButton());
    await user.click(within(await panel()).getByRole('button', { name: 'Editar' }));

    const field = description() as HTMLTextAreaElement;
    expect(field).toHaveValue(SUGGESTION);
    expect(field).toHaveFocus();
    expect(field.selectionStart).toBe(SUGGESTION.length);
    expect(screen.queryByRole('region', { name: 'Sugerencia del asistente' })).toBeNull();
    expect(timeEntryPosts(calls)).toHaveLength(0);
  });

  // Scenario: Descartar la sugerencia
  it('"Descartar" oculta la sugerencia y conserva el texto original', async () => {
    const { calls } = mockFetch(handlers());
    const user = userEvent.setup();
    renderApp('/registrar-horas');

    await typeNote(user, 'redaccion demanda');
    await user.click(await suggestButton());
    await user.click(within(await panel()).getByRole('button', { name: 'Descartar' }));

    expect(screen.queryByRole('region', { name: 'Sugerencia del asistente' })).toBeNull();
    expect(screen.queryByText(SUGGESTION)).toBeNull();
    expect(description()).toHaveValue('redaccion demanda');
    expect(timeEntryPosts(calls)).toHaveLength(0);
  });

  // Scenario: Guardar después de aceptar
  it('al enviar el formulario tras aceptar, crea el registro con la sugerencia', async () => {
    const bodies: TimeEntryCreate[] = [];
    mockFetch(
      handlers({
        'POST /api/time-entries': async (request) => {
          const body = (await request.clone().json()) as TimeEntryCreate;
          bodies.push(body);
          return jsonResponse(makeTimeEntry(openMatter, body), 201);
        },
      }),
    );
    const user = userEvent.setup();
    renderApp('/registrar-horas');

    const matter = await screen.findByRole('option', { name: /AS-2026-0012/ });
    await user.selectOptions(screen.getByRole('combobox', { name: /^Asunto/ }), matter);
    await user.type(screen.getByRole('textbox', { name: /^Horas/ }), '1,5');
    await typeNote(user, 'reunion cliente revisar contrato');
    await user.click(await suggestButton());
    await user.click(within(await panel()).getByRole('button', { name: 'Aceptar' }));
    expect(bodies).toHaveLength(0);

    await user.click(screen.getByRole('button', { name: 'Guardar' }));

    expect(await screen.findByRole('status')).toHaveTextContent(/Registro guardado: 1,5 h/);
    expect(bodies).toHaveLength(1);
    expect(bodies[0]?.description).toBe(SUGGESTION);
  });

  // Scenario: Sugerencia con marcado
  it('muestra la sugerencia con HTML y Markdown como texto literal y la copia literal', async () => {
    mockFetch(
      handlers({
        'POST /api/assistant/description-suggestions': () =>
          jsonResponse({ suggestion: MARKUP, provider: 'fake' }),
      }),
    );
    const user = userEvent.setup();
    const { container } = renderApp('/registrar-horas');

    await typeNote(user, 'revision contrato');
    await user.click(await suggestButton());

    const region = await panel();
    expect(within(region).getByText(MARKUP)).toBeVisible();
    expect(region.querySelector('b, strong, em, img, script')).toBeNull();
    expect(container.querySelector('img')).toBeNull();

    await user.click(within(region).getByRole('button', { name: 'Aceptar' }));

    expect(description()).toHaveValue(MARKUP);
    expect(container.querySelector('b, strong, img')).toBeNull();
  });

  // Scenario: Asistente no disponible
  it('503 muestra un mensaje en español, conserva el texto y permite guardar', async () => {
    const bodies: TimeEntryCreate[] = [];
    mockFetch(
      handlers({
        'POST /api/assistant/description-suggestions': () =>
          problemResponse(
            problem(503, 'assistant-unavailable', 'java.net.http.HttpTimeoutException: timed out'),
          ),
        'POST /api/time-entries': async (request) => {
          const body = (await request.clone().json()) as TimeEntryCreate;
          bodies.push(body);
          return jsonResponse(makeTimeEntry(openMatter, body), 201);
        },
      }),
    );
    const user = userEvent.setup();
    renderApp('/registrar-horas');

    const matter = await screen.findByRole('option', { name: /AS-2026-0012/ });
    await user.selectOptions(screen.getByRole('combobox', { name: /^Asunto/ }), matter);
    await user.type(screen.getByRole('textbox', { name: /^Horas/ }), '2');
    await typeNote(user, 'Audiencia preliminar.');
    await user.click(await suggestButton());

    expect(await screen.findByRole('alert')).toHaveTextContent(UNAVAILABLE);
    expect(document.body).not.toHaveTextContent('assistant-unavailable');
    expect(document.body).not.toHaveTextContent('HttpTimeoutException');
    expect(description()).toHaveValue('Audiencia preliminar.');

    await user.click(screen.getByRole('button', { name: 'Guardar' }));

    expect(await screen.findByRole('status')).toHaveTextContent(/Registro guardado: 2 h/);
    expect(bodies[0]?.description).toBe('Audiencia preliminar.');
  });

  // Scenario: Nota vacía en la interfaz
  it('con la descripción vacía pide escribir una nota breve sin llamar a la API', async () => {
    const { calls } = mockFetch(handlers());
    const user = userEvent.setup();
    renderApp('/registrar-horas');

    await user.click(await suggestButton());

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Escribe una nota breve en la descripción antes de pedir una sugerencia.',
    );
    expect(suggestionCalls(calls)).toHaveLength(0);
  });

  it('una nota de solo espacios cuenta como vacía', async () => {
    const { calls } = mockFetch(handlers());
    const user = userEvent.setup();
    renderApp('/registrar-horas');

    await typeNote(user, '    ');
    await user.click(await suggestButton());

    expect(await screen.findByRole('alert')).toHaveTextContent('Escribe una nota breve');
    expect(suggestionCalls(calls)).toHaveLength(0);
    expect(description()).toHaveValue('    ');
  });

  it('una nota de más de 500 caracteres avisa el límite y no llama a la API', async () => {
    const { calls } = mockFetch(handlers());
    const user = userEvent.setup();
    renderApp('/registrar-horas');

    const note = 'a'.repeat(501);
    await suggestButton();
    await user.click(description());
    await user.paste(note);

    expect(screen.getByRole('button', { name: 'Sugerir descripción' })).toHaveAccessibleDescription(
      'Se envía la descripción como nota: 501 de 500 caracteres.',
    );
    await user.click(screen.getByRole('button', { name: 'Sugerir descripción' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'La nota admite como máximo 500 caracteres.',
    );
    expect(suggestionCalls(calls)).toHaveLength(0);
    expect(description()).toHaveValue(note);
  });

  it('una nota de 500 caracteres rodeada de espacios se envía', async () => {
    const { calls } = mockFetch(handlers());
    const user = userEvent.setup();
    renderApp('/registrar-horas');

    await suggestButton();
    await user.click(description());
    await user.paste(`  ${'b'.repeat(500)}  `);
    await user.click(screen.getByRole('button', { name: 'Sugerir descripción' }));

    expect(within(await panel()).getByText(SUGGESTION)).toBeVisible();
    expect(suggestionCalls(calls)).toHaveLength(1);
  });

  it.each([
    [
      'assistant-note-empty',
      'Escribe una nota breve en la descripción antes de pedir una sugerencia.',
    ],
    ['assistant-note-too-long', 'La nota admite como máximo 500 caracteres.'],
  ])('400 con código de campo %s muestra el mensaje en español', async (fieldCode, expected) => {
    mockFetch(
      handlers({
        'POST /api/assistant/description-suggestions': () =>
          problemResponse(
            problem(400, 'validation-error', 'Validation failed', {
              errors: [{ field: 'note', code: fieldCode, message: 'note invalid' }],
            }),
          ),
      }),
    );
    const user = userEvent.setup();
    renderApp('/registrar-horas');

    await typeNote(user, 'nota');
    await user.click(await suggestButton());

    expect(await screen.findByRole('alert')).toHaveTextContent(expected);
    expect(document.body).not.toHaveTextContent(fieldCode);
    expect(description()).toHaveValue('nota');
  });

  it('un error inesperado muestra un mensaje genérico sin detalles internos', async () => {
    mockFetch(
      handlers({
        'POST /api/assistant/description-suggestions': () => problemResponse(internalErrorProblem),
      }),
    );
    const user = userEvent.setup();
    renderApp('/registrar-horas');

    await typeNote(user, 'nota');
    await user.click(await suggestButton());

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Ocurrió un error inesperado. Inténtalo de nuevo en unos minutos.',
    );
    expect(document.body).not.toHaveTextContent('PSQLException');
  });

  it('404 feature-disabled avisa y oculta el botón tras refrescar los indicadores', async () => {
    let enabled = true;
    mockFetch(
      handlers({
        'GET /api/features': () => jsonResponse({ descriptionAssistant: enabled }),
        'POST /api/assistant/description-suggestions': () => {
          enabled = false;
          return problemResponse(problem(404, 'feature-disabled', 'Feature disabled'));
        },
      }),
    );
    const user = userEvent.setup();
    renderApp('/registrar-horas');

    await typeNote(user, 'nota');
    await user.click(await suggestButton());

    await waitFor(() =>
      expect(screen.queryByRole('button', { name: 'Sugerir descripción' })).toBeNull(),
    );
    expect(description()).toHaveValue('nota');
    expect(document.body).not.toHaveTextContent('Feature disabled');
  });

  it('404 not-found del asunto muestra un mensaje en español', async () => {
    mockFetch(
      handlers({
        'POST /api/assistant/description-suggestions': () =>
          problemResponse(problem(404, 'not-found', 'Matter not found')),
      }),
    );
    const user = userEvent.setup();
    renderApp('/registrar-horas');

    const matter = await screen.findByRole('option', { name: /AS-2026-0012/ });
    await user.selectOptions(screen.getByRole('combobox', { name: /^Asunto/ }), matter);
    await typeNote(user, 'redaccion demanda');
    await user.click(await suggestButton());

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'El asunto seleccionado ya no existe.',
    );
  });

  it('muestra el estado de carga mientras espera la sugerencia', async () => {
    let release: (response: Response) => void = () => undefined;
    mockFetch(
      handlers({
        'POST /api/assistant/description-suggestions': () =>
          new Promise<Response>((resolve) => {
            release = resolve;
          }),
      }),
    );
    const user = userEvent.setup();
    renderApp('/registrar-horas');

    await typeNote(user, 'llamada cliente');
    await user.click(await suggestButton());

    const busy = await screen.findByRole('button', { name: 'Generando sugerencia…' });
    expect(busy).toHaveAttribute('aria-busy', 'true');
    release(jsonResponse({ suggestion: SUGGESTION, provider: 'fake' }));
    expect(within(await panel()).getByText(SUGGESTION)).toBeVisible();
    expect(screen.getByRole('button', { name: 'Sugerir descripción' })).not.toHaveAttribute(
      'aria-busy',
    );
  });
});
