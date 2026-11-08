package com.emba.asuntoshoras.domain.timeentry;

import com.emba.asuntoshoras.domain.shared.DomainException;

/** Un abogado intentó editar un registro de otro abogado. */
public class NotEntryOwnerException extends DomainException {

  public static final String CODE = "not-entry-owner";

  public NotEntryOwnerException() {
    super(CODE, "Solo el autor puede editar este registro de horas.");
  }
}
