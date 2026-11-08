import { useId, useRef, useState, type FormEvent } from 'react';
import { DESCRIPTION_MAX, formatHours, isIsoDate, parseHours, todayInLaPaz } from '../api/hours';
import { ApiProblem } from '../api/problem';
import { fieldMessage, PROBLEM_MESSAGES, userMessage } from '../api/problemMessages';
import { useOpenMatterOptions } from '../api/timeEntries';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { SelectField, type SelectOption } from '../ui/SelectField';
import { TextAreaField } from '../ui/TextAreaField';
import { TextField } from '../ui/TextField';

export interface TimeEntryFormValues {
  matterId: string;
  workDate: string;
  hours: number;
  description: string;
}

interface FieldErrors {
  matterId?: string;
  workDate?: string;
  hours?: string;
  description?: string;
}

type Field = keyof FieldErrors;

const FIELD_ORDER: readonly Field[] = ['matterId', 'workDate', 'hours', 'description'];
const FUTURE_DATE_MESSAGE = PROBLEM_MESSAGES['future-date'] ?? '';

/** Regla de negocio (422) → campo junto al que se muestra (spec "Mensajes de error claros"). */
const CODE_FIELD: Readonly<Record<string, Field>> = {
  'future-date': 'workDate',
  'hours-invalid-increment': 'hours',
  'hours-out-of-range': 'hours',
  'daily-hours-exceeded': 'hours',
  'matter-closed': 'matterId',
};

/** Validación previa en el cliente; el servidor vuelve a validar todo (design D8). */
export function validateTimeEntry(
  input: { matterId: string; workDate: string; hours: string; description: string },
  today: string,
): { errors: FieldErrors; hours?: number } {
  const errors: FieldErrors = {};
  if (input.matterId === '') errors.matterId = 'Selecciona el asunto.';
  if (!isIsoDate(input.workDate)) errors.workDate = 'Ingresa la fecha de trabajo.';
  else if (input.workDate > today) errors.workDate = FUTURE_DATE_MESSAGE;
  const parsed = parseHours(input.hours);
  if (!parsed.ok) errors.hours = parsed.message;
  const description = input.description.trim();
  if (description === '') errors.description = 'Describe el trabajo realizado.';
  else if (description.length > DESCRIPTION_MAX)
    errors.description = `La descripción no puede superar ${DESCRIPTION_MAX} caracteres.`;
  return { errors, hours: parsed.ok ? parsed.value : undefined };
}

export interface TimeEntryFormProps {
  id?: string;
  /** Título (`<h2>`) y nombre accesible de la región del formulario. */
  title: string;
  submitLabel: string;
  initial?: { matterId: string; workDate: string; hours: number; description: string };
  /** Opción adicional para el asunto actual de un registro si ya no está `OPEN`. */
  currentMatter?: SelectOption;
  /** Limpia el formulario tras guardar (alta). */
  resetOnSuccess?: boolean;
  /** Mensaje para `not-found` (asunto o registro inexistente). */
  notFoundMessage?: string;
  /** Guarda; si lanza `ApiProblem`, el formulario muestra el error y conserva los datos. */
  onSubmit: (values: TimeEntryFormValues) => Promise<void>;
  onCancel?: () => void;
}

