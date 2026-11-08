import { useEffect, useId, useRef, useState, type FormEvent, type SyntheticEvent } from 'react';
import { REJECTION_REASON_MAX } from '../api/hours';
import { Button } from '../ui/Button';

export interface RejectDialogProps {
  open: boolean;
  /** Resumen del registro que se rechaza (abogado, asunto, fecha y horas). */
  summary: string;
  busy?: boolean;
  /** Error del servidor (p. ej. `rejection-reason-required`), mostrado junto al motivo. */
  error?: string;
  onConfirm: (reason: string) => void;
  onCancel: () => void;
}

/**
 * Diálogo **Rechazar** (spec "Pantalla Aprobaciones", design D8) sobre `<dialog>` nativo con
 * `showModal()`: `aria-modal` implícito y foco atrapado. Exige un motivo de 1 a 500 caracteres tras
 * recortar; "Confirmar rechazo" queda deshabilitado mientras esté vacío. Contador 0/500 anunciado.
 */
export function RejectDialog({
  open,
  summary,
  busy = false,
  error,
  onConfirm,
  onCancel,
}: RejectDialogProps) {
  const id = useId();
  const titleId = `${id}-title`;
  const summaryId = `${id}-summary`;
  const reasonId = `${id}-reason`;
  const counterId = `${id}-counter`;
  const errorId = `${id}-error`;
  const dialogRef = useRef<HTMLDialogElement>(null);
  const reasonRef = useRef<HTMLTextAreaElement>(null);
  const returnFocusRef = useRef<HTMLElement | null>(null);
  const [reason, setReason] = useState('');
  const [wasOpen, setWasOpen] = useState(open);

  // Cada apertura empieza con el motivo vacío (ajuste de estado durante el render).
  if (open !== wasOpen) {
    setWasOpen(open);
    if (open) setReason('');
  }

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (open && !dialog.open) {
      returnFocusRef.current =
        document.activeElement instanceof HTMLElement ? document.activeElement : null;
      dialog.showModal();
      reasonRef.current?.focus();
    } else if (!open && dialog.open) {
      dialog.close();
      const target = returnFocusRef.current;
      if (target?.isConnected) target.focus();
      returnFocusRef.current = null;
    }
  }, [open]);

  const trimmed = reason.trim();
  const valid = trimmed.length >= 1 && trimmed.length <= REJECTION_REASON_MAX;

  function handleCancel(event: SyntheticEvent<HTMLDialogElement>) {
    event.preventDefault();
    if (!busy) onCancel();
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (valid && !busy) onConfirm(trimmed);
  }

  const describedBy = [counterId, error ? errorId : null].filter(Boolean).join(' ');

  return (
    <dialog
      ref={dialogRef}
      className="dialog"
      aria-labelledby={titleId}
      aria-describedby={summaryId}
      onCancel={handleCancel}
    >
      <h2 id={titleId} className="dialog__title">
        Rechazar registro
      </h2>
      <form onSubmit={handleSubmit} noValidate>
        <div className="dialog__body">
          <p id={summaryId}>{summary}</p>
          <div className={['field', error ? 'field--invalid' : null].filter(Boolean).join(' ')}>
            <label htmlFor={reasonId} className="field__label">
              Motivo del rechazo
              <span className="field__required" aria-hidden="true">
                {' '}
                *
              </span>
            </label>
            <textarea
              ref={reasonRef}
              id={reasonId}
              name="reason"
              className="field__input field__textarea"
              rows={4}
              required
              aria-required
              maxLength={REJECTION_REASON_MAX}
              aria-invalid={error ? true : undefined}
              aria-describedby={describedBy}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
            <p id={counterId} className="field__hint" aria-live="polite">
              {reason.length}/{REJECTION_REASON_MAX} caracteres
            </p>
            {error ? (
              <p id={errorId} className="field__error">
                {error}
              </p>
            ) : null}
          </div>
        </div>
        <div className="dialog__actions">
          <Button variant="outline" onClick={onCancel} disabled={busy}>
            Cancelar
          </Button>
          <Button type="submit" variant="danger" busy={busy} disabled={!valid}>
            Confirmar rechazo
          </Button>
        </div>
      </form>
    </dialog>
  );
}
