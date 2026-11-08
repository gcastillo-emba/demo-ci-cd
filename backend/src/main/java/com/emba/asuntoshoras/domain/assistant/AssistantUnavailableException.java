package com.emba.asuntoshoras.domain.assistant;

import com.emba.asuntoshoras.domain.shared.DomainException;

/**
 * El proveedor del asistente no respondió en el tiempo límite, falló o devolvió un texto vacío
 * ({@code 503 assistant-unavailable}, design D6). Nunca envuelve el mensaje ni la causa del
 * proveedor, que podrían contener el texto de la nota (design D9).
 */
public class AssistantUnavailableException extends DomainException {

  public static final String CODE = "assistant-unavailable";

  public AssistantUnavailableException() {
    super(
        CODE,
        "El asistente no está disponible en este momento; puedes escribir la descripción"
            + " manualmente.");
  }
}
