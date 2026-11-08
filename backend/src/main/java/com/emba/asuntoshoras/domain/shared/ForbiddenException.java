package com.emba.asuntoshoras.domain.shared;

/**
 * El rol del usuario no permite la operación solicitada (p. ej. un Abogado que intenta escribir).
 */
public class ForbiddenException extends DomainException {

  public static final String CODE = "forbidden";

  public ForbiddenException() {
    super(CODE, "Tu rol no permite realizar esta operación.");
  }
}
