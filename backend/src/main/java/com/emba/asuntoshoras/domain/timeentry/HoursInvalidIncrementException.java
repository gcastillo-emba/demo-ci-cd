package com.emba.asuntoshoras.domain.timeentry;

import com.emba.asuntoshoras.domain.shared.DomainException;

/** Las horas tienen más de un decimal significativo (solo se aceptan múltiplos de 0,1). */
public class HoursInvalidIncrementException extends DomainException {

  public static final String CODE = "hours-invalid-increment";

  public HoursInvalidIncrementException() {
    super(CODE, "Las horas deben registrarse en incrementos de 0,1.");
  }
}
