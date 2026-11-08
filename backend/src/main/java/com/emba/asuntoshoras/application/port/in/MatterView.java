package com.emba.asuntoshoras.application.port.in;

import com.emba.asuntoshoras.domain.matter.Matter;
import java.util.Objects;

/** Asunto con el nombre de su cliente, tal como lo exponen las consultas. */
public record MatterView(Matter matter, String clientName) {

  public MatterView {
    Objects.requireNonNull(matter, "matter");
    Objects.requireNonNull(clientName, "clientName");
  }
}
