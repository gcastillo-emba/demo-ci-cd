package com.emba.asuntoshoras.domain.timeentry;

import com.emba.asuntoshoras.domain.shared.InvalidValueException;
import java.math.BigDecimal;

/**
 * Horas de un registro, guardadas como décimas enteras (design D1): {@code 1.5 h = 15}. Una
 * cantidad válida de un registro está entre {@value #MIN_TENTHS} y {@value #MAX_TENTHS} décimas;
 * las sumas diarias usan {@link #sumOfTenths(long)} y pueden ser 0.
 */
public record Hours(int tenths) {

  public static final int MIN_TENTHS = 1;
  public static final int MAX_TENTHS = 240;

  private static final BigDecimal TEN = BigDecimal.TEN;
  private static final BigDecimal MAX = BigDecimal.valueOf(MAX_TENTHS);

  public static final Hours ZERO = new Hours(0);

  public Hours {
    if (tenths < 0) {
      throw new InvalidValueException("hours", "Las horas no pueden ser negativas.");
    }
  }

  /**
   * Convierte el valor crudo recibido por la API. Evalúa primero el incremento y después el rango.
   *
   * @throws InvalidValueException si el valor es nulo
   * @throws HoursInvalidIncrementException si tiene más de un decimal significativo
   * @throws HoursOutOfRangeException si no es mayor que 0 o supera 24
   */
  public static Hours of(BigDecimal value) {
    if (value == null) {
      throw new InvalidValueException("hours", "Las horas son obligatorias.");
    }
    BigDecimal scaled = value.multiply(TEN).stripTrailingZeros();
    if (scaled.scale() > 0) {
      throw new HoursInvalidIncrementException();
    }
    if (scaled.signum() <= 0 || scaled.compareTo(MAX) > 0) {
      throw new HoursOutOfRangeException();
    }
    return new Hours(scaled.intValueExact());
  }

  /**
   * Horas de un registro a partir de décimas ya validadas (p. ej. desde persistencia).
   *
   * @throws HoursOutOfRangeException si quedan fuera de 1..240 décimas
   */
  public static Hours ofTenths(int tenths) {
    if (tenths < MIN_TENTHS || tenths > MAX_TENTHS) {
      throw new HoursOutOfRangeException();
    }
    return new Hours(tenths);
  }

  /** Suma de horas de un día (puede ser 0 o superar 24 si los datos previos lo hicieran). */
  public static Hours sumOfTenths(long tenths) {
    if (tenths > Integer.MAX_VALUE) {
      throw new InvalidValueException("hours", "La suma de horas es demasiado grande.");
    }
    return new Hours((int) tenths);
  }

  public Hours plus(Hours other) {
    return new Hours(Math.addExact(tenths, other.tenths));
  }

  public boolean exceeds(Hours limit) {
    return tenths > limit.tenths;
  }

  /** Valor decimal para la API: entero si no tiene décimas ({@code 24}), si no con un decimal. */
  public BigDecimal toBigDecimal() {
    return tenths % 10 == 0 ? BigDecimal.valueOf(tenths / 10) : BigDecimal.valueOf(tenths, 1);
  }

  @Override
  public String toString() {
    return toBigDecimal().toPlainString() + " h";
  }
}
