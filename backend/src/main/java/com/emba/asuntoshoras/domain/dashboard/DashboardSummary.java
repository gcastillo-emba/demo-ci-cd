package com.emba.asuntoshoras.domain.dashboard;

import java.util.List;
import java.util.Objects;

/**
 * Resumen del tablero: ventana efectiva, filas por asunto y por abogado y totales. Los totales se
 * calculan sumando las filas por asunto (design D4), lo que garantiza su consistencia.
 */
public record DashboardSummary(
    DateWindow window, List<MatterHours> byMatter, List<LawyerHours> byLawyer, HoursTotals totals) {

  public DashboardSummary {
    Objects.requireNonNull(window, "window");
    Objects.requireNonNull(totals, "totals");
    byMatter = List.copyOf(byMatter);
    byLawyer = List.copyOf(byLawyer);
  }

  /** Construye el resumen con los totales derivados de {@code byMatter}. */
  public static DashboardSummary of(
      DateWindow window, List<MatterHours> byMatter, List<LawyerHours> byLawyer) {
    return new DashboardSummary(window, byMatter, byLawyer, sumOfMatters(byMatter));
  }

  /** Suma de las columnas de las filas por asunto. */
  public static HoursTotals sumOfMatters(List<MatterHours> rows) {
    return rows.stream().map(MatterHours::hours).reduce(HoursTotals.ZERO, HoursTotals::plus);
  }

  /** Suma de las columnas de las filas por abogado. */
  public static HoursTotals sumOfLawyers(List<LawyerHours> rows) {
    return rows.stream().map(LawyerHours::hours).reduce(HoursTotals.ZERO, HoursTotals::plus);
  }
}
