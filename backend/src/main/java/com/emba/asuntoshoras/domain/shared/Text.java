package com.emba.asuntoshoras.domain.shared;

/** Validaciones de texto compartidas por los agregados (recorte y longitud). */
public final class Text {

  private Text() {}

  /**
   * Recorta y valida un texto obligatorio.
   *
   * @throws InvalidValueException si es nulo, queda vacío tras recortar o supera {@code maxLength}
   */
  public static String required(String field, String value, int maxLength) {
    if (value == null || value.isBlank()) {
      throw new InvalidValueException(field, "El campo es obligatorio.");
    }
    return limited(field, value.trim(), maxLength);
  }

  /**
   * Recorta y valida un texto opcional que, si viene informado, no puede quedar vacío.
   *
   * @return el texto recortado o {@code null} si no se informó
   */
  public static String optionalNonBlank(String field, String value, int maxLength) {
    if (value == null) {
      return null;
    }
    if (value.isBlank()) {
      throw new InvalidValueException(field, "Si se informa, no puede estar vacío.");
    }
    return limited(field, value.trim(), maxLength);
  }

  /**
   * Valida un texto opcional libre; un texto en blanco se normaliza a {@code null}.
   *
   * @return el texto sin cambios o {@code null} si no se informó o está en blanco
   */
  public static String optional(String field, String value, int maxLength) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return limited(field, value, maxLength);
  }

  private static String limited(String field, String value, int maxLength) {
    if (value.length() > maxLength) {
      throw new InvalidValueException(field, "No puede superar " + maxLength + " caracteres.");
    }
    return value;
  }
}
