package com.emba.asuntoshoras.domain.shared;

import java.util.List;
import java.util.Objects;

/**
 * Una o varias reglas de validación entre campos no se cumplen (p. ej. la ventana de fechas del
 * tablero). El adaptador web la publica como {@code 400 validation-error} con {@code errors[{field,
 * message}]}, igual que la Bean Validation. Los mensajes son textos fijos: nunca copian la entrada
 * del usuario.
 */
public class DomainValidationException extends DomainException {

  public static final String CODE = "validation-error";

  private final List<Violation> violations;

  public DomainValidationException(List<Violation> violations) {
    super(CODE, "La solicitud tiene campos inválidos.");
    Objects.requireNonNull(violations, "violations");
    if (violations.isEmpty()) {
      throw new IllegalArgumentException("Se requiere al menos una violación");
    }
    this.violations = List.copyOf(violations);
  }

  /** Errores por campo, en el orden en que se detectaron. */
  public List<Violation> violations() {
    return violations;
  }

  /**
   * Error de un campo: nombre del campo, mensaje fijo en español y, opcionalmente, un código
   * estable en inglés (kebab-case) que viaja en {@code errors[].code} (add-description-assistant,
   * Supuesto 3). Sin código, el significado de {@code field} y {@code message} no cambia.
   */
  public record Violation(String field, String message, String code) {

    public Violation {
      Objects.requireNonNull(field, "field");
      Objects.requireNonNull(message, "message");
    }

    /** Violación sin código propio (C1–C3). */
    public Violation(String field, String message) {
      this(field, message, null);
    }
  }
}
