package com.emba.asuntoshoras.domain.timeentry;

import com.emba.asuntoshoras.domain.shared.DomainException;

/** Pasaron más de 7 días calendario (en La Paz) desde la creación del registro. */
public class EditWindowExpiredException extends DomainException {

  public static final String CODE = "edit-window-expired";

  public EditWindowExpiredException() {
    super(CODE, "El registro solo podía editarse hasta 7 días después de su creación.");
  }
}
