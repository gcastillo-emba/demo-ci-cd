import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import { useCreateClient, type Client, type ClientCreate } from '../api/clientsMatters';
import { ApiProblem } from '../api/problem';
import { fieldMessage, userMessage } from '../api/problemMessages';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { TextField } from '../ui/TextField';

export const CLIENT_NAME_MAX = 200;
export const CLIENT_TAX_ID_MAX = 50;

interface ClientFieldErrors {
  name?: string;
  taxId?: string;
}

/** Mismos límites que la API (spec "Validación de datos del cliente"), tras recortar espacios. */
export function validateClient(name: string, taxId: string): ClientFieldErrors {
  const errors: ClientFieldErrors = {};
  const trimmedName = name.trim();
  if (trimmedName === '') errors.name = 'Ingresa el nombre del cliente.';
  else if (trimmedName.length > CLIENT_NAME_MAX)
    errors.name = `El nombre no puede superar ${CLIENT_NAME_MAX} caracteres.`;
  if (taxId !== '' && taxId.trim() === '')
    errors.taxId = 'La identificación tributaria no puede quedar en blanco.';
  else if (taxId.trim().length > CLIENT_TAX_ID_MAX)
    errors.taxId = `La identificación tributaria no puede superar ${CLIENT_TAX_ID_MAX} caracteres.`;
  return errors;
}

export interface ClientFormProps {
  id?: string;
  onCreated: (client: Client) => void;
  onCancel: () => void;
}

/** Formulario **Nuevo cliente** (solo Socio). */
export function ClientForm({ id, onCreated, onCancel }: ClientFormProps) {
  const titleId = useId();
  const [name, setName] = useState('');
  const [taxId, setTaxId] = useState('');
  const [fieldErrors, setFieldErrors] = useState<ClientFieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const nameRef = useRef<HTMLInputElement>(null);
  const taxIdRef = useRef<HTMLInputElement>(null);
  const createClient = useCreateClient();

  useEffect(() => {
    nameRef.current?.focus();
  }, []);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (createClient.isPending) return;
    setFormError(null);

    const errors = validateClient(name, taxId);
    setFieldErrors(errors);
    if (errors.name) return nameRef.current?.focus();
    if (errors.taxId) return taxIdRef.current?.focus();

    const body: ClientCreate = { name: name.trim() };
    if (taxId.trim() !== '') body.taxId = taxId.trim();

    try {
      const client = await createClient.mutateAsync(body);
      onCreated(client);
    } catch (error) {
      if (error instanceof ApiProblem && error.code === 'client-tax-id-taken') {
        setFieldErrors({ taxId: userMessage(error) });
        taxIdRef.current?.focus();
        return;
      }
      if (error instanceof ApiProblem) {
        setFieldErrors({ name: fieldMessage(error, 'name'), taxId: fieldMessage(error, 'taxId') });
      }
      setFormError(userMessage(error));
    }
  }

  return (
    <section id={id} className="panel" aria-labelledby={titleId}>
      <h2 id={titleId}>Nuevo cliente</h2>
      {formError ? <Alert title="No se pudo crear el cliente">{formError}</Alert> : null}
      <form onSubmit={handleSubmit} noValidate>
        <TextField
          ref={nameRef}
          label="Nombre"
          name="name"
          required
          autoComplete="off"
          hint={`Hasta ${CLIENT_NAME_MAX} caracteres.`}
          value={name}
          onChange={(e) => setName(e.target.value)}
          error={fieldErrors.name}
        />
        <TextField
          ref={taxIdRef}
          label="Identificación tributaria (opcional)"
          name="taxId"
          autoComplete="off"
          hint={`NIT u otra identificación, hasta ${CLIENT_TAX_ID_MAX} caracteres.`}
          value={taxId}
          onChange={(e) => setTaxId(e.target.value)}
          error={fieldErrors.taxId}
        />
        <div className="form-actions">
          <Button type="submit" busy={createClient.isPending}>
            {createClient.isPending ? 'Guardando…' : 'Guardar cliente'}
          </Button>
          <Button variant="outline" onClick={onCancel}>
            Cancelar
          </Button>
        </div>
      </form>
    </section>
  );
}
