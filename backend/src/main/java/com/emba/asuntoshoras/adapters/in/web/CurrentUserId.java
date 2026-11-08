package com.emba.asuntoshoras.adapters.in.web;

import java.util.UUID;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Obtiene el userId ({@code sub} del JWT) de la solicitud autenticada. */
final class CurrentUserId {

  private CurrentUserId() {}

  static UUID require() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new AuthenticationCredentialsNotFoundException("Sin autenticación");
    }
    try {
      return UUID.fromString(authentication.getName());
    } catch (IllegalArgumentException e) {
      throw new AuthenticationCredentialsNotFoundException("Sujeto del token inválido", e);
    }
  }
}
