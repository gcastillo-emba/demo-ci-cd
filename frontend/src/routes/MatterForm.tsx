import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import {
  useClientOptions,
  useCreateMatter,
  type Matter,
  type MatterCreate,
} from '../api/clientsMatters';
import { ApiProblem } from '../api/problem';
import { fieldMessage, userMessage } from '../api/problemMessages';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { SelectField } from '../ui/SelectField';
import { TextAreaField } from '../ui/TextAreaField';
import { TextField } from '../ui/TextField';

export const MATTER_TITLE_MAX = 200;
export const MATTER_DESCRIPTION_MAX = 2000;

interface MatterFieldErrors {
  clientId?: string;
  title?: string;
  description?: string;
}

/** Mismos límites que la API (spec "Validación de datos del asunto"), tras recortar el título. */
export function validateMatter(clientId: string, title: string, description: string) {
  const errors: MatterFieldErrors = {};
  if (clientId === '') errors.clientId = 'Selecciona el cliente del asunto.';
  const trimmedTitle = title.trim();
  if (trimmedTitle === '') errors.title = 'Ingresa el título del asunto.';
  else if (trimmedTitle.length > MATTER_TITLE_MAX)
    errors.title = `El título no puede superar ${MATTER_TITLE_MAX} caracteres.`;
  if (description.length > MATTER_DESCRIPTION_MAX)
    errors.description = `La descripción no puede superar ${MATTER_DESCRIPTION_MAX} caracteres.`;
  return errors;
}

export interface MatterFormProps {
  id?: string;
  /** Cliente preseleccionado (p. ej. el del filtro activo). */
  defaultClientId?: string;
  onCreated: (matter: Matter) => void;
  onCancel: () => void;
}

/** Formulario **Nuevo asunto** (solo Socio). El código y el estado los asigna el servidor. */
export function MatterForm({ id, defaultClientId = '', onCreated, onCancel }: MatterFormProps) {
  const titleId = useId();
  const [clientId, setClientId] = useState(defaultClientId);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [fieldErrors, setFieldErrors] = useState<MatterFieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const clientRef = useRef<HTMLSelectElement>(null);
  const titleRef = useRef<HTMLInputElement>(null);
  const descriptionRef = useRef<HTMLTextAreaElement>(null);
  const clients = useClientOptions();
  const createMatter = useCreateMatter();

  useEffect(() => {
    clientRef.current?.focus();
  }, []);

  const clientOptions = [
    { value: '', label: clients.isPending ? 'Cargando clientes…' : 'Selecciona un cliente' },
    ...(clients.data?.items ?? []).map((c) => ({ value: c.id, label: c.name })),
  ];
  const noClients = clients.isSuccess && clients.data.items.length === 0;

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (createMatter.isPending) return;
    setFormError(null);

    const errors = validateMatter(clientId, title, description);
    setFieldErrors(errors);
    if (errors.clientId) return clientRef.current?.focus();
    if (errors.title) return titleRef.current?.focus();
    if (errors.description) return descriptionRef.current?.focus();

    const body: MatterCreate = { clientId, title: title.trim() };
    if (description.trim() !== '') body.description = description;

    try {
      const matter = await createMatter.mutateAsync(body);
      onCreated(matter);
    } catch (error) {
      if (error instanceof ApiProblem && error.code === 'not-found') {
        setFieldErrors({ clientId: 'El cliente seleccionado ya no existe.' });
        clientRef.current?.focus();
        return;
      }
      if (error instanceof ApiProblem) {
        setFieldErrors({
          clientId: fieldMessage(error, 'clientId'),
          title: fieldMessage(error, 'title'),
          description: fieldMessage(error, 'description'),
        });
      }
      setFormError(userMessage(error));
    }
  }

  return (
    <section id={id} className="panel" aria-labelledby={titleId}>
      <h2 id={titleId}>Nuevo asunto</h2>
      {formError ? <Alert title="No se pudo crear el asunto">{formError}</Alert> : null}
      {clients.isError ? (
        <Alert title="No se pudieron cargar los clientes">{userMessage(clients.error)}</Alert>
      ) : null}
      <form onSubmit={handleSubmit} noValidate>
        <SelectField
          ref={clientRef}
          label="Cliente"
          name="clientId"
          required
          options={clientOptions}
          hint={
            noClients ? 'No hay clientes registrados: crea uno en la pantalla Clientes.' : undefined
          }
          value={clientId}
          onChange={(e) => setClientId(e.target.value)}
          error={fieldErrors.clientId}
        />
        <TextField
          ref={titleRef}
          label="Título"
          name="title"
          required
          autoComplete="off"
          hint={`Hasta ${MATTER_TITLE_MAX} caracteres.`}
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          error={fieldErrors.title}
        />
        <TextAreaField
          ref={descriptionRef}
          label="Descripción (opcional)"
          name="description"
          rows={4}
          hint={`Hasta ${MATTER_DESCRIPTION_MAX} caracteres.`}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          error={fieldErrors.description}
        />
        <p className="field__hint">
          El código (AS-AAAA-NNNN) y el estado Abierto los asigna el sistema al guardar.
        </p>
        <div className="form-actions">
          <Button type="submit" busy={createMatter.isPending}>
            {createMatter.isPending ? 'Guardando…' : 'Guardar asunto'}
          </Button>
          <Button variant="outline" onClick={onCancel}>
            Cancelar
          </Button>
        </div>
      </form>
    </section>
  );
}
