package com.emba.asuntoshoras.domain.dashboard;

import java.util.Objects;
import java.util.UUID;

/** Fila del resumen por abogado: horas contadas de un abogado en la ventana y el alcance. */
public record LawyerHours(UUID lawyerId, String lawyerName, HoursTotals hours) {

  public LawyerHours {
    Objects.requireNonNull(lawyerId, "lawyerId");
    Objects.requireNonNull(lawyerName, "lawyerName");
    Objects.requireNonNull(hours, "hours");
  }

  /** Fila sin horas contadas (la fila propia del Abogado, Supuesto 1). */
  public static LawyerHours empty(UUID lawyerId, String lawyerName) {
    return new LawyerHours(lawyerId, lawyerName, HoursTotals.ZERO);
  }
}
