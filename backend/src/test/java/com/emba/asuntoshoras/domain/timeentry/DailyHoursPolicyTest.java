package com.emba.asuntoshoras.domain.timeentry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Límite diario de 24 h (la suma la calcula el puerto de persistencia). */
class DailyHoursPolicyTest {

  // Scenario: Completar exactamente 24 horas
  @Test
  void reachingExactlyTwentyFourIsAllowed() {
    DailyHoursPolicy.check(Hours.sumOfTenths(200), Hours.ofTenths(40));
    DailyHoursPolicy.check(Hours.ZERO, Hours.ofTenths(240));
  }

  // Scenario: Superar el límite diario
  @Test
  void exceedingTwentyFourReportsLimitAndAlreadyLogged() {
    assertThatThrownBy(() -> DailyHoursPolicy.check(Hours.sumOfTenths(200), Hours.ofTenths(41)))
        .isInstanceOfSatisfying(
            DailyHoursExceededException.class,
            e -> {
              assertThat(e.code()).isEqualTo("daily-hours-exceeded");
              assertThat(e.limit().tenths()).isEqualTo(240);
              assertThat(e.alreadyLogged().tenths()).isEqualTo(200);
              assertThat(e.properties())
                  .containsEntry("limitHours", new BigDecimal("24"))
                  .containsEntry("alreadyLoggedHours", new BigDecimal("20"));
              assertThat(e.getMessage())
                  .isEqualTo(
                      "Ya tienes 20 h registradas ese día; puedes registrar como máximo 4 h más.");
            });
  }

  @Test
  void messageUsesDecimalCommaAndNeverNegativeRemaining() {
    DailyHoursExceededException partial =
        new DailyHoursExceededException(DailyHoursPolicy.DAILY_LIMIT, Hours.sumOfTenths(205));
    DailyHoursExceededException over =
        new DailyHoursExceededException(DailyHoursPolicy.DAILY_LIMIT, Hours.sumOfTenths(250));

    assertThat(partial.getMessage()).contains("20,5 h registradas").contains("3,5 h más");
    assertThat(over.getMessage()).contains("como máximo 0 h más");
  }
}
