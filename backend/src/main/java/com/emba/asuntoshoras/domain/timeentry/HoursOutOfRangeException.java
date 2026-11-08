package com.emba.asuntoshoras.domain.timeentry;

import com.emba.asuntoshoras.domain.shared.DomainException;

/** Las horas no son mayores que 0 o superan 24. */
public class HoursOutOfRangeException extends DomainException {

  public static final String CODE = "hours-out-of-range";

  public HoursOutOfRangeException() {
    super(CODE, "Las horas deben ser mayores que 0 y como máximo 24.");
  }
}