/** Formulario de registro de horas, compartido por el alta y la edición. */
export function TimeEntryForm({
  id,
  title,
  submitLabel,
  initial,
  currentMatter,
  resetOnSuccess = false,
  notFoundMessage = 'El asunto seleccionado ya no existe.',
  onSubmit,
  onCancel,
}: TimeEntryFormProps) {
  const headingId = useId();
  const today = todayInLaPaz();
  const [matterId, setMatterId] = useState(initial?.matterId ?? '');
  const [workDate, setWorkDate] = useState(initial?.workDate ?? today);
  const [hours, setHours] = useState(initial ? formatHours(initial.hours) : '');
  const [description, setDescription] = useState(initial?.description ?? '');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const matterRef = useRef<HTMLSelectElement>(null);
  const workDateRef = useRef<HTMLInputElement>(null);
  const hoursRef = useRef<HTMLInputElement>(null);
  const descriptionRef = useRef<HTMLTextAreaElement>(null);
  const matters = useOpenMatterOptions();

  const options: SelectOption[] = [
    { value: '', label: matters.isPending ? 'Cargando asuntos…' : 'Selecciona un asunto abierto' },
    ...(matters.data?.items ?? []).map((m) => ({ value: m.id, label: `${m.code} — ${m.title}` })),
  ];
  if (currentMatter && !options.some((o) => o.value === currentMatter.value)) {
    options.push(currentMatter);
  }
  const noMatters = matters.isSuccess && matters.data.items.length === 0;

  function focusFirst(errors: FieldErrors) {
    const first = FIELD_ORDER.find((field) => errors[field]);
    const target = {
      matterId: matterRef,
      workDate: workDateRef,
      hours: hoursRef,
      description: descriptionRef,
    };
    if (first) target[first].current?.focus();
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    setFormError(null);
    const { errors, hours: value } = validateTimeEntry(
      { matterId, workDate, hours, description },
      todayInLaPaz(),
    );
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0 || value === undefined) return focusFirst(errors);

    setBusy(true);
    try {
      await onSubmit({ matterId, workDate, hours: value, description: description.trim() });
      if (resetOnSuccess) {
        setMatterId('');
        setWorkDate(todayInLaPaz());
        setHours('');
        setDescription('');
      }
    } catch (error) {
      const message = userMessage(error, { 'not-found': notFoundMessage });
      if (error instanceof ApiProblem) {
        const field = CODE_FIELD[error.code];
        const next: FieldErrors = field
          ? { [field]: message }
          : {
              matterId: fieldMessage(error, 'matterId'),
              workDate: fieldMessage(error, 'workDate'),
              hours: fieldMessage(error, 'hours'),
              description: fieldMessage(error, 'description'),
            };
        setFieldErrors(next);
        focusFirst(next);
      }
      setFormError(message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section id={id} className="panel" aria-labelledby={headingId}>
      <h2 id={headingId}>{title}</h2>
      {formError ? <Alert title="No se pudo guardar el registro">{formError}</Alert> : null}
      {matters.isError ? (
        <Alert title="No se pudieron cargar los asuntos">{userMessage(matters.error)}</Alert>
      ) : null}
      <form onSubmit={handleSubmit} noValidate>
        <SelectField
          ref={matterRef}
          label="Asunto"
          name="matterId"
          required
          options={options}
          hint={noMatters ? 'No hay asuntos abiertos para registrar horas.' : undefined}
          value={matterId}
          onChange={(e) => setMatterId(e.target.value)}
          error={fieldErrors.matterId}
        />
        <TextField
          ref={workDateRef}
          label="Fecha de trabajo"
          name="workDate"
          type="date"
          required
          max={today}
          hint="No puede ser posterior a hoy (hora de La Paz)."
          value={workDate}
          onChange={(e) => setWorkDate(e.target.value)}
          error={fieldErrors.workDate}
        />
        <TextField
          ref={hoursRef}
          label="Horas"
          name="hours"
          inputMode="decimal"
          autoComplete="off"
          required
          hint="En incrementos de 0,1, mayor que 0 y hasta 24. Acepta coma o punto (1,5)."
          value={hours}
          onChange={(e) => setHours(e.target.value)}
          error={fieldErrors.hours}
        />
        <TextAreaField
          ref={descriptionRef}
          label="Descripción"
          name="description"
          required
          rows={4}
          maxLength={DESCRIPTION_MAX}
          hint={`Hasta ${DESCRIPTION_MAX} caracteres.`}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          error={fieldErrors.description}
        />
        <div className="form-actions">
          <Button type="submit" busy={busy}>
            {busy ? 'Guardando…' : submitLabel}
          </Button>
          {onCancel ? (
            <Button variant="outline" onClick={onCancel}>
              Cancelar
            </Button>
          ) : null}
        </div>
      </form>
    </section>
  );
}
