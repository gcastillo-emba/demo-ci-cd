package com.emba.asuntoshoras.application.fakes;

import com.emba.asuntoshoras.application.port.out.PasswordHasher;

/** Fake: el "hash" es {@code plain:<contraseña>}. Solo para tests. */
public final class PlainTextPasswordHasher implements PasswordHasher {

  public static String hashOf(String raw) {
    return "plain:" + raw;
  }

  @Override
  public boolean matches(String rawPassword, String passwordHash) {
    return hashOf(rawPassword).equals(passwordHash);
  }
}
