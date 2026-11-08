import { useEffect, useId, useRef, type ReactNode, type SyntheticEvent } from 'react';
import { Button } from './Button';

export interface ConfirmDialogProps {
  open: boolean;
  title: string;
  children: ReactNode;
  confirmLabel: string;
  cancelLabel?: string;
  busy?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

/**
 * Diálogo de confirmación sobre `<dialog>` nativo abierto con `showModal()` (design D9): el resto
 * de la página queda inerte, así que el foco queda atrapado en el diálogo. Escape equivale a
 * Cancelar. El foco inicial va a Cancelar (la opción segura) y al cerrar vuelve al disparador.
 */
export function ConfirmDialog({
  open,
  title,
  children,
  confirmLabel,
  cancelLabel = 'Cancelar',
  busy = false,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  const id = useId();
  const titleId = `${id}-title`;
  const bodyId = `${id}-body`;
  const dialogRef = useRef<HTMLDialogElement>(null);
  const cancelRef = useRef<HTMLButtonElement>(null);
  const returnFocusRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (open && !dialog.open) {
      returnFocusRef.current =
        document.activeElement instanceof HTMLElement ? document.activeElement : null;
      dialog.showModal();
      cancelRef.current?.focus();
    } else if (!open && dialog.open) {
      dialog.close();
      const target = returnFocusRef.current;
      if (target?.isConnected) target.focus();
      returnFocusRef.current = null;
    }
  }, [open]);

  function handleCancel(event: SyntheticEvent<HTMLDialogElement>) {
    // Escape: el navegador cerraría el diálogo por su cuenta; lo controla el estado `open`.
    event.preventDefault();
    if (!busy) onCancel();
  }

  return (
    <dialog
      ref={dialogRef}
      className="dialog"
      aria-labelledby={titleId}
      aria-describedby={bodyId}
      onCancel={handleCancel}
    >
      <h2 id={titleId} className="dialog__title">
        {title}
      </h2>
      <div id={bodyId} className="dialog__body">
        {children}
      </div>
      <div className="dialog__actions">
        <Button ref={cancelRef} variant="outline" onClick={onCancel} disabled={busy}>
          {cancelLabel}
        </Button>
        <Button variant="danger" busy={busy} onClick={onConfirm}>
          {confirmLabel}
        </Button>
      </div>
    </dialog>
  );
}
