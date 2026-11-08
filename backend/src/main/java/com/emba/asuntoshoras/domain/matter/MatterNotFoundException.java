package com.emba.asuntoshoras.domain.matter;

import com.emba.asuntoshoras.domain.shared.NotFoundException;
import java.util.UUID;

/** El asunto solicitado no existe. */
public class MatterNotFoundException extends NotFoundException {

  public static final String CODE = "not-found";

  private final UUID matterId;

  public MatterNotFoundException(UUID matterId) {
    super(CODE, "El asunto solicitado no existe.");
    this.matterId = matterId;
  }

  public UUID matterId() {
    return matterId;
  }
}
