import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import type { Matter } from '../api/clientsMatters';
import { abogado, makeClient, makeMatter, signIn, socia } from '../test/fixtures';
import { jsonResponse, mockFetch, problemResponse, renderApp } from '../test/utils';

const andinas = makeClient('Inversiones Andinas S.A.');

function openMatter(): Matter {
  return makeMatter('AS-2026-0012', 'Due diligence adquisición planta', andinas, 'OPEN');
}

function closedCopy(matter: Matter): Matter {
  return { ...matter, status: 'CLOSED', closedAt: '2026-11-07T18:30:00Z' };
}

function statusValue() {
  return screen.getByText('Estado').nextElementSibling;
}

function dialog() {
  return document.querySelector('dialog') as HTMLDialogElement;
}

describe('Detalle de asunto y cierre', () => {
  // Scenario: Socio confirma el cierre
  it('el Socio confirma el cierre: el asunto se muestra Cerrado y desaparece la acción', async () => {
    const matter = openMatter();
    let current = matter;
    const { calls } = mockFetch({
      'GET /api/me': signIn(socia),
      [`GET /api/matters/${matter.id}`]: () => jsonResponse(current),
      [`POST /api/matters/${matter.id}/close`]: () => {
        current = closedCopy(matter);
        return jsonResponse(current);
      },
    });
    const user = userEvent.setup();
    renderApp(`/asuntos/${matter.id}`);

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Asunto AS-2026-0012' }),
    ).toBeVisible();
    expect(statusValue()).toHaveTextContent('Abierto');

    await user.click(screen.getByRole('button', { name: 'Cerrar asunto' }));
    expect(dialog()).toHaveAttribute('open');
    expect(dialog()).toHaveAccessibleName('¿Cerrar el asunto AS-2026-0012?');
    expect(dialog()).toHaveAccessibleDescription(/irreversible/);
    await user.click(screen.getByRole('button', { name: 'Sí, cerrar asunto' }));

    expect(await screen.findByText(/El asunto se cerró/)).toBeVisible();
    expect(statusValue()).toHaveTextContent('Cerrado');
    expect(screen.getByText('Cerrado el')).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Cerrar asunto' })).not.toBeInTheDocument();
    expect(dialog()).not.toHaveAttribute('open');
    expect(screen.getByRole('heading', { level: 1 })).toHaveFocus();
    expect(calls.filter((c) => c.method === 'POST')).toHaveLength(1);
  });

  // Scenario: Socio cancela el cierre
  it('al cancelar no se envía ninguna petición y el asunto sigue Abierto', async () => {
    const matter = openMatter();
    const { calls } = mockFetch({
      'GET /api/me': signIn(socia),
      [`GET /api/matters/${matter.id}`]: () => jsonResponse(matter),
    });
    const user = userEvent.setup();
    renderApp(`/asuntos/${matter.id}`);

    const trigger = await screen.findByRole('button', { name: 'Cerrar asunto' });
    await user.click(trigger);
    // El foco inicial va a la opción segura.
    expect(screen.getByRole('button', { name: 'Cancelar' })).toHaveFocus();
    await user.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(dialog()).not.toHaveAttribute('open');
    expect(trigger).toHaveFocus();
    expect(statusValue()).toHaveTextContent('Abierto');
    expect(calls.some((c) => c.method === 'POST')).toBe(false);
  });

  it('Escape (evento cancel del diálogo) equivale a Cancelar', async () => {
    const matter = openMatter();
    const { calls } = mockFetch({
      'GET /api/me': signIn(socia),
      [`GET /api/matters/${matter.id}`]: () => jsonResponse(matter),
    });
    const user = userEvent.setup();
    renderApp(`/asuntos/${matter.id}`);

    await user.click(await screen.findByRole('button', { name: 'Cerrar asunto' }));
    fireEvent(dialog(), new Event('cancel', { cancelable: true }));

    await waitFor(() => expect(dialog()).not.toHaveAttribute('open'));
    expect(calls.some((c) => c.method === 'POST')).toBe(false);
  });

  // Scenario: Asunto cerrado por otro Socio mientras tanto
  it('ante 409 matter-already-closed muestra el aviso y el asunto pasa a Cerrado', async () => {
    const matter = openMatter();
    let current = matter;
    mockFetch({
      'GET /api/me': signIn(socia),
      [`GET /api/matters/${matter.id}`]: () => jsonResponse(current),
      [`POST /api/matters/${matter.id}/close`]: () => {
        current = closedCopy(matter); // otro Socio ya lo había cerrado
        return problemResponse({
          type: 'https://asuntos-horas.example/problems/matter-already-closed',
          title: 'El asunto ya está cerrado',
          status: 409,
          detail: 'El asunto ya está cerrado.',
          code: 'matter-already-closed',
          traceId: 'abc',
        });
      },
    });
    const user = userEvent.setup();
    renderApp(`/asuntos/${matter.id}`);

    await user.click(await screen.findByRole('button', { name: 'Cerrar asunto' }));
    await user.click(screen.getByRole('button', { name: 'Sí, cerrar asunto' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'El asunto ya estaba cerrado por otro usuario.',
    );
    await waitFor(() => expect(statusValue()).toHaveTextContent('Cerrado'));
    expect(screen.queryByRole('button', { name: 'Cerrar asunto' })).not.toBeInTheDocument();
  });

  it('un error inesperado al cerrar muestra un mensaje genérico y el asunto sigue Abierto', async () => {
    const matter = openMatter();
    mockFetch({
      'GET /api/me': signIn(socia),
      [`GET /api/matters/${matter.id}`]: () => jsonResponse(matter),
      [`POST /api/matters/${matter.id}/close`]: () =>
        new Response('<html>Bad Gateway</html>', { status: 502 }),
    });
    const user = userEvent.setup();
    renderApp(`/asuntos/${matter.id}`);

    await user.click(await screen.findByRole('button', { name: 'Cerrar asunto' }));
    await user.click(screen.getByRole('button', { name: 'Sí, cerrar asunto' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/Ocurrió un error inesperado/);
    expect(statusValue()).toHaveTextContent('Abierto');
  });

  // Scenario: Abogado no ve la acción de cierre
  it('el Abogado ve el asunto Abierto pero no la acción Cerrar asunto', async () => {
    const matter = openMatter();
    mockFetch({
      'GET /api/me': signIn(abogado),
      [`GET /api/matters/${matter.id}`]: () => jsonResponse(matter),
    });
    renderApp(`/asuntos/${matter.id}`);

    await screen.findByRole('heading', { level: 1, name: 'Asunto AS-2026-0012' });
    expect(statusValue()).toHaveTextContent('Abierto');
    expect(screen.queryByRole('button', { name: 'Cerrar asunto' })).not.toBeInTheDocument();
    expect(document.querySelector('dialog')).toBeNull();
  });

  // Scenario: La interfaz no ofrece reabrir
  it('un asunto Cerrado no ofrece cerrar, reabrir ni editar', async () => {
    const matter = closedCopy(openMatter());
    mockFetch({
      'GET /api/me': signIn(socia),
      [`GET /api/matters/${matter.id}`]: () => jsonResponse(matter),
    });
    renderApp(`/asuntos/${matter.id}`);

    await screen.findByRole('heading', { level: 1, name: 'Asunto AS-2026-0012' });
    expect(statusValue()).toHaveTextContent('Cerrado');
    expect(screen.queryByRole('button', { name: 'Cerrar asunto' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /reabrir|editar/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /reabrir|editar/i })).not.toBeInTheDocument();
  });

  it('un asunto inexistente muestra "El asunto no existe."', async () => {
    const missing = '00000000-0000-4000-8000-00000000dead';
    mockFetch({
      'GET /api/me': signIn(socia),
      [`GET /api/matters/${missing}`]: () =>
        problemResponse({
          type: 'https://asuntos-horas.example/problems/not-found',
          title: 'Recurso no encontrado',
          status: 404,
          detail: 'No existe el asunto.',
          code: 'not-found',
          traceId: 'abc',
        }),
    });
    renderApp(`/asuntos/${missing}`);

    expect(await screen.findByRole('alert')).toHaveTextContent('El asunto no existe.');
    expect(screen.getByRole('link', { name: /Volver a Asuntos/ })).toHaveAttribute(
      'href',
      '/asuntos',
    );
  });
});
