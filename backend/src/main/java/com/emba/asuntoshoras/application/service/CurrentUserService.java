package com.emba.asuntoshoras.application.service;

import com.emba.asuntoshoras.application.port.in.GetCurrentUserQuery;
import com.emba.asuntoshoras.application.port.out.UserRepository;
import com.emba.asuntoshoras.domain.user.User;
import com.emba.asuntoshoras.domain.user.UserNotFoundException;
import java.util.Objects;
import java.util.UUID;

/** Devuelve el usuario dueño del token, solo si sigue existiendo y activo. */
public class CurrentUserService implements GetCurrentUserQuery {

  private final UserRepository users;

  public CurrentUserService(UserRepository users) {
    this.users = Objects.requireNonNull(users);
  }

  @Override
  public User getCurrentUser(UUID userId) {
    return users
        .findById(userId)
        .filter(User::isActive)
        .orElseThrow(() -> new UserNotFoundException(userId));
  }
}
