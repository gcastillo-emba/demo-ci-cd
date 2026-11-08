package com.emba.asuntoshoras.domain.matter;

import com.emba.asuntoshoras.domain.shared.DomainException;

/** Se intentó cerrar un asunto que ya está cerrado. */
public class MatterAlreadyClosedException extends DomainException {

  public static final String CODE = "matter-already-closed";

  public MatterAlreadyClosedException() {
    super(CODE, "El asunto ya está cerrado y no puede volver a cerrarse.");
  }
}
