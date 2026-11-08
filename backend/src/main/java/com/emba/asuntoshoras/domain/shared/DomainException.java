package com.emba.asuntoshoras.domain.shared;

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
}
