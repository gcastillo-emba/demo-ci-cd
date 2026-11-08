package com.emba.asuntoshoras.adapters.in.web;

import com.emba.asuntoshoras.application.port.in.CurrentUser;
import com.emba.asuntoshoras.domain.shared.InvalidValueException;
import com.emba.asuntoshoras.domain.user.Role;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Obtiene el userId ({@code sub} del JWT) y el rol de la solicitud autenticada. */
final class CurrentUserId {

  private static final String ROLE_PREFIX = "ROLE_";

  private CurrentUserId() {}

  static UUID require() {
    Authentication authentication = authentication();
    try {
      return UUID.fromString(authentication.getName());
    } catch (IllegalArgumentException e) {
      throw new AuthenticationCredentialsNotFoundException("Sujeto del token inválido", e);
    }
  }

  /** Usuario actual con su rol (claim {@code role} mapeado a {@code ROLE_*}). */
  static CurrentUser requireUser() {
    UUID userId = require();
    Role role =
        authentication().getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(a -> a != null && a.startsWith(ROLE_PREFIX))
            .map(a -> a.substring(ROLE_PREFIX.length()))
            .findFirst()
            .map(CurrentUserId::parseRole)
            .orElseThrow(() -> new AccessDeniedException("Token sin rol"));
    return new CurrentUser(userId, role);
  }

  private static Role parseRole(String value) {
    try {
      return Role.from(value);
    } catch (InvalidValueException e) {
      throw new AccessDeniedException("Rol del token inválido", e);
    }
  }

  private static Authentication authentication() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new AuthenticationCredentialsNotFoundException("Sin autenticación");
    }
    return authentication;
  }
}
