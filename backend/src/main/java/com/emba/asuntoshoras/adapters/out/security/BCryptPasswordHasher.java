package com.emba.asuntoshoras.adapters.out.security;

import com.emba.asuntoshoras.application.port.out.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Verificación de contraseñas con BCrypt. */
public class BCryptPasswordHasher implements PasswordHasher {

  private final BCryptPasswordEncoder encoder;

  public BCryptPasswordHasher() {
    this(new BCryptPasswordEncoder());
  }

  BCryptPasswordHasher(BCryptPasswordEncoder encoder) {
    this.encoder = encoder;
  }

  @Override
  public boolean matches(String rawPassword, String passwordHash) {
    if (rawPassword == null || passwordHash == null || passwordHash.isBlank()) {
      return false;
    }
    return encoder.matches(rawPassword, passwordHash);
  }
}
