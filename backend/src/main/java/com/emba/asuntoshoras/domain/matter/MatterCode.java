package com.emba.asuntoshoras.domain.matter;

import com.emba.asuntoshoras.domain.shared.InvalidValueException;
import java.util.regex.Pattern;

/**
 * Código de asunto {@code AS-YYYY-NNNN}: año de creación (zona de la firma) y correlativo anual de
 * 4 dígitos con ceros a la izquierda, de 0001 a 9999 (Supuesto S4).
 */
public record MatterCode(String value) {

  public static final int MAX_SEQUENCE = 9999;

  private static final Pattern FORMAT = Pattern.compile("^AS-\\d{4}-\\d{4}$");

  public MatterCode {
    if (value == null || !FORMAT.matcher(value).matches()) {
      throw new InvalidValueException(
          "code", "El código del asunto no tiene el formato AS-YYYY-NNNN.");
    }
    if (Integer.parseInt(value.substring(8)) < 1) {
      throw new InvalidValueException("code", "El correlativo del código empieza en 0001.");
    }
  }

  /**
   * Formatea el código de un año y correlativo.
   *
   * @throws InvalidValueException si el año no tiene 4 dígitos o el correlativo está fuera de
   *     1..9999
   */
  public static MatterCode of(int year, int sequence) {
    if (year < 1000 || year > 9999) {
      throw new InvalidValueException("code", "El año del código debe tener 4 dígitos.");
    }
    if (sequence < 1 || sequence > MAX_SEQUENCE) {
      throw new InvalidValueException(
          "code", "Se alcanzó el máximo de 9999 asuntos para el año " + year + ".");
    }
    return new MatterCode(String.format("AS-%04d-%04d", year, sequence));
  }

  /** Año del código. */
  public int year() {
    return Integer.parseInt(value.substring(3, 7));
  }

  /** Correlativo anual del código. */
  public int sequence() {
    return Integer.parseInt(value.substring(8));
  }

  @Override
  public String toString() {
    return value;
  }
}
