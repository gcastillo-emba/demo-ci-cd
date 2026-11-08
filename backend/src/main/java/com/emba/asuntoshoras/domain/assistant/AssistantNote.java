package com.emba.asuntoshoras.domain.assistant;

import com.emba.asuntoshoras.domain.shared.DomainValidationException;
import com.emba.asuntoshoras.domain.shared.DomainValidationException.Violation;
import java.util.List;

/**
 * Nota libre del Abogado para el asistente de descripción, ya recortada. Tiene de 1 a {@link
 * #MAX_LENGTH} caracteres Unicode (code points) tras el recorte (add-description-assistant,
 * Supuesto 6).
 */
public record AssistantNote(String value) {

  public static final int MAX_LENGTH = 500;
  public static final String FIELD = "note";
  public static final String EMPTY_CODE = "assistant-note-empty";
  public static final String TOO_LONG_CODE = "assistant-note-too-long";

  static final String EMPTY_MESSAGE = "Escribe una nota breve antes de pedir una sugerencia.";
  static final String TOO_LONG_MESSAGE = "La nota admite como máximo 500 caracteres.";

  public AssistantNote {
    if (value == null || value.isBlank()) {
      throw empty();
    }
    if (!value.equals(value.strip())) {
      throw new IllegalArgumentException("La nota debe llegar recortada");
    }
    if (codePoints(value) > MAX_LENGTH) {
      throw tooLong();
    }
  }

  /**
   * Recorta y valida una nota recibida.
   *
   * @throws DomainValidationException con {@code errors[].code} {@code assistant-note-empty} si es
   *     nula, vacía o solo espacios, o {@code assistant-note-too-long} si supera 500 caracteres
   */
  public static AssistantNote of(String raw) {
    if (raw == null) {
      throw empty();
    }
    return new AssistantNote(raw.strip());
  }

  /** Longitud en caracteres Unicode (code points). */
  public int length() {
    return codePoints(value);
  }

  /** Longitud en code points de un texto (0 si es nulo). */
  public static int codePoints(String text) {
    return text == null ? 0 : text.codePointCount(0, text.length());
  }

  private static DomainValidationException empty() {
    return new DomainValidationException(List.of(new Violation(FIELD, EMPTY_MESSAGE, EMPTY_CODE)));
  }

  private static DomainValidationException tooLong() {
    return new DomainValidationException(
        List.of(new Violation(FIELD, TOO_LONG_MESSAGE, TOO_LONG_CODE)));
  }

  @Override
  public String toString() {
    // Nunca el texto de la nota (Privacidad en logs, design D9).
    return "AssistantNote[length=" + length() + "]";
  }
}
