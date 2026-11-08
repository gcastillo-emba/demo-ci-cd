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
 * inactivo) produce la misma {@link InvalidCredentialsException}.
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
    User user =
        parseEmail(email)
            .flatMap(users::findByEmail)
            .filter(candidate -> passwordHasher.matches(password, candidate.passwordHash()))
            .filter(User::isActive)
            .orElseThrow(InvalidCredentialsException::new);
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
