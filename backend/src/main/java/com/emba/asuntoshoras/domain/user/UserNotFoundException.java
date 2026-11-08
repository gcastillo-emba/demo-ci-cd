package com.emba.asuntoshoras.domain.user;

import com.emba.asuntoshoras.domain.shared.NotFoundException;
import java.util.UUID;

/** El usuario solicitado no existe o está inactivo. */
public class UserNotFoundException extends NotFoundException {

  public static final String CODE = "user-not-found";

  private final UUID userId;

  public UserNotFoundException(UUID userId) {
    super(CODE, "El usuario no existe o no está activo.");
    this.userId = userId;
  }

  public UUID userId() {
    return userId;
  }
}
