package com.emba.asuntoshoras.application.service;

import com.emba.asuntoshoras.application.port.in.AuthenticateUseCase;
import com.emba.asuntoshoras.application.port.out.PasswordHasher;
import com.emba.asuntoshoras.application.port.out.TokenIssuer;
import com.emba.asuntoshoras.application.port.out.UserRepository;
import com.emba.asuntoshoras.domain.shared.InvalidValueException;
import com.emba.asuntoshoras.domain.user.Email;
import com.emba.asuntoshoras.domain.user.InvalidCredentialsException;
import com.emba.asuntoshoras.domain.user.User;
import java.util.Objects;
import java.util.Optional;

/**
 * Inicio de sesión. Cualquier fallo (email mal formado, inexistente, contraseña errónea o usuario
 * inactivo) produce la misma {@link InvalidCredentialsException}. Si el email no existe se ejecuta
 * igualmente una verificación de contraseña ficticia, para no revelar por tiempo qué cuentas
 * existen.
 */
public class AuthenticationService implements AuthenticateUseCase {

  private final UserRepository users;
  private final PasswordHasher passwordHasher;
  private final TokenIssuer tokenIssuer;

  public AuthenticationService(
      UserRepository users, PasswordHasher passwordHasher, TokenIssuer tokenIssuer) {
    this.users = Objects.requireNonNull(users);
    this.passwordHasher = Objects.requireNonNull(passwordHasher);
    this.tokenIssuer = Objects.requireNonNull(tokenIssuer);
  }

  @Override
  public AuthenticationResult authenticate(String email, String password) {
    if (password == null || password.isEmpty()) {
      throw new InvalidCredentialsException();
    }
    Optional<User> candidate = parseEmail(email).flatMap(users::findByEmail);
    if (candidate.isEmpty()) {
      // Anti-enumeración: se paga el mismo costo de BCrypt que con un email existente.
      passwordHasher.dummyCheck(password);
      throw new InvalidCredentialsException();
    }
    User user = candidate.get();
    if (!passwordHasher.matches(password, user.passwordHash()) || !user.isActive()) {
      throw new InvalidCredentialsException();
    }
    TokenIssuer.IssuedToken token = tokenIssuer.issue(user);
    return new AuthenticationResult(user, token.value(), token.expiresIn().toSeconds());
  }

  private static Optional<Email> parseEmail(String email) {
    try {
      return Optional.of(Email.of(email));
    } catch (InvalidValueException e) {
      return Optional.empty();
    }
  }
}
