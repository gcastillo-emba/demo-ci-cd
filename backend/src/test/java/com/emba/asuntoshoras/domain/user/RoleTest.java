package com.emba.asuntoshoras.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emba.asuntoshoras.domain.shared.InvalidValueException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class RoleTest {

  @Test
  void parsesIgnoringCaseAndSpaces() {
    assertThat(Role.from(" partner ")).isEqualTo(Role.PARTNER);
    assertThat(Role.from("LAWYER")).isEqualTo(Role.LAWYER);
  }

  @Test
  void onlyPartnerIsPartner() {
    assertThat(Role.PARTNER.isPartner()).isTrue();
    assertThat(Role.LAWYER.isPartner()).isFalse();
  }

  @ParameterizedTest
  @NullAndEmptySource
  void rejectsMissingRole(String value) {
    assertThatThrownBy(() -> Role.from(value))
        .isInstanceOf(InvalidValueException.class)
        .hasMessage("El rol es obligatorio.");
  }

  @Test
  void rejectsUnknownRole() {
    assertThatThrownBy(() -> Role.from("ADMIN"))
        .isInstanceOf(InvalidValueException.class)
        .hasMessage("El rol no es válido.");
  }
}
