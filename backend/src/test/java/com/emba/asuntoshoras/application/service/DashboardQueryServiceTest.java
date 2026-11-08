package com.emba.asuntoshoras.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emba.asuntoshoras.application.fakes.InMemoryDashboardReadRepository;
import com.emba.asuntoshoras.application.fakes.InMemoryDashboardReadRepository.Entry;
import com.emba.asuntoshoras.application.fakes.InMemoryUserRepository;
import com.emba.asuntoshoras.application.port.in.CurrentUser;
import com.emba.asuntoshoras.domain.dashboard.DashboardScope;
import com.emba.asuntoshoras.domain.dashboard.DashboardSummary;
import com.emba.asuntoshoras.domain.dashboard.HoursTotals;
import com.emba.asuntoshoras.domain.dashboard.LawyerHours;
import com.emba.asuntoshoras.domain.dashboard.MatterHours;
import com.emba.asuntoshoras.domain.shared.DomainValidationException;
import com.emba.asuntoshoras.domain.shared.ForbiddenException;
import com.emba.asuntoshoras.domain.timeentry.Hours;
import com.emba.asuntoshoras.domain.timeentry.TimeEntryStatus;
import com.emba.asuntoshoras.domain.user.Email;
import com.emba.asuntoshoras.domain.user.Role;
import com.emba.asuntoshoras.domain.user.User;
import com.emba.asuntoshoras.domain.user.UserNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Caso de uso del tablero con fakes en memoria y {@code Clock.fixed} en La Paz (tarea 2.4). */
class DashboardQueryServiceTest {

  private static final ZoneId LA_PAZ = ZoneId.of("America/La_Paz");

  /** Hoy es 2026-11-07 (10:00) en La Paz. */
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-07T14:00:00Z"), LA_PAZ);

  private static final UUID PARTNER = UUID.randomUUID();
  private static final UUID ANA = UUID.randomUUID();
  private static final UUID BRUNO = UUID.randomUUID();
  private static final UUID CARLA = UUID.randomUUID();
  private static final UUID MAT_1 = UUID.randomUUID();
  private static final UUID MAT_2 = UUID.randomUUID();
  private static final UUID MAT_3 = UUID.randomUUID();
  private static final UUID MAT_4 = UUID.randomUUID();

  private final InMemoryDashboardReadRepository repository = new InMemoryDashboardReadRepository();
  private final InMemoryUserRepository users =
      new InMemoryUserRepository()
          .with(user(PARTNER, "socia@x.test", "Socia Demo", Role.PARTNER))
          .with(user(ANA, "ana@x.test", "Ana Pérez", Role.LAWYER))
          .with(user(BRUNO, "bruno@x.test", "Bruno Rojas", Role.LAWYER))
          .with(user(CARLA, "carla@x.test", "Carla Vargas", Role.LAWYER));
  private final DashboardQueryService service = new DashboardQueryService(repository, users, CLOCK);

  private static User user(UUID id, String email, String name, Role role) {
    return User.restore(id, Email.of(email), name, "hash", role, true);
  }

  private static CurrentUser partner() {
    return new CurrentUser(PARTNER, Role.PARTNER);
  }

  private static CurrentUser ana() {
    return new CurrentUser(ANA, Role.LAWYER);
  }

  private void add(UUID lawyer, UUID matter, String date, int tenths, TimeEntryStatus status) {
    String name =
        lawyer.equals(ANA) ? "Ana Pérez" : lawyer.equals(BRUNO) ? "Bruno Rojas" : "Carla Vargas";
    String code =
        matter.equals(MAT_1)
            ? "AS-2026-0001"
            : matter.equals(MAT_2)
                ? "AS-2026-0002"
                : matter.equals(MAT_3) ? "AS-2026-0003" : "AS-2026-0004";
    repository.with(
        new Entry(
            lawyer, name, matter, code, "Asunto " + code, LocalDate.parse(date), tenths, status));
  }

  private static HoursTotals h(int approvedTenths, int pendingTenths) {
    return new HoursTotals(new Hours(approvedTenths), new Hours(pendingTenths));
  }

  // Scenario: Límites de la ventana por defecto
  @Test
  void defaultWindowIsLast30DaysInFirmZone() {
    add(ANA, MAT_1, "2026-10-09", 20, TimeEntryStatus.APPROVED);
    add(ANA, MAT_1, "2026-11-07", 30, TimeEntryStatus.APPROVED);
    add(ANA, MAT_1, "2026-10-08", 50, TimeEntryStatus.APPROVED);

    DashboardSummary summary = service.getDashboardSummary(partner(), null, null);

    assertThat(summary.window().from()).isEqualTo(LocalDate.parse("2026-10-09"));
    assertThat(summary.window().to()).isEqualTo(LocalDate.parse("2026-11-07"));
    assertThat(summary.totals()).isEqualTo(h(50, 0));
  }

