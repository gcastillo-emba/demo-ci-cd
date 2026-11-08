package com.emba.asuntoshoras.domain.shared;

/** Un valor no cumple una invariante del dominio. */
public class InvalidValueException extends DomainException {

  public static final String CODE = "invalid-value";

  private final String field;

  public InvalidValueException(String field, String message) {
    super(CODE, message);
    this.field = field;
  }

  /** Campo o concepto del dominio que no cumple la invariante. */
  public String field() {
    return field;
  }
}
