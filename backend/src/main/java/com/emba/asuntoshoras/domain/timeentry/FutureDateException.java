package com.emba.asuntoshoras.domain.timeentry;

import com.emba.asuntoshoras.domain.shared.DomainException;

/** La fecha de trabajo es posterior a hoy en la zona de la firma ({@code America/La_Paz}). */
public class FutureDateException extends DomainException {

  public static final String CODE = "future-date";

  public FutureDateException() {
    super(CODE, "No se pueden registrar horas en una fecha posterior a hoy.");
  }
}
