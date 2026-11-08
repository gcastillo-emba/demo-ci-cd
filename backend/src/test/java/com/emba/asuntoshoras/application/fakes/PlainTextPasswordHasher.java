package com.emba.asuntoshoras.application.fakes;

import com.emba.asuntoshoras.application.port.out.PasswordHasher;

/**
 * Fake: el "hash" es {@code plain:<contraseña>}. Cuenta las verificaciones (reales y ficticias)
 * para poder comprobar la medida anti-enumeración. Solo para tests.
 */
public final class PlainTextPasswordHasher implements PasswordHasher {

  private int matchesCalls;
  private int dummyChecks;

  public static String hashOf(String raw) {
    return "plain:" + raw;
  }

  @Override
  public boolean matches(String rawPassword, String passwordHash) {
    matchesCalls++;
    return hashOf(rawPassword).equals(passwordHash);
  }

  @Override
  public void dummyCheck(String rawPassword) {
    dummyChecks++;
  }

  public int matchesCalls() {
    return matchesCalls;
  }

  public int dummyChecks() {
    return dummyChecks;
  }
}
