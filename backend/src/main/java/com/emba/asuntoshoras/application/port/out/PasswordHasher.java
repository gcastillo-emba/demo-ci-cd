package com.emba.asuntoshoras.application.port.out;

/** Puerto de salida: verificación de contraseñas contra su hash. */
public interface PasswordHasher {

  boolean matches(String rawPassword, String passwordHash);
}
