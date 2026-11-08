package com.emba.asuntoshoras.domain.dashboard;

import java.util.Objects;
import java.util.UUID;

/** Fila del resumen por asunto: horas contadas de un asunto en la ventana y el alcance. */
public record MatterHours(UUID matterId, String matterCode, String matterTitle, HoursTotals hours) {

  public MatterHours {
    Objects.requireNonNull(matterId, "matterId");
    Objects.requireNonNull(matterCode, "matterCode");
    Objects.requireNonNull(matterTitle, "matterTitle");
    Objects.requireNonNull(hours, "hours");
  }
}
