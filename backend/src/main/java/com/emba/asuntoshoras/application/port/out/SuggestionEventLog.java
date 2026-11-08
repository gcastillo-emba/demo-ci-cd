package com.emba.asuntoshoras.application.port.out;

import java.util.Objects;

/**
 * Puerto de salida: un único evento de metadatos por solicitud de sugerencia (design D9). Nunca
 * recibe el texto de la nota ni el de la sugerencia, solo longitudes, proveedor, latencia y
 * resultado.
 */
public interface SuggestionEventLog {

  void record(SuggestionEvent event);

  /** Resultado de la solicitud. */
  enum Outcome {
    OK,
    VALIDATION,
    NOT_FOUND,
    UNAVAILABLE,
    DISABLED,
    FORBIDDEN;

    /** Valor en minúsculas para el log ({@code ok}, {@code not_found}, …). */
    public String value() {
      return name().toLowerCase(java.util.Locale.ROOT);
    }
  }

  /** Metadatos de una solicitud (longitudes en code points, latencia en milisegundos). */
  record SuggestionEvent(
      int noteLength, int suggestionLength, String provider, long latencyMs, Outcome outcome) {

    public SuggestionEvent {
      Objects.requireNonNull(provider, "provider");
      Objects.requireNonNull(outcome, "outcome");
    }
  }
}
