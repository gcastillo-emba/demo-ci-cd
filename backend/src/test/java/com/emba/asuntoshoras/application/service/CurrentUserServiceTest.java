package com.emba.asuntoshoras.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emba.asuntoshoras.application.fakes.InMemoryUserRepository;
import com.emba.asuntoshoras.domain.user.Email;
import com.emba.asuntoshoras.domain.user.Role;
import com.emba.asuntoshoras.domain.user.User;
import com.emba.asuntoshoras.domain.user.UserNotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CurrentUserServiceTest {

  private static final User LAWYER =
      User.restore(
          UUID.fromString("00000000-0000-4000-8000-000000000002"),
          Email.of("abogado1@asuntos.test"),
          "Abogado Uno",
          "hash",
          Role.LAWYER,
          true);
  private static final User INACTIVE =
      User.restore(
          UUID.fromString("00000000-0000-4000-8000-000000000005"),
          Email.of("inactivo@asuntos.test"),
          "Usuario Inactivo",
          "hash",
          Role.LAWYER,
          false);

  private final CurrentUserService service =
      new CurrentUserService(new InMemoryUserRepository().with(LAWYER).with(INACTIVE));

  @Test
  void returnsActiveUser() {
    assertThat(service.getCurrentUser(LAWYER.id())).isEqualTo(LAWYER);
  }

  @Test
  void unknownUserIsNotFound() {
    UUID unknown = UUID.fromString("00000000-0000-4000-8000-000000000099");
    assertThatThrownBy(() -> service.getCurrentUser(unknown))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void inactiveUserIsNotFound() {
    assertThatThrownBy(() -> service.getCurrentUser(INACTIVE.id()))
        .isInstanceOf(UserNotFoundException.class);
  }
}
