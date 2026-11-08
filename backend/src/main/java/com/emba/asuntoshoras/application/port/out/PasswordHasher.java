package com.emba.asuntoshoras.application.port.out;

/** Puerto de salida: verificación de contraseñas contra su hash. */
public interface PasswordHasher {

  /**
   * Indica si la contraseña coincide con el hash. Nunca lanza por el contenido de la contraseña
   * (por ejemplo, si supera el límite del algoritmo): en ese caso devuelve {@code false}.
   */
  boolean matches(String rawPassword, String passwordHash);

  /**
   * Ejecuta una verificación con el mismo costo que {@link #matches} contra un hash ficticio
   * constante y descarta el resultado. Se usa cuando el email no existe, para que el tiempo de
   * respuesta no revele qué cuentas existen (anti-enumeración, ADR-006).
   */
  void dummyCheck(String rawPassword);
}
