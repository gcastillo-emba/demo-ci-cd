package com.emba.asuntoshoras.application.port.in;

import com.emba.asuntoshoras.domain.user.Role;
import java.util.Objects;
import java.util.UUID;

/** Usuario autenticado que invoca un caso de uso (lo construye el adaptador web desde el JWT). */
public record CurrentUser(UUID userId, Role role) {

  public CurrentUser {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(role, "role");
  }

  public boolean isPartner() {
    return role.isPartner();
  }
}
