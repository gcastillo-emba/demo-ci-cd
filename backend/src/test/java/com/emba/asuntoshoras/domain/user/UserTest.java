package com.emba.asuntoshoras.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emba.asuntoshoras.domain.shared.InvalidValueException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserTest {

  private static final UUID ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
  private static final Email EMAIL = Email.of("socia@asuntos.test");
  private static final String HASH = "$2a$10$hash-sintetico";

  @Test
  void restoresValidUser() {
    User user = User.restore(ID, EMAIL, "  Socia Demo ", HASH, Role.PARTNER, true);

    assertThat(user.id()).isEqualTo(ID);
    assertThat(user.email()).isEqualTo(EMAIL);
    assertThat(user.fullName()).isEqualTo("Socia Demo");
    assertThat(user.passwordHash()).isEqualTo(HASH);
    assertThat(user.role()).isEqualTo(Role.PARTNER);
    assertThat(user.isActive()).isTrue();
    assertThat(user.isPartner()).isTrue();
  }

  @Test
  void toStringNeverExposesEmailOrHash() {
    User user = User.restore(ID, EMAIL, "Socia Demo", HASH, Role.LAWYER, false);

    assertThat(user.toString())
        .contains(ID.toString())
        .doesNotContain("socia@asuntos.test")
        .doesNotContain(HASH)
        .doesNotContain("Socia Demo");
    assertThat(user.isPartner()).isFalse();
  }

  @Test
  void equalityIsByIdentity() {
    User a = User.restore(ID, EMAIL, "Socia Demo", HASH, Role.PARTNER, true);
    User b = User.restore(ID, Email.of("otra@asuntos.test"), "Otra", HASH, Role.LAWYER, false);
    User c =
        User.restore(
            UUID.fromString("00000000-0000-4000-8000-000000000002"),
            EMAIL,
            "Socia Demo",
            HASH,
            Role.PARTNER,
            true);

    assertThat(a).isEqualTo(a).isEqualTo(b).hasSameHashCodeAs(b).isNotEqualTo(c).isNotEqualTo("x");
  }

  @Test
  void rejectsMissingId() {
    assertInvalid(() -> User.restore(null, EMAIL, "Socia Demo", HASH, Role.PARTNER, true), "id");
  }

  @Test
  void rejectsMissingEmail() {
    assertInvalid(() -> User.restore(ID, null, "Socia Demo", HASH, Role.PARTNER, true), "email");
  }

  @Test
  void rejectsBlankFullName() {
    assertInvalid(() -> User.restore(ID, EMAIL, " ", HASH, Role.PARTNER, true), "fullName");
    assertInvalid(() -> User.restore(ID, EMAIL, null, HASH, Role.PARTNER, true), "fullName");
  }

  @Test
  void rejectsTooLongFullName() {
    assertInvalid(
        () -> User.restore(ID, EMAIL, "x".repeat(121), HASH, Role.PARTNER, true), "fullName");
  }

  @Test
  void rejectsMissingPasswordHash() {
    assertInvalid(
        () -> User.restore(ID, EMAIL, "Socia Demo", "", Role.PARTNER, true), "passwordHash");
    assertInvalid(
        () -> User.restore(ID, EMAIL, "Socia Demo", null, Role.PARTNER, true), "passwordHash");
  }

  @Test
  void rejectsMissingRole() {
    assertInvalid(() -> User.restore(ID, EMAIL, "Socia Demo", HASH, null, true), "role");
  }

  private static void assertInvalid(Runnable action, String field) {
    assertThatThrownBy(action::run)
        .isInstanceOf(InvalidValueException.class)
        .satisfies(e -> assertThat(((InvalidValueException) e).field()).isEqualTo(field));
  }
}
