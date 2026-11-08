package com.emba.asuntoshoras.domain.timeentry;

import com.emba.asuntoshoras.domain.shared.DomainException;

/** El registro ya está en un estado terminal ({@code APPROVED} o {@code REJECTED}). */
public class EntryNotPendingException extends DomainException {

  public static final String CODE = "entry-not-pending";

  public EntryNotPendingException() {
    super(CODE, "El registro ya fue aprobado o rechazado; no se modificó.");
  }
}
