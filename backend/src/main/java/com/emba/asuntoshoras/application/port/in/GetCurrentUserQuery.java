package com.emba.asuntoshoras.application.port.in;

import com.emba.asuntoshoras.domain.user.User;
import java.util.UUID;

/** Consulta: usuario dueño del token. */
public interface GetCurrentUserQuery {

  /**
   * @throws com.emba.asuntoshoras.domain.user.UserNotFoundException si no existe o está inactivo
   */
  User getCurrentUser(UUID userId);
}
