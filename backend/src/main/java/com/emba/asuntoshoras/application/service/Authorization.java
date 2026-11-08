package com.emba.asuntoshoras.application.service;

import com.emba.asuntoshoras.application.port.in.CurrentUser;
import com.emba.asuntoshoras.domain.shared.ForbiddenException;

/** Reglas de autorización por rol de la capa de aplicación (design D6). */
final class Authorization {

  private Authorization() {}

  /**
   * Exige el rol Socio para una escritura.
   *
   * @throws ForbiddenException si no hay usuario o no es {@code PARTNER}
   */
  static void requirePartner(CurrentUser user) {
    if (user == null || !user.isPartner()) {
      throw new ForbiddenException();
    }
  }
}
