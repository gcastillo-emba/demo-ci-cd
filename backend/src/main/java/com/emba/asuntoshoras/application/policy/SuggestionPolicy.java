package com.emba.asuntoshoras.application.policy;

import com.emba.asuntoshoras.domain.assistant.AssistantUnavailableException;

/**
 * Normaliza la salida del proveedor (design D7): recorta, colapsa espacios y saltos de línea en un
 * espacio y, si supera {@value #MAX_LENGTH} caracteres Unicode, la corta en el último límite de
 * palabra que quepa (o exactamente en 300 si no hay ninguno). Un resultado vacío significa que el
 * asistente no está disponible. Clase pura, sin Spring.
 */
public final class SuggestionPolicy {

  public static final int MAX_LENGTH = 300;

  private static final String WHITESPACE_RUN = "[\\s\\p{Z}]+";

  /**
   * Aplica la política.
   *
   * @throws AssistantUnavailableException si el texto es nulo o queda vacío
   */
  public String apply(String raw) {
    if (raw == null) {
      throw new AssistantUnavailableException();
    }
    String text = raw.replaceAll(WHITESPACE_RUN, " ").strip();
    if (text.isEmpty()) {
      throw new AssistantUnavailableException();
    }
    if (text.codePointCount(0, text.length()) <= MAX_LENGTH) {
      return text;
    }
    // Se miran MAX_LENGTH + 1 code points: si el siguiente al 300 es un espacio, el prefijo de 300
    // ya termina en una palabra completa.
    int window = text.offsetByCodePoints(0, MAX_LENGTH + 1);
    int lastSpace = text.lastIndexOf(' ', window - 1);
    if (lastSpace > 0) {
      return text.substring(0, lastSpace);
    }
    return text.substring(0, text.offsetByCodePoints(0, MAX_LENGTH));
  }
}
