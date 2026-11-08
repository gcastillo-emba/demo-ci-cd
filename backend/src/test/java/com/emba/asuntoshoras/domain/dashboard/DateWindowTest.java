package com.emba.asuntoshoras.domain.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.emba.asuntoshoras.domain.shared.DomainValidationException;
import com.emba.asuntoshoras.domain.shared.DomainValidationException.Violation;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/** Reglas de la ventana del tablero con {@link Clock#fixed} (tarea 2.1, design D2). */
class DateWindowTest {

  private static final ZoneId LA_PAZ = ZoneId.of("America/La_Paz");

  private static LocalDate d(String iso) {
    return LocalDate.parse(iso);
  }

  private static List<Violation> violations(ThrowingCallable call) {
    Throwable thrown = catchThrowable(call);
    assertThat(thrown).isInstanceOf(DomainValidationException.class);
    DomainValidationException ex = (DomainValidationException) thrown;
    assertThat(ex.code()).isEqualTo("validation-error");
    return ex.violations();
  }

  // Scenario: Límites de la ventana por defecto
  @Test
  void defaultWindowIsTodayMinus29ToToday() {
    Clock clock = Clock.fixed(Instant.parse("2026-11-07T14:00:00Z"), LA_PAZ);

    DateWindow window = DateWindow.lastDays(clock, LA_PAZ, DateWindow.DEFAULT_DAYS);

    assertThat(window.from()).isEqualTo(d("2026-10-09"));
    assertThat(window.to()).isEqualTo(d("2026-11-07"));
    assertThat(window.days()).isEqualTo(30);
    assertThat(window.contains(d("2026-10-09"))).isTrue();
    assertThat(window.contains(d("2026-11-07"))).isTrue();
    assertThat(window.contains(d("2026-10-08"))).isFalse();
    assertThat(window.contains(d("2026-11-08"))).isFalse();
    assertThat(window.contains(null)).isFalse();
  }

  // Scenario: "Hoy" se calcula en la zona de la firma y no en UTC
  @Test
  void todayIsComputedInFirmZoneNotUtc() {
    // 2026-11-08T02:00Z = 2026-11-07 22:00 en La Paz; el reloj puede venir en UTC.
    Clock utcClock = Clock.fixed(Instant.parse("2026-11-08T02:00:00Z"), ZoneOffset.UTC);

    DateWindow window = DateWindow.lastDays(utcClock, LA_PAZ, DateWindow.DEFAULT_DAYS);

    assertThat(window.to()).isEqualTo(d("2026-11-07"));
    assertThat(window.from()).isEqualTo(d("2026-10-09"));
    assertThat(DateWindow.lastDays(utcClock, ZoneOffset.UTC, 30).to()).isEqualTo(d("2026-11-08"));
  }

  @Test
  void lastDaysRejectsOutOfRangeLengths() {
    Clock clock = Clock.fixed(Instant.parse("2026-11-07T14:00:00Z"), LA_PAZ);
    assertThatThrownBy(() -> DateWindow.lastDays(clock, LA_PAZ, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DateWindow.lastDays(clock, LA_PAZ, 93))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(DateWindow.lastDays(clock, LA_PAZ, 1).days()).isEqualTo(1);
    assertThat(DateWindow.lastDays(clock, LA_PAZ, 92).days()).isEqualTo(92);
  }

  // Scenario: Rango válido de 92 días
  @Test
  void ninetyTwoDaysInclusiveIsValid() {
    DateWindow window = DateWindow.of(d("2026-08-08"), d("2026-11-07"));
    assertThat(window.days()).isEqualTo(92);
  }

  // Scenario: Rango mayor a 92 días
  @Test
  void ninetyThreeDaysIsRejectedOnTo() {
    assertThat(violations(() -> DateWindow.of(d("2026-08-07"), d("2026-11-07"))))
        .containsExactly(new Violation("to", DateWindow.RANGE_MESSAGE));
  }

  // Scenario: Rango de un solo día
  @Test
  void singleDayIsValid() {
    DateWindow window = DateWindow.of(d("2026-11-01"), d("2026-11-01"));
    assertThat(window.days()).isEqualTo(1);
  }

  // Scenario: from posterior a to
  @Test
  void fromAfterToIsRejectedOnFrom() {
    assertThat(violations(() -> DateWindow.of(d("2026-11-07"), d("2026-11-01"))))
        .containsExactly(new Violation("from", DateWindow.ORDER_MESSAGE));
  }

  // Scenario: Solo se envía from
  @Test
  void missingToIsReportedOnTo() {
    assertThat(violations(() -> DateWindow.of(d("2026-11-01"), null)))
        .containsExactly(new Violation("to", DateWindow.MISSING_MESSAGE));
  }

  // Scenario: Solo se envía to
  @Test
  void missingFromIsReportedOnFrom() {
    assertThat(violations(() -> DateWindow.of(null, d("2026-11-07"))))
        .containsExactly(new Violation("from", DateWindow.MISSING_MESSAGE));
  }

  @Test
  void bothMissingReportsBothFields() {
    assertThat(violations(() -> DateWindow.of(null, null)))
        .extracting(Violation::field)
        .containsExactly("from", "to");
  }

  @Test
  void futureDatesAreAllowed() {
    // Supuesto 6.
    assertThat(DateWindow.of(d("2027-01-01"), d("2027-01-31")).days()).isEqualTo(31);
  }

  @Test
  void messagesNeverEchoInput() {
    List<Violation> list = violations(() -> DateWindow.of(d("2026-08-07"), d("2026-11-07")));
    assertThat(list.get(0).message()).doesNotContain("2026");
  }
}
