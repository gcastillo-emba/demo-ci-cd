package com.emba.asuntoshoras.application.service;

import com.emba.asuntoshoras.application.port.in.CurrentUser;
import com.emba.asuntoshoras.domain.shared.ForbiddenException;
import com.emba.asuntoshoras.domain.user.Role;

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

  /**
   * Exige el rol Abogado (registro y edición de horas, Supuesto 1 de add-time-entries).
   *
   * @throws ForbiddenException si no hay usuario o no es {@code LAWYER}
   */
  static void requireLawyer(CurrentUser user) {
    if (user == null || user.role() != Role.LAWYER) {
      throw new ForbiddenException();
    }
  }

  /**
   * Exige un usuario autenticado (cualquier rol).
   *
   * @throws ForbiddenException si no hay usuario
   */
  static CurrentUser requireAuthenticated(CurrentUser user) {
    if (user == null) {
      throw new ForbiddenException();
    }
    return user;
  }
}
