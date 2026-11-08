package com.emba.asuntoshoras.domain.user;

import com.emba.asuntoshoras.domain.shared.DomainException;

/**
 * Credenciales inválidas. Se usa igual para email inexistente, contraseña errónea o usuario
 * inactivo, para no revelar qué cuentas existen.
 */
public class InvalidCredentialsException extends DomainException {

  public static final String CODE = "invalid-credentials";

  public InvalidCredentialsException() {
    super(CODE, "El email o la contraseña no son correctos.");
  }
}
