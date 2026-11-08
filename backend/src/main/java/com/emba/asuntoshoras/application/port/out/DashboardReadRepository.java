package com.emba.asuntoshoras.application.port.out;

import com.emba.asuntoshoras.domain.dashboard.DashboardScope;
import com.emba.asuntoshoras.domain.dashboard.DateWindow;
import com.emba.asuntoshoras.domain.dashboard.LawyerHours;
import com.emba.asuntoshoras.domain.dashboard.MatterHours;
import java.util.List;

/**
 * Puerto de salida (read model) del tablero: agrega en la base las horas {@code APPROVED} y {@code
 * PENDING} de la ventana dentro del alcance ya resuelto. {@code REJECTED} no cuenta (design D4).
 */
public interface DashboardReadRepository {

  /**
   * Filas por asunto (orden {@code matterCode} asc) y por abogado (orden {@code lawyerName} asc),
   * solo con horas contadas en la ventana.
   */
  Aggregate aggregate(DateWindow window, DashboardScope scope);

  /** Resultado de la agregación. */
  record Aggregate(List<MatterHours> byMatter, List<LawyerHours> byLawyer) {

    public Aggregate {
      byMatter = List.copyOf(byMatter);
      byLawyer = List.copyOf(byLawyer);
    }
  }
}
