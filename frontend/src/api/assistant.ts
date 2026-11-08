import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from './client';
import { featuresKeys } from './features';
import type { components } from './generated/schema';
import { ApiProblem } from './problem';
import { PROBLEM_MESSAGES, userMessage } from './problemMessages';

export type DescriptionSuggestionRequest = components['schemas']['DescriptionSuggestionRequest'];
export type DescriptionSuggestionResponse = components['schemas']['DescriptionSuggestionResponse'];

/** Máximo de la nota en caracteres Unicode tras el recorte (design Supuesto 6). */
export const NOTE_MAX = 500;

export const NOTE_EMPTY_MESSAGE =
  'Escribe una nota breve en la descripción antes de pedir una sugerencia.';
export const NOTE_TOO_LONG_MESSAGE = `La nota admite como máximo ${NOTE_MAX} caracteres.`;
export const MATTER_NOT_FOUND_MESSAGE = 'El asunto seleccionado ya no existe.';
export const MATTER_INVALID_MESSAGE = 'El asunto seleccionado no es válido.';

/** Mensajes por `errors[].code` del campo `note` (design D8, Supuesto 3). */
const NOTE_CODE_MESSAGES: Readonly<Record<string, string>> = {
  'assistant-note-empty': NOTE_EMPTY_MESSAGE,
  'assistant-note-too-long': NOTE_TOO_LONG_MESSAGE,
};

/** Longitud en caracteres Unicode (code points) del texto recortado, como la mide el servidor. */
export function noteLength(note: string): number {
  return [...note.trim()].length;
}

/** Validación previa en el cliente; el servidor vuelve a validar (spec "Validación de la nota"). */
export function validateNote(note: string): string | undefined {
  const length = noteLength(note);
  if (length === 0) return NOTE_EMPTY_MESSAGE;
  if (length > NOTE_MAX) return NOTE_TOO_LONG_MESSAGE;
  return undefined;
}

/** Mensaje en español para un error del asistente; nunca muestra detalles internos. */
export function assistantErrorMessage(error: unknown): string {
  if (error instanceof ApiProblem && error.code === 'validation-error') {
    const code = error.fieldErrorCode('note');
    if (code && NOTE_CODE_MESSAGES[code]) return NOTE_CODE_MESSAGES[code];
    if (error.fieldError('matterId') !== undefined) return MATTER_INVALID_MESSAGE;
    return PROBLEM_MESSAGES['validation-error'] ?? userMessage(error);
  }
  return userMessage(error, { 'not-found': MATTER_NOT_FOUND_MESSAGE });
}

export async function suggestDescription(
  body: DescriptionSuggestionRequest,
): Promise<DescriptionSuggestionResponse> {
  const { data } = await api.POST('/api/assistant/description-suggestions', { body });
  if (data === undefined) throw new Error('Respuesta vacía de suggestDescription');
  return data;
}

/**
 * Pide una sugerencia. No invalida registros de horas: sugerir nunca guarda nada (spec "La
 * sugerencia no se persiste"). Un `404 feature-disabled` refresca los flags para ocultar el botón.
 */
export function useSuggestDescription() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: suggestDescription,
    onError: (error) =>
      error instanceof ApiProblem && error.code === 'feature-disabled'
        ? queryClient.invalidateQueries({ queryKey: featuresKeys.all })
        : undefined,
  });
}
