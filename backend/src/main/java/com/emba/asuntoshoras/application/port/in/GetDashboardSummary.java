package com.emba.asuntoshoras.application.port.in;

import com.emba.asuntoshoras.domain.dashboard.DashboardSummary;
import java.time.LocalDate;

/**
 * Caso de uso del tablero (add-hours-dashboard): resumen de horas aprobadas y pendientes por asunto
 * y por abogado en una ventana de fechas, con alcance por rol (design D3).
 */
public interface GetDashboardSummary {

  /**
   * Sin {@code from} ni {@code to} usa los últimos 30 días en la zona de la firma; con ambos usa
   * {@code [from, to]}. El Socio ve todo; el Abogado solo sus registros y siempre exactamente una
   * fila propia en {@code byLawyer}.
   *
   * @throws com.emba.asuntoshoras.domain.shared.DomainValidationException si solo viene una fecha,
   *     {@code from > to} o el rango supera 92 días
   */
  DashboardSummary getDashboardSummary(CurrentUser user, LocalDate from, LocalDate to);
}