  // Scenario: El socio ve a todos los abogados
  @Test
  void partnerSeesEverything() {
    add(ANA, MAT_1, "2026-11-01", 10, TimeEntryStatus.APPROVED);
    add(BRUNO, MAT_2, "2026-11-01", 10, TimeEntryStatus.PENDING);
    add(CARLA, MAT_3, "2026-11-01", 10, TimeEntryStatus.APPROVED);
    add(CARLA, MAT_4, "2026-11-02", 10, TimeEntryStatus.PENDING);

    DashboardSummary summary = service.getDashboardSummary(partner(), null, null);

    assertThat(repository.lastScope()).isEqualTo(DashboardScope.all());
    assertThat(summary.byLawyer()).hasSize(3);
    assertThat(summary.byMatter()).hasSize(4);
    assertThat(summary.totals()).isEqualTo(h(20, 20));
    assertThat(DashboardSummary.sumOfLawyers(summary.byLawyer())).isEqualTo(summary.totals());
  }

  // Scenario: El abogado solo ve sus horas
  @Test
  void lawyerSeesOnlyOwnHours() {
    add(ANA, MAT_1, "2026-11-01", 30, TimeEntryStatus.APPROVED);
    add(BRUNO, MAT_1, "2026-11-01", 50, TimeEntryStatus.APPROVED);
    add(BRUNO, MAT_2, "2026-11-02", 20, TimeEntryStatus.PENDING);

    DashboardSummary summary = service.getDashboardSummary(ana(), null, null);

    assertThat(repository.lastScope()).isEqualTo(DashboardScope.own(ANA));
    assertThat(summary.byLawyer()).containsExactly(new LawyerHours(ANA, "Ana Pérez", h(30, 0)));
    assertThat(summary.byMatter()).extracting(MatterHours::matterId).containsExactly(MAT_1);
    assertThat(summary.byMatter().get(0).hours()).isEqualTo(h(30, 0));
    assertThat(summary.totals()).isEqualTo(h(30, 0));
  }

  // Scenario: Abogado sin horas propias en la ventana
  @Test
  void lawyerWithoutHoursGetsOwnZeroRow() {
    add(BRUNO, MAT_1, "2026-11-01", 50, TimeEntryStatus.APPROVED);
    add(ANA, MAT_1, "2026-11-01", 40, TimeEntryStatus.REJECTED);

    DashboardSummary summary = service.getDashboardSummary(ana(), null, null);

    assertThat(summary.byMatter()).isEmpty();
    assertThat(summary.byLawyer()).containsExactly(LawyerHours.empty(ANA, "Ana Pérez"));
    assertThat(summary.totals()).isEqualTo(HoursTotals.ZERO);
  }

  // Scenario: Ventana sin registros para el socio
  @Test
  void emptyWindowForPartnerHasEmptyLists() {
    DashboardSummary summary = service.getDashboardSummary(partner(), null, null);

    assertThat(summary.byMatter()).isEmpty();
    assertThat(summary.byLawyer()).isEmpty();
    assertThat(summary.totals()).isEqualTo(HoursTotals.ZERO);
  }

  // Scenario: Registro rechazado no suma
  @Test
  void rejectedEntriesAreExcluded() {
    add(ANA, MAT_3, "2026-11-01", 80, TimeEntryStatus.REJECTED);
    add(ANA, MAT_1, "2026-11-01", 10, TimeEntryStatus.APPROVED);

    DashboardSummary summary = service.getDashboardSummary(partner(), null, null);

    assertThat(summary.byMatter()).extracting(MatterHours::matterId).containsExactly(MAT_1);
    assertThat(summary.totals()).isEqualTo(h(10, 0));
  }

  // Scenario: Rango de un solo día
  @Test
  void customWindowIsPassedToTheRepository() {
    add(ANA, MAT_1, "2026-11-01", 15, TimeEntryStatus.PENDING);
    add(ANA, MAT_1, "2026-11-02", 15, TimeEntryStatus.PENDING);
    LocalDate day = LocalDate.parse("2026-11-01");

    DashboardSummary summary = service.getDashboardSummary(partner(), day, day);

    assertThat(repository.lastWindow().from()).isEqualTo(day);
    assertThat(repository.lastWindow().to()).isEqualTo(day);
    assertThat(summary.totals()).isEqualTo(h(0, 15));
  }

  // Scenario: Solo se envía from
  @Test
  void onlyFromIsRejectedBeforeQuerying() {
    assertThatThrownBy(
            () -> service.getDashboardSummary(partner(), LocalDate.parse("2026-11-01"), null))
        .isInstanceOf(DomainValidationException.class);
    assertThat(repository.lastWindow()).isNull();
  }

  // Scenario: Solo se envía to
  @Test
  void onlyToIsRejected() {
    assertThatThrownBy(
            () -> service.getDashboardSummary(partner(), null, LocalDate.parse("2026-11-07")))
        .isInstanceOf(DomainValidationException.class);
  }

  @Test
  void missingUserIsRejected() {
    assertThatThrownBy(() -> service.getDashboardSummary(null, null, null))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void unknownLawyerWithoutRowsIsNotFound() {
    CurrentUser ghost = new CurrentUser(UUID.randomUUID(), Role.LAWYER);
    assertThatThrownBy(() -> service.getDashboardSummary(ghost, null, null))
        .isInstanceOf(UserNotFoundException.class);
  }
}
