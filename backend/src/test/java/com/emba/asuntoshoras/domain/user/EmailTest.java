package com.emba.asuntoshoras.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emba.asuntoshoras.domain.shared.InvalidValueException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EmailTest {

  @Test
  void normalizesToTrimmedLowerCase() {
    Email email = Email.of("  Socia@Asuntos.TEST ");

    assertThat(email.value()).isEqualTo("socia@asuntos.test");
    assertThat(email).hasToString("socia@asuntos.test");
    assertThat(email).isEqualTo(new Email("socia@asuntos.test"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void rejectsMissingValue(String value) {
    assertThatThrownBy(() -> Email.of(value))
        .isInstanceOf(InvalidValueException.class)
        .hasMessage("El email es obligatorio.")
        .satisfies(
            e -> {
              InvalidValueException ex = (InvalidValueException) e;
              assertThat(ex.field()).isEqualTo("email");
              assertThat(ex.code()).isEqualTo(InvalidValueException.CODE);
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"sin-arroba", "a@b", "@asuntos.test", "a b@asuntos.test", "a@-x.test"})
  void rejectsInvalidFormat(String value) {
    assertThatThrownBy(() -> Email.of(value))
        .isInstanceOf(InvalidValueException.class)
        .hasMessage("El email no tiene un formato válido.");
  }

  @Test
  void rejectsTooLongValue() {
    String local = "a".repeat(64);
    String domain = "b".repeat(190) + ".test";
    assertThatThrownBy(() -> Email.of(local + "@" + domain))
        .isInstanceOf(InvalidValueException.class);
  }
}
