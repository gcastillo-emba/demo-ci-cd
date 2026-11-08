package com.emba.asuntoshoras.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.emba.asuntoshoras.domain.shared.NotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserExceptionsTest {

  @Test
  void invalidCredentialsHasStableCodeAndSpanishMessage() {
    InvalidCredentialsException ex = new InvalidCredentialsException();

    assertThat(ex.code()).isEqualTo("invalid-credentials");
    assertThat(ex).hasMessage("El email o la contraseña no son correctos.");
  }

  @Test
  void userNotFoundCarriesUserId() {
    UUID id = UUID.fromString("00000000-0000-4000-8000-000000000009");
    UserNotFoundException ex = new UserNotFoundException(id);

    assertThat(ex).isInstanceOf(NotFoundException.class);
    assertThat(ex.code()).isEqualTo("user-not-found");
    assertThat(ex.userId()).isEqualTo(id);
    assertThat(ex.getMessage()).doesNotContain(id.toString());
  }
}
