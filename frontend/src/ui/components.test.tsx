import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { ConfirmDialog } from './ConfirmDialog';
import { DataTable } from './DataTable';
import { Pagination } from './Pagination';
import { SearchInput } from './SearchInput';

describe('Pagination', () => {
  it('muestra la página en base 1 y desactiva los extremos', async () => {
    const onPageChange = vi.fn();
    const user = userEvent.setup();
    const { rerender } = render(<Pagination page={0} totalPages={3} onPageChange={onPageChange} />);

    expect(screen.getByRole('navigation', { name: 'Paginación' })).toBeVisible();
    expect(screen.getByText('Página 1 de 3')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Anterior' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Siguiente' }));
    expect(onPageChange).toHaveBeenCalledWith(1);

    rerender(<Pagination page={2} totalPages={3} onPageChange={onPageChange} />);
    expect(screen.getByText('Página 3 de 3')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Siguiente' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Anterior' }));
    expect(onPageChange).toHaveBeenLastCalledWith(1);
  });

  it('con 0 páginas muestra "Página 1 de 1" y ambos botones desactivados', () => {
    render(<Pagination page={0} totalPages={0} onPageChange={vi.fn()} />);

    expect(screen.getByText('Página 1 de 1')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Anterior' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Siguiente' })).toBeDisabled();
  });
});

describe('SearchInput', () => {
  it('aplica debounce: una sola búsqueda con el texto recortado tras dejar de escribir', async () => {
    // Cada "tecla" es un change síncrono: entre ellas no pasa tiempo real, así que el debounce no
    // puede disparar a mitad de la escritura. Con user.type y timers reales, una máquina cargada
    // tardaba más de 50 ms entre teclas y el test fallaba de forma intermitente (bitácora #26).
    const onSearch = vi.fn();
    render(<SearchInput label="Buscar" value="" onSearch={onSearch} delay={50} />);
    const input = screen.getByRole('searchbox', { name: 'Buscar' });

    for (const partial of [' ', ' a', ' an', ' and', ' andi', ' andin', ' andin ']) {
      fireEvent.change(input, { target: { value: partial } });
    }
    expect(input).toHaveValue(' andin ');
    expect(onSearch).not.toHaveBeenCalled();

    await waitFor(() => expect(onSearch).toHaveBeenCalledTimes(1));
    expect(onSearch).toHaveBeenCalledWith('andin');
    await new Promise((resolve) => setTimeout(resolve, 80));
    expect(onSearch).toHaveBeenCalledTimes(1);
  });

  it('no busca si el texto coincide con el valor confirmado y refleja cambios externos', async () => {
    const onSearch = vi.fn();
    const { rerender } = render(
      <SearchInput label="Buscar" value="illimani" onSearch={onSearch} delay={10} />,
    );
    expect(screen.getByRole('searchbox')).toHaveValue('illimani');

    rerender(<SearchInput label="Buscar" value="AS-2026-0013" onSearch={onSearch} delay={10} />);
    expect(screen.getByRole('searchbox')).toHaveValue('AS-2026-0013');
    await new Promise((resolve) => setTimeout(resolve, 30));
    expect(onSearch).not.toHaveBeenCalled();
  });
});

describe('DataTable', () => {
  it('renderiza caption, encabezados de columna y de fila', () => {
    render(
      <DataTable
        caption="Lista de prueba"
        columns={[
          { header: 'Nombre', cell: (r: { id: string; name: string }) => r.name, rowHeader: true },
          { header: 'Id', cell: (r) => r.id },
        ]}
        rows={[{ id: '1', name: 'Uno' }]}
        rowKey={(r) => r.id}
      />,
    );

    expect(screen.getByRole('table', { name: 'Lista de prueba' })).toBeVisible();
    expect(screen.getByRole('columnheader', { name: 'Nombre' })).toBeVisible();
    expect(screen.getByRole('rowheader', { name: 'Uno' })).toBeVisible();
    expect(screen.getByRole('cell', { name: '1' })).toBeVisible();
  });
});

function DialogHarness({ onConfirm }: { onConfirm: () => void }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button type="button" onClick={() => setOpen(true)}>
        Abrir
      </button>
      <ConfirmDialog
        open={open}
        title="¿Confirmar?"
        confirmLabel="Sí, confirmar"
        onConfirm={() => {
          onConfirm();
          setOpen(false);
        }}
        onCancel={() => setOpen(false)}
      >
        <p>Acción irreversible.</p>
      </ConfirmDialog>
    </>
  );
}

describe('ConfirmDialog', () => {
  it('abre como modal con foco en Cancelar y devuelve el foco al disparador', async () => {
    const onConfirm = vi.fn();
    const user = userEvent.setup();
    render(<DialogHarness onConfirm={onConfirm} />);
    const dialog = document.querySelector('dialog')!;
    expect(dialog).not.toHaveAttribute('open');

    const trigger = screen.getByRole('button', { name: 'Abrir' });
    await user.click(trigger);

    expect(dialog).toHaveAttribute('open');
    expect(dialog).toHaveAccessibleName('¿Confirmar?');
    expect(dialog).toHaveAccessibleDescription('Acción irreversible.');
    expect(screen.getByRole('button', { name: 'Cancelar' })).toHaveFocus();

    await user.click(screen.getByRole('button', { name: 'Sí, confirmar' }));
    expect(onConfirm).toHaveBeenCalledTimes(1);
    expect(dialog).not.toHaveAttribute('open');
    expect(trigger).toHaveFocus();
  });
});
