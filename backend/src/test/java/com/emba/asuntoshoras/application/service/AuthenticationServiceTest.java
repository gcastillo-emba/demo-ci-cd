package com.emba.asuntoshoras.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emba.asuntoshoras.application.fakes.FakeTokenIssuer;
import com.emba.asuntoshoras.application.fakes.InMemoryUserRepository;
import com.emba.asuntoshoras.application.fakes.PlainTextPasswordHasher;
import com.emba.asuntoshoras.application.port.in.AuthenticateUseCase.AuthenticationResult;
import com.emba.asuntoshoras.domain.user.Email;
import com.emba.asuntoshoras.domain.user.InvalidCredentialsException;
import com.emba.asuntoshoras.domain.user.Role;
import com.emba.asuntoshoras.domain.user.User;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AuthenticationServiceTest {

  private static final String PASSWORD = "Demo2026!";
  private static final User PARTNER =
      User.restore(
          UUID.fromString("00000000-0000-4000-8000-000000000001"),
          Email.of("socia@asuntos.test"),
          "Socia Demo",
          PlainTextPasswordHasher.hashOf(PASSWORD),
          Role.PARTNER,
          true);
  private static final User INACTIVE =
      User.restore(
          UUID.fromString("00000000-0000-4000-8000-000000000005"),
          Email.of("inactivo@asuntos.test"),
          "Usuario Inactivo",
          PlainTextPasswordHasher.hashOf(PASSWORD),
          Role.LAWYER,
          false);

  private FakeTokenIssuer tokenIssuer;
  private AuthenticationService service;

  @BeforeEach
  void setUp() {
    tokenIssuer = new FakeTokenIssuer();
    service =
        new AuthenticationService(
            new InMemoryUserRepository().with(PARTNER).with(INACTIVE),
            new PlainTextPasswordHasher(),
            tokenIssuer);
  }

  @Test
  void validCredentialsReturnTokenAndUser() {
    AuthenticationResult result = service.authenticate(" Socia@Asuntos.test ", PASSWORD);

    assertThat(result.user()).isEqualTo(PARTNER);
    assertThat(result.accessToken()).isEqualTo("token-" + PARTNER.id());
    assertThat(result.expiresInSeconds()).isEqualTo(7200);
    assertThat(tokenIssuer.issuedTo()).containsExactly(PARTNER);
  }

  @Test
  void wrongPasswordIsInvalidCredentials() {
    assertThatThrownBy(() -> service.authenticate("socia@asuntos.test", "otra"))
        .isInstanceOf(InvalidCredentialsException.class);
    assertThat(tokenIssuer.issuedTo()).isEmpty();
  }

  @Test
  void unknownEmailIsInvalidCredentials() {
    assertThatThrownBy(() -> service.authenticate("nadie@asuntos.test", PASSWORD))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void inactiveUserIsInvalidCredentials() {
    assertThatThrownBy(() -> service.authenticate("inactivo@asuntos.test", PASSWORD))
        .isInstanceOf(InvalidCredentialsException.class);
    assertThat(tokenIssuer.issuedTo()).isEmpty();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"no-es-email"})
  void malformedEmailIsInvalidCredentials(String email) {
    assertThatThrownBy(() -> service.authenticate(email, PASSWORD))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @ParameterizedTest
  @NullAndEmptySource
  void missingPasswordIsInvalidCredentials(String password) {
    assertThatThrownBy(() -> service.authenticate("socia@asuntos.test", password))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void requiresAllPorts() {
    assertThatNullPointerException()
        .isThrownBy(
            () -> new AuthenticationService(null, new PlainTextPasswordHasher(), tokenIssuer));
  }
}
