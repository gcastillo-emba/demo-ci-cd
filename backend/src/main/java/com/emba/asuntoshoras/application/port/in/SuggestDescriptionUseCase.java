package com.emba.asuntoshoras.application.port.in;

import java.util.Objects;
import java.util.UUID;

/** Caso de uso: sugerir la descripción de un registro de horas a partir de una nota libre. */
public interface SuggestDescriptionUseCase {

  /**
   * Devuelve una sugerencia no persistida. Orden de verificaciones (design D2): indicador → rol
   * {@code LAWYER} → nota → asunto → proveedor.
   */
  Suggestion suggest(CurrentUser user, SuggestionRequest request);

  /** Nota cruda (sin recortar) y asunto opcional. */
  record SuggestionRequest(String note, UUID matterId) {

    @Override
    public String toString() {
      // Nunca el texto de la nota (design D9).
      return "SuggestionRequest[noteLength="
          + (note == null ? 0 : note.length())
          + ", matterId="
          + matterId
          + "]";
    }
  }

  /** Sugerencia normalizada y nombre del proveedor que la generó. */
  record Suggestion(String text, String provider) {

    public Suggestion {
      Objects.requireNonNull(text, "text");
      Objects.requireNonNull(provider, "provider");
    }

    @Override
    public String toString() {
      return "Suggestion[length=" + text.length() + ", provider=" + provider + "]";
    }
  }
}
