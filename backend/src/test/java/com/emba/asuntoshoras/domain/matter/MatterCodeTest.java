package com.emba.asuntoshoras.domain.matter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emba.asuntoshoras.domain.shared.InvalidValueException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MatterCodeTest {

  // Scenario: Primer asunto del año
  @Test
  void firstSequenceOfTheYearIsPaddedWithZeros() {
    MatterCode code = MatterCode.of(2026, 1);

    assertThat(code.value()).isEqualTo("AS-2026-0001");
    assertThat(code.year()).isEqualTo(2026);
    assertThat(code.sequence()).isEqualTo(1);
    assertThat(code).hasToString("AS-2026-0001");
  }

  // Scenario: Correlativo siguiente
  @Test
  void formatsTwoAndFourDigitSequences() {
    assertThat(MatterCode.of(2026, 42).value()).isEqualTo("AS-2026-0042");
    assertThat(MatterCode.of(2026, 9999).value()).isEqualTo("AS-2026-9999");
  }

  @Test
  void sequenceAbove9999IsRejected() {
    assertThatThrownBy(() -> MatterCode.of(2026, 10000))
        .isInstanceOf(InvalidValueException.class)
        .hasMessageContaining("9999");
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1})
  void sequenceBelowOneIsRejected(int sequence) {
    assertThatThrownBy(() -> MatterCode.of(2026, sequence))
        .isInstanceOf(InvalidValueException.class);
  }

  @ParameterizedTest
  @ValueSource(ints = {999, 10000})
  void yearMustHaveFourDigits(int year) {
    assertThatThrownBy(() -> MatterCode.of(year, 1)).isInstanceOf(InvalidValueException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "AS-26-0001", "AS-2026-001", "XX-2026-0001", "as-2026-0001"})
  void parsingRejectsMalformedCodes(String value) {
    assertThatThrownBy(() -> new MatterCode(value))
        .isInstanceOf(InvalidValueException.class)
        .extracting(e -> ((InvalidValueException) e).field())
        .isEqualTo("code");
  }

  @Test
  void parsingRejectsNullAndZeroSequence() {
    assertThatThrownBy(() -> new MatterCode(null)).isInstanceOf(InvalidValueException.class);
    assertThatThrownBy(() -> new MatterCode("AS-2026-0000"))
        .isInstanceOf(InvalidValueException.class);
  }

  @Test
  void parsesValidCode() {
    MatterCode code = new MatterCode("AS-2027-0815");

    assertThat(code.year()).isEqualTo(2027);
    assertThat(code.sequence()).isEqualTo(815);
  }
}
