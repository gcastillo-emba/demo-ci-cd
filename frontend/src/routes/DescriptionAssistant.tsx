import { useId, useState } from 'react';
import {
  assistantErrorMessage,
  NOTE_MAX,
  noteLength,
  useSuggestDescription,
  validateNote,
} from '../api/assistant';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';

/** Cómo se aplica la sugerencia al campo descripción. */
export type ApplyMode = 'accept' | 'edit';

export interface DescriptionAssistantProps {
  /** Texto actual del campo descripción: es la nota que se envía (design Supuesto 9). */
  note: string;
  /** Asunto seleccionado (opcional, solo contexto); vacío si no hay. */
  matterId: string;
  /** Copia la sugerencia al campo descripción; `edit` además le da el foco. Nunca envía el form. */
  onApply: (suggestion: string, mode: ApplyMode) => void;
}

/**
 * Botón "Sugerir descripción" y panel de sugerencia (C4, design D10). Humano en el circuito: la
 * sugerencia se muestra como texto plano (`{suggestion}`, nunca HTML ni Markdown) y solo llega al
 * campo con "Aceptar" o "Editar"; nada se guarda hasta que el Abogado envía el formulario.
 */
export function DescriptionAssistant({ note, matterId, onApply }: DescriptionAssistantProps) {
  const counterId = useId();
  const headingId = useId();
  const suggest = useSuggestDescription();
  const [localError, setLocalError] = useState<string | null>(null);
  const length = noteLength(note);
  const overLimit = length > NOTE_MAX;
  const suggestion = suggest.data?.suggestion ?? null;
  const error = localError ?? (suggest.isError ? assistantErrorMessage(suggest.error) : null);

  function request() {
    if (suggest.isPending) return;
    suggest.reset();
    const invalid = validateNote(note);
    setLocalError(invalid ?? null);
    if (invalid) return;
    suggest.mutate({ note, ...(matterId !== '' ? { matterId } : {}) });
  }

  function apply(mode: ApplyMode) {
    if (suggestion === null) return;
    onApply(suggestion, mode);
    suggest.reset();
  }

  return (
    <div className="assistant">
      <div className="assistant__bar">
        <Button
          variant="secondary"
          busy={suggest.isPending}
          aria-describedby={counterId}
          onClick={request}
        >
          {suggest.isPending ? 'Generando sugerencia…' : 'Sugerir descripción'}
        </Button>
        <p
          id={counterId}
          className={['field__hint', overLimit ? 'assistant__counter--over' : null]
            .filter(Boolean)
            .join(' ')}
        >
          Se envía la descripción como nota: {length} de {NOTE_MAX} caracteres.
        </p>
      </div>
      {error ? <Alert title="No se pudo obtener una sugerencia">{error}</Alert> : null}
      <div className="assistant__live" aria-live="polite">
        {suggest.isPending ? <p className="field__hint">Generando sugerencia…</p> : null}
        {suggestion !== null ? (
          <section className="assistant__panel" aria-labelledby={headingId}>
            <h3 id={headingId}>Sugerencia del asistente</h3>
            <p className="assistant__text">{suggestion}</p>
            <p className="field__hint">
              Revísala antes de usarla: no se guarda nada hasta que pulses &quot;Guardar&quot;.
            </p>
            <div className="form-actions">
              <Button onClick={() => apply('accept')}>Aceptar</Button>
              <Button variant="secondary" onClick={() => apply('edit')}>
                Editar
              </Button>
              <Button variant="outline" onClick={() => suggest.reset()}>
                Descartar
              </Button>
            </div>
          </section>
        ) : null}
      </div>
    </div>
  );
}
