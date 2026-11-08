package com.emba.asuntoshoras.domain.user;

import com.emba.asuntoshoras.domain.shared.InvalidValueException;
import java.util.Locale;

/** Rol del usuario: {@code PARTNER} = Socio, {@code LAWYER} = Abogado. */
public enum Role {
  PARTNER,
  LAWYER;

  /** Convierte un texto (sin distinguir mayúsculas) en un rol válido. */
  public static Role from(String value) {
    if (value == null || value.isBlank()) {
      throw new InvalidValueException("role", "El rol es obligatorio.");
    }
    try {
      return Role.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new InvalidValueException("role", "El rol no es válido.");
    }
  }

  public boolean isPartner() {
    return this == PARTNER;
  }
}
