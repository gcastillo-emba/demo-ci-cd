package com.emba.asuntoshoras.domain.dashboard;

import com.emba.asuntoshoras.domain.shared.DomainValidationException;
import com.emba.asuntoshoras.domain.shared.DomainValidationException.Violation;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Ventana inclusiva de fechas de trabajo del tablero (add-hours-dashboard, design D2). Invariantes:
 * ambos extremos presentes, {@code from <= to} y como máximo {@value #MAX_DAYS} días contando ambos
 * extremos (Supuesto 7).
 */
public record DateWindow(LocalDate from, LocalDate to) {

  /** Ventana por defecto del tablero: últimos 30 días incluido hoy. */
  public static final int DEFAULT_DAYS = 30;

  /** Máximo de días de una ventana personalizada, contando ambos extremos. */
  public static final int MAX_DAYS = 92;

  public static final String FIELD_FROM = "from";
  public static final String FIELD_TO = "to";

  static final String MISSING_MESSAGE = "Es obligatorio cuando se informa la otra fecha del rango.";
  static final String ORDER_MESSAGE = "La fecha inicial no puede ser posterior a la final.";
  static final String RANGE_MESSAGE = "El rango no puede superar 92 días.";

  /**
   * @throws DomainValidationException si falta un extremo, {@code from > to} o el rango supera
   *     {@value #MAX_DAYS} días
   */
  public DateWindow {
    List<Violation> violations = new ArrayList<>();
    if (from == null) {
      violations.add(new Violation(FIELD_FROM, MISSING_MESSAGE));
    }
    if (to == null) {
      violations.add(new Violation(FIELD_TO, MISSING_MESSAGE));
    }
    if (violations.isEmpty()) {
      if (from.isAfter(to)) {
        // Supuesto 8: el orden invertido se reporta en "from".
        violations.add(new Violation(FIELD_FROM, ORDER_MESSAGE));
      } else if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
        // Supuesto 8: el exceso de rango se reporta en "to".
        violations.add(new Violation(FIELD_TO, RANGE_MESSAGE));
      }
    }
    if (!violations.isEmpty()) {
      throw new DomainValidationException(violations);
    }
  }

  /**
   * Ventana personalizada {@code [from, to]} (ambos inclusive).
   *
   * @throws DomainValidationException si no cumple las invariantes
   */
  public static DateWindow of(LocalDate from, LocalDate to) {
    return new DateWindow(from, to);
  }

  /**
   * Últimos {@code days} días terminando hoy, donde "hoy" es la fecha del instante del reloj en la
   * zona de la firma: {@code [hoy-(days-1), hoy]}.
   *
   * @throws IllegalArgumentException si {@code days} no está entre 1 y {@value #MAX_DAYS}
   */
  public static DateWindow lastDays(Clock clock, ZoneId firmZone, int days) {
    if (days < 1 || days > MAX_DAYS) {
      throw new IllegalArgumentException("days fuera de 1.." + MAX_DAYS);
    }
    LocalDate today = clock.instant().atZone(firmZone).toLocalDate();
    return new DateWindow(today.minusDays(days - 1L), today);
  }

  /** Cantidad de días de la ventana contando ambos extremos. */
  public long days() {
    return ChronoUnit.DAYS.between(from, to) + 1;
  }

  /** Indica si la fecha cae dentro de la ventana (inclusive). */
  public boolean contains(LocalDate date) {
    return date != null && !date.isBefore(from) && !date.isAfter(to);
  }
}
