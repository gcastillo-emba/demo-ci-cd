package com.emba.asuntoshoras.domain.user;

import com.emba.asuntoshoras.domain.shared.InvalidValueException;
import java.util.Locale;
import java.util.regex.Pattern;

/** Email normalizado (sin espacios, en minúsculas) con validación básica de formato. */
public record Email(String value) {

  public static final int MAX_LENGTH = 254;

  private static final Pattern FORMAT =
      Pattern.compile("^[a-z0-9._%+-]+@[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9-]+)+$");

  public Email {
    if (value == null || value.isBlank()) {
      throw new InvalidValueException("email", "El email es obligatorio.");
    }
    value = value.trim().toLowerCase(Locale.ROOT);
    if (value.length() > MAX_LENGTH || !FORMAT.matcher(value).matches()) {
      throw new InvalidValueException("email", "El email no tiene un formato válido.");
    }
  }

  public static Email of(String value) {
    return new Email(value);
  }

  @Override
  public String toString() {
    return value;
  }
}
