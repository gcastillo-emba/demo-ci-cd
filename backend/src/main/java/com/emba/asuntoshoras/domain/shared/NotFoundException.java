package com.emba.asuntoshoras.domain.shared;

/** Un recurso solicitado no existe (o no es visible para quien lo pide). */
public abstract class NotFoundException extends DomainException {

  protected NotFoundException(String code, String message) {
    super(code, message);
  }
}
