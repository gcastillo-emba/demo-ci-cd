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

  /** Error de un campo: nombre del campo y mensaje fijo en español. */
  public record Violation(String field, String message) {

    public Violation {
      Objects.requireNonNull(field, "field");
      Objects.requireNonNull(message, "message");
    }
  }
}
