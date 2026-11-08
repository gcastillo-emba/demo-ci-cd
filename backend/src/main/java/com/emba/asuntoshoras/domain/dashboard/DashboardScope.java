package com.emba.asuntoshoras.domain.dashboard;

import java.util.Objects;
import java.util.UUID;

/**
 * Alcance ya resuelto del resumen (design D3): {@link All} para el Socio, {@link Own} para el
 * Abogado. Lo decide la capa de aplicación; el adaptador de persistencia solo lo aplica.
 */
public sealed interface DashboardScope {

  static DashboardScope all() {
    return All.INSTANCE;
  }

  static DashboardScope own(UUID lawyerId) {
    return new Own(lawyerId);
  }

  /** Todos los abogados y asuntos. */
  enum All implements DashboardScope {
    INSTANCE
  }

  /** Solo los registros cuyo autor es {@code lawyerId}. */
  record Own(UUID lawyerId) implements DashboardScope {

    public Own {
      Objects.requireNonNull(lawyerId, "lawyerId");
    }
  }
}
