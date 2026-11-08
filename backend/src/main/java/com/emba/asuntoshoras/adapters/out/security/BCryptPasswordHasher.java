package com.emba.asuntoshoras.adapters.out.security;

import com.emba.asuntoshoras.application.port.out.PasswordHasher;
import java.util.UUID;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Verificación de contraseñas con BCrypt. BCrypt solo admite 72 bytes: una contraseña más larga
 * nunca coincide (devuelve {@code false} en lugar de propagar la excepción y producir un 500).
 */
public class BCryptPasswordHasher implements PasswordHasher {

  private final BCryptPasswordEncoder encoder;

  /**
   * Hash ficticio precalculado al construir el adaptador, con el mismo costo que los hashes reales.
   * Su texto plano es aleatorio y se descarta, así que ninguna contraseña coincide con él.
   */
  private final String dummyHash;

  public BCryptPasswordHasher() {
    this(new BCryptPasswordEncoder());
  }

  BCryptPasswordHasher(BCryptPasswordEncoder encoder) {
    this.encoder = encoder;
    this.dummyHash = encoder.encode(UUID.randomUUID().toString());
  }

  @Override
  public boolean matches(String rawPassword, String passwordHash) {
    if (rawPassword == null || passwordHash == null || passwordHash.isBlank()) {
      return false;
    }
    try {
      return encoder.matches(rawPassword, passwordHash);
    } catch (IllegalArgumentException e) {
      // Contraseña de más de 72 bytes u otro dato que BCrypt rechaza: credenciales inválidas.
      return false;
    }
  }

  @Override
  public void dummyCheck(String rawPassword) {
    matches(rawPassword == null ? "" : rawPassword, dummyHash);
  }
}
