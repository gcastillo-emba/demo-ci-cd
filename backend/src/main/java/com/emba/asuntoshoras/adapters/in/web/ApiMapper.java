package com.emba.asuntoshoras.adapters.in.web;

import com.emba.asuntoshoras.adapters.in.web.api.model.Role;
import com.emba.asuntoshoras.adapters.in.web.api.model.User;

/** Mapeo dominio → DTOs generados del contrato. */
final class ApiMapper {

  private ApiMapper() {}

  static User toDto(com.emba.asuntoshoras.domain.user.User user) {
    return new User()
        .id(user.id())
        .email(user.email().value())
        .fullName(user.fullName())
        .role(Role.fromValue(user.role().name()));
  }
}
