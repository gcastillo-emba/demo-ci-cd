package com.emba.asuntoshoras.domain.timeentry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emba.asuntoshoras.domain.shared.InvalidValueException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Horas en décimas (design D1): primero el incremento, después el rango. */
class HoursTest {

  // Scenario: Límites válidos
  @ParameterizedTest
  @ValueSource(strings = {"0.1", "23.9", "24", "24.0", "1.5", "1.50", "8", "0.10"})
  void acceptsMultiplesOfOneTenthWithinRange(String raw) {
    Hours hours = Hours.of(new BigDecimal(raw));

    assertThat(hours.tenths()).isEqualTo(new BigDecimal(raw).multiply(BigDecimal.TEN).intValue());
  }

  // Scenario: Incremento inválido
  @ParameterizedTest
  @ValueSource(strings = {"1.25", "2.05", "0.01", "24.05", "-1.25", "0.001"})
  void rejectsMoreThanOneSignificantDecimal(String raw) {
    assertThatThrownBy(() -> Hours.of(new BigDecimal(raw)))
        .isInstanceOf(HoursInvalidIncrementException.class)
        .extracting(e -> ((HoursInvalidIncrementException) e).code())
        .isEqualTo("hours-invalid-increment");
  }

  // Scenario: Cero horas / Más de 24 horas / Horas negativas
  @ParameterizedTest
  @ValueSource(strings = {"0", "0.0", "24.1", "-1", "25", "1E+3", "-0.1"})
  void rejectsValuesOutsideZeroExclusiveToTwentyFour(String raw) {
    assertThatThrownBy(() -> Hours.of(new BigDecimal(raw)))
        .isInstanceOf(HoursOutOfRangeException.class)
        .extracting(e -> ((HoursOutOfRangeException) e).code())
        .isEqualTo("hours-out-of-range");
  }

  @Test
  void incrementIsEvaluatedBeforeRange() {
    assertThatThrownBy(() -> Hours.of(new BigDecimal("30.25")))
        .isInstanceOf(HoursInvalidIncrementException.class);
  }

  @Test
  void hugeValuesAreOutOfRangeWithoutOverflow() {
    assertThatThrownBy(() -> Hours.of(new BigDecimal("1E+400")))
        .isInstanceOf(HoursOutOfRangeException.class);
  }

  @Test
  void nullIsInvalidValue() {
    assertThatThrownBy(() -> Hours.of(null))
        .isInstanceOf(InvalidValueException.class)
        .extracting(e -> ((InvalidValueException) e).field())
        .isEqualTo("hours");
  }

  @Test
  void ofTenthsValidatesRange() {
    assertThat(Hours.ofTenths(1).tenths()).isEqualTo(1);
    assertThat(Hours.ofTenths(240).tenths()).isEqualTo(240);
    assertThatThrownBy(() -> Hours.ofTenths(0)).isInstanceOf(HoursOutOfRangeException.class);
    assertThatThrownBy(() -> Hours.ofTenths(241)).isInstanceOf(HoursOutOfRangeException.class);
  }

  @Test
  void sumsAndComparisons() {
    Hours sum = Hours.sumOfTenths(200).plus(Hours.ofTenths(40));

    assertThat(sum.tenths()).isEqualTo(240);
    assertThat(sum.exceeds(DailyHoursPolicy.DAILY_LIMIT)).isFalse();
    assertThat(sum.plus(Hours.ofTenths(1)).exceeds(DailyHoursPolicy.DAILY_LIMIT)).isTrue();
    assertThat(Hours.ZERO.tenths()).isZero();
    assertThatThrownBy(() -> Hours.sumOfTenths(Long.MAX_VALUE))
        .isInstanceOf(InvalidValueException.class);
    assertThatThrownBy(() -> new Hours(-1)).isInstanceOf(InvalidValueException.class);
  }

  @Test
  void exposesWholeHoursWithoutDecimalsAndTenthsWithOneDecimal() {
    assertThat(Hours.ofTenths(240).toBigDecimal()).isEqualByComparingTo("24");
    assertThat(Hours.ofTenths(240).toBigDecimal().scale()).isZero();
    assertThat(Hours.ofTenths(15).toBigDecimal()).isEqualTo(new BigDecimal("1.5"));
    assertThat(Hours.ofTenths(15)).hasToString("1.5 h");
  }
}
