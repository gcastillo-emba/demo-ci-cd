package com.emba.asuntoshoras.domain.client;

import com.emba.asuntoshoras.domain.shared.NotFoundException;
import java.util.UUID;

/** El cliente solicitado no existe. */
public class ClientNotFoundException extends NotFoundException {

  public static final String CODE = "not-found";

  private final UUID clientId;

  public ClientNotFoundException(UUID clientId) {
    super(CODE, "El cliente solicitado no existe.");
    this.clientId = clientId;
  }

  public UUID clientId() {
    return clientId;
  }
}
