package com.emba.asuntoshoras.domain.shared;

/**
 * Un valor no cumple una invariante del dominio. El adaptador web la publica como {@code 400
 * validation-error} con {@code errors[{field, message}]}, igual que la Bean Validation.
 */
public class InvalidValueException extends DomainException {

  public static final String CODE = "validation-error";

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
