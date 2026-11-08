package com.emba.asuntoshoras.application.service;

import com.emba.asuntoshoras.application.port.in.CurrentUser;
import com.emba.asuntoshoras.application.port.in.GetDashboardSummary;
import com.emba.asuntoshoras.application.port.out.DashboardReadRepository;
import com.emba.asuntoshoras.application.port.out.DashboardReadRepository.Aggregate;
import com.emba.asuntoshoras.application.port.out.UserRepository;
import com.emba.asuntoshoras.domain.dashboard.DashboardScope;
import com.emba.asuntoshoras.domain.dashboard.DashboardSummary;
import com.emba.asuntoshoras.domain.dashboard.DateWindow;
import com.emba.asuntoshoras.domain.dashboard.LawyerHours;
import com.emba.asuntoshoras.domain.user.User;
import com.emba.asuntoshoras.domain.user.UserNotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resumen del tablero (add-hours-dashboard). La ventana se valida en el dominio ({@link
 * DateWindow}); el alcance por rol se resuelve aquí (design D3) y el adaptador solo lo aplica. Los
 * totales salen de las filas por asunto (design D4).
 */
public class DashboardQueryService implements GetDashboardSummary {

  private final DashboardReadRepository dashboard;
  private final UserRepository users;
  private final Clock clock;

  public DashboardQueryService(
      DashboardReadRepository dashboard, UserRepository users, Clock clock) {
    this.dashboard = Objects.requireNonNull(dashboard);
    this.users = Objects.requireNonNull(users);
    this.clock = Objects.requireNonNull(clock);
  }

  /**
   * Las dos agregaciones (por asunto y por abogado) corren en una única transacción {@code
   * REPEATABLE READ} de solo lectura: ven la misma instantánea y sus sumas coinciden con los
   * totales aunque haya escrituras concurrentes.
   */
  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public DashboardSummary getDashboardSummary(CurrentUser user, LocalDate from, LocalDate to) {
    CurrentUser caller = Authorization.requireAuthenticated(user);
    DateWindow window =
        from == null && to == null
            ? DateWindow.lastDays(clock, clock.getZone(), DateWindow.DEFAULT_DAYS)
            : DateWindow.of(from, to);
    // Supuesto 12: no hay parámetro de abogado; el Abogado nunca puede ampliar su alcance.
    DashboardScope scope =
        caller.isPartner() ? DashboardScope.all() : DashboardScope.own(caller.userId());

    Aggregate aggregate = dashboard.aggregate(window, scope);
    List<LawyerHours> byLawyer = aggregate.byLawyer();
    if (scope instanceof DashboardScope.Own own) {
      byLawyer = ownRow(own, byLawyer);
    }
    return DashboardSummary.of(window, aggregate.byMatter(), byLawyer);
  }

  /**
   * Supuesto 1: el Abogado recibe siempre exactamente una fila, la suya; con ceros si no tiene
   * horas contadas en la ventana.
   */
  private List<LawyerHours> ownRow(DashboardScope.Own own, List<LawyerHours> rows) {
    return rows.stream()
        .filter(row -> row.lawyerId().equals(own.lawyerId()))
        .findFirst()
        .map(List::of)
        .orElseGet(() -> List.of(LawyerHours.empty(own.lawyerId(), lawyerName(own))));
  }

  private String lawyerName(DashboardScope.Own own) {
    return users
        .findById(own.lawyerId())
        .map(User::fullName)
        .orElseThrow(() -> new UserNotFoundException(own.lawyerId()));
  }
}
