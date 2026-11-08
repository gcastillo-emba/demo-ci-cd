package com.emba.asuntoshoras.application.port.in;

import com.emba.asuntoshoras.domain.user.User;

/** Caso de uso: iniciar sesión con email y contraseña. */
public interface AuthenticateUseCase {

  /**
   * Valida las credenciales y emite un token.
   *
   * @throws com.emba.asuntoshoras.domain.user.InvalidCredentialsException si no son válidas
   */
  AuthenticationResult authenticate(String email, String password);

  /** Resultado de un inicio de sesión exitoso. */
  record AuthenticationResult(User user, String accessToken, long expiresInSeconds) {}
}
