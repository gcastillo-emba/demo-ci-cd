package com.emba.asuntoshoras.domain.shared;

import java.util.Map;

/**
 * Base de las excepciones de dominio. Cada subclase concreta expone un {@code code} estable en
 * inglés (kebab-case) y un mensaje en español; el adaptador web la traduce a Problem Details.
 */
public abstract class DomainException extends RuntimeException {

  private final String code;

  protected DomainException(String code, String message) {
    super(message);
    this.code = code;
  }

  /** Código estable en inglés (kebab-case), usado como slug del Problem Details. */
  public String code() {
    return code;
  }

  /**
   * Miembros de extensión del Problem Details (RFC 9457 §3.2); vacío por defecto. Solo valores
   * calculados por el servidor, nunca texto del usuario.
   */
  public Map<String, Object> properties() {
    return Map.of();
  }
}
