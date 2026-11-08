package com.emba.asuntoshoras.domain.timeentry;

import com.emba.asuntoshoras.domain.shared.DomainException;

/** Se intentó registrar o editar horas en un asunto {@code CLOSED}. */
public class MatterClosedException extends DomainException {

  public static final String CODE = "matter-closed";

  public MatterClosedException() {
    super(CODE, "No se pueden registrar ni editar horas en un asunto cerrado.");
  }
}
