package com.emba.asuntoshoras.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emba.asuntoshoras.application.fakes.InMemoryClientRepository;
import com.emba.asuntoshoras.application.fakes.InMemoryMatterRepository;
import com.emba.asuntoshoras.application.fakes.InMemoryTimeEntryRepository;
import com.emba.asuntoshoras.application.fakes.RecordingDailyHoursLock;
import com.emba.asuntoshoras.application.port.in.CurrentUser;
import com.emba.asuntoshoras.application.port.in.PageQuery;
import com.emba.asuntoshoras.application.port.in.PageResult;
import com.emba.asuntoshoras.application.port.in.TimeEntryUseCases.NewTimeEntry;
import com.emba.asuntoshoras.application.port.in.TimeEntryUseCases.TimeEntryChanges;
import com.emba.asuntoshoras.application.port.in.TimeEntryUseCases.TimeEntryFilter;
import com.emba.asuntoshoras.application.port.in.TimeEntryUseCases.TimeEntryOrder;
import com.emba.asuntoshoras.application.port.in.TimeEntryView;
import com.emba.asuntoshoras.domain.client.Client;
import com.emba.asuntoshoras.domain.matter.Matter;
import com.emba.asuntoshoras.domain.matter.MatterCode;
import com.emba.asuntoshoras.domain.matter.MatterNotFoundException;
import com.emba.asuntoshoras.domain.shared.DomainException;
import com.emba.asuntoshoras.domain.shared.ForbiddenException;
import com.emba.asuntoshoras.domain.shared.InvalidValueException;
import com.emba.asuntoshoras.domain.timeentry.ConcurrentModificationException;
import com.emba.asuntoshoras.domain.timeentry.DailyHoursExceededException;
import com.emba.asuntoshoras.domain.timeentry.EditWindowExpiredException;
import com.emba.asuntoshoras.domain.timeentry.EntryNotPendingException;
import com.emba.asuntoshoras.domain.timeentry.FutureDateException;
import com.emba.asuntoshoras.domain.timeentry.Hours;
import com.emba.asuntoshoras.domain.timeentry.HoursInvalidIncrementException;
import com.emba.asuntoshoras.domain.timeentry.HoursOutOfRangeException;
import com.emba.asuntoshoras.domain.timeentry.MatterClosedException;
import com.emba.asuntoshoras.domain.timeentry.NotEntryOwnerException;
import com.emba.asuntoshoras.domain.timeentry.RejectionReasonRequiredException;
import com.emba.asuntoshoras.domain.timeentry.TimeEntry;
import com.emba.asuntoshoras.domain.timeentry.TimeEntryNotFoundException;
import com.emba.asuntoshoras.domain.timeentry.TimeEntryStatus;
import com.emba.asuntoshoras.domain.user.Role;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/** Casos de uso de registros de horas con fakes en memoria y {@code Clock.fixed} en La Paz. */
class TimeEntryServiceTest {

  private static final ZoneId LA_PAZ = ZoneId.of("America/La_Paz");

  /** Hoy es 2026-11-07 (10:00) en La Paz. */
  private static final Instant NOW = Instant.parse("2026-11-07T14:00:00Z");

  private static final LocalDate NOV_4 = LocalDate.parse("2026-11-04");
  private static final LocalDate NOV_5 = LocalDate.parse("2026-11-05");
  private static final LocalDate NOV_6 = LocalDate.parse("2026-11-06");
  private static final LocalDate NOV_7 = LocalDate.parse("2026-11-07");
  private static final LocalDate NOV_8 = LocalDate.parse("2026-11-08");

  private static final CurrentUser LAWYER_A = new CurrentUser(UUID.randomUUID(), Role.LAWYER);
  private static final CurrentUser LAWYER_B = new CurrentUser(UUID.randomUUID(), Role.LAWYER);
  private static final CurrentUser PARTNER = new CurrentUser(UUID.randomUUID(), Role.PARTNER);

  private final InMemoryClientRepository clients = new InMemoryClientRepository();
  private final InMemoryMatterRepository matters = new InMemoryMatterRepository(clients);
  private final InMemoryTimeEntryRepository entries =
      new InMemoryTimeEntryRepository(matters)
          .lawyer(LAWYER_A.userId(), "Abogado Uno")
          .lawyer(LAWYER_B.userId(), "Abogada Dos");
  private final RecordingDailyHoursLock lock = new RecordingDailyHoursLock();

  private final Client client = Client.create(UUID.randomUUID(), "Andes Logística", null, NOW);
  private final Matter open = matter(1, false);
  private final Matter otherOpen = matter(2, false);
  private final Matter closed = matter(3, true);

  private TimeEntryService service = serviceAt(NOW);

  TimeEntryServiceTest() {
    clients.with(client);
    matters.with(open).with(otherOpen).with(closed);
  }

  private TimeEntryService serviceAt(Instant instant) {
    return new TimeEntryService(entries, matters, lock, Clock.fixed(instant, LA_PAZ));
  }

  private Matter matter(int sequence, boolean isClosed) {
    Matter matter =
        Matter.open(
            UUID.randomUUID(),
            MatterCode.of(2026, sequence),
            client.id(),
            "Asunto " + sequence,
            null,
            NOW.minusSeconds(864_000));
    if (isClosed) {
      matter.close(NOW.minusSeconds(86_400));
    }
    return matter;
  }

  private static NewTimeEntry newEntry(UUID matterId, LocalDate date, String hours) {
    return new NewTimeEntry(
        matterId, date, hours == null ? null : new BigDecimal(hours), "Revisión de contrato");
  }

  private static TimeEntryChanges changes(UUID matterId, LocalDate date, String hours, long v) {
    return new TimeEntryChanges(matterId, date, new BigDecimal(hours), "Nueva descripción", v);
  }

  /** Registro existente creado {@code createdAt} con el estado dado. */
  private TimeEntry existing(
      CurrentUser owner, LocalDate date, int tenths, TimeEntryStatus status, Instant createdAt) {
    TimeEntry entry =
        TimeEntry.restore(
            UUID.randomUUID(),
            open.id(),
            owner.userId(),
            date,
            Hours.ofTenths(tenths),
            "Trabajo previo",
            status,
            status == TimeEntryStatus.REJECTED ? "Motivo previo" : null,
            createdAt,
            0);
    entries.with(entry);
    return entry;
  }

  private TimeEntry existing(CurrentUser owner, LocalDate date, int tenths, TimeEntryStatus s) {
    return existing(owner, date, tenths, s, NOW.minusSeconds(2 * 86_400));
  }

  private static void assertCode(ThrowingCallable call, Class<? extends DomainException> type) {
    assertThatThrownBy(call).isInstanceOf(type);
  }

  // ---------------------------------------------------------------- alta

  // Scenario: Registro válido
  @Test
  void lawyerRecordsPendingHours() {
    TimeEntryView view =
        service.createTimeEntry(
            LAWYER_A,
            new NewTimeEntry(open.id(), NOV_6, new BigDecimal("1.5"), "  " + "d".repeat(40) + " "));

    TimeEntry entry = view.entry();
    assertThat(entry.status()).isEqualTo(TimeEntryStatus.PENDING);
    assertThat(entry.hours().tenths()).isEqualTo(15);
    assertThat(entry.lawyerId()).isEqualTo(LAWYER_A.userId());
    assertThat(entry.description()).hasSize(40);
    assertThat(entry.version()).isZero();
    assertThat(entry.createdAt()).isEqualTo(NOW);
    assertThat(view.matterCode()).isEqualTo("AS-2026-0001");
    assertThat(view.lawyerName()).isEqualTo("Abogado Uno");
    assertThat(view.editableUntil()).isEqualTo(LocalDate.parse("2026-11-14"));
    assertThat(lock.locked()).containsExactly(LAWYER_A.userId() + ":" + NOV_6);
    assertThat(entries.count()).isEqualTo(1);
  }

  // Scenario: Socio intenta registrar horas
  @Test
  void partnerCannotRecordHours() {
    assertCode(
        () -> service.createTimeEntry(PARTNER, newEntry(open.id(), NOV_6, "1")),
        ForbiddenException.class);
    assertCode(
        () -> service.createTimeEntry(null, newEntry(open.id(), NOV_6, "1")),
        ForbiddenException.class);
    assertThat(entries.count()).isZero();
  }

  // Scenario: Asunto inexistente
  @Test
  void unknownMatterIs404() {
    assertCode(
        () -> service.createTimeEntry(LAWYER_A, newEntry(UUID.randomUUID(), NOV_6, "1")),
        MatterNotFoundException.class);
  }

  // Scenario: Campos obligatorios ausentes / Descripción vacía
  @Test
  void missingFieldsAreValidationErrors() {
    assertThatThrownBy(() -> service.createTimeEntry(LAWYER_A, newEntry(null, NOV_6, "1")))
        .isInstanceOfSatisfying(
            InvalidValueException.class, e -> assertThat(e.field()).isEqualTo("matterId"));
    assertThatThrownBy(() -> service.createTimeEntry(LAWYER_A, newEntry(open.id(), null, "1")))
        .isInstanceOfSatisfying(
            InvalidValueException.class, e -> assertThat(e.field()).isEqualTo("workDate"));
    assertThatThrownBy(() -> service.createTimeEntry(LAWYER_A, newEntry(open.id(), NOV_6, null)))
        .isInstanceOfSatisfying(
            InvalidValueException.class, e -> assertThat(e.field()).isEqualTo("hours"));
    assertThatThrownBy(
            () ->
                service.createTimeEntry(
                    LAWYER_A, new NewTimeEntry(open.id(), NOV_6, BigDecimal.ONE, " ")))
        .isInstanceOfSatisfying(
            InvalidValueException.class, e -> assertThat(e.field()).isEqualTo("description"));
  }

  // Scenario: Descripción de 2001 caracteres / Descripción de 2000 caracteres
  @Test
  void descriptionLengthIsLimitedTo2000() {
    assertThatThrownBy(
            () ->
                service.createTimeEntry(
                    LAWYER_A, new NewTimeEntry(open.id(), NOV_6, BigDecimal.ONE, "d".repeat(2001))))
        .isInstanceOf(InvalidValueException.class);

    assertThat(
            service
                .createTimeEntry(
                    LAWYER_A, new NewTimeEntry(open.id(), NOV_6, BigDecimal.ONE, "d".repeat(2000)))
                .entry()
                .description())
        .hasSize(2000);
  }

  // Scenario: Límites válidos
  @Test
  void boundaryHoursAreAccepted() {
    service.createTimeEntry(LAWYER_A, newEntry(open.id(), NOV_5, "0.1"));
    service.createTimeEntry(LAWYER_A, newEntry(open.id(), NOV_5, "23.9"));

    assertThat(entries.sumCountedHours(LAWYER_A.userId(), NOV_5, null).tenths()).isEqualTo(240);
  }

  // Scenario: Incremento inválido / Cero horas / Más de 24 horas / Horas negativas
  @Test
  void invalidHoursAreRejected() {
    assertCode(
        () -> service.createTimeEntry(LAWYER_A, newEntry(open.id(), NOV_6, "1.25")),
        HoursInvalidIncrementException.class);
    for (String hours : new String[] {"0", "24.1", "-1"}) {
      assertCode(
          () -> service.createTimeEntry(LAWYER_A, newEntry(open.id(), NOV_6, hours)),
          HoursOutOfRangeException.class);
    }
    assertThat(entries.count()).isZero();
  }

  // Scenario: Completar exactamente 24 horas
  @Test
  void completingExactlyTwentyFourHours() {
    existing(LAWYER_A, NOV_5, 160, TimeEntryStatus.PENDING);
    existing(LAWYER_A, NOV_5, 40, TimeEntryStatus.APPROVED);

    service.createTimeEntry(LAWYER_A, newEntry(open.id(), NOV_5, "4"));

    assertThat(entries.count()).isEqualTo(3);
  }

  // Scenario: Superar el límite diario
  @Test
  void exceedingTheDailyLimit() {
    existing(LAWYER_A, NOV_5, 120, TimeEntryStatus.PENDING);
    existing(LAWYER_A, NOV_5, 80, TimeEntryStatus.APPROVED);

    assertThatThrownBy(() -> service.createTimeEntry(LAWYER_A, newEntry(open.id(), NOV_5, "4.1")))
        .isInstanceOfSatisfying(
            DailyHoursExceededException.class,
            e -> {
              assertThat(e.limit().tenths()).isEqualTo(240);
              assertThat(e.alreadyLogged().tenths()).isEqualTo(200);
            });
    assertThat(entries.count()).isEqualTo(2);
  }

  // Scenario: Las horas rechazadas no cuentan
  @Test
  void rejectedHoursDoNotCount() {
    existing(LAWYER_A, NOV_5, 200, TimeEntryStatus.APPROVED);
    existing(LAWYER_A, NOV_5, 80, TimeEntryStatus.REJECTED);

    service.createTimeEntry(LAWYER_A, newEntry(open.id(), NOV_5, "4"));
  }

  // Scenario: Las horas de otro abogado no cuentan
  @Test
  void otherLawyersHoursDoNotCount() {
    existing(LAWYER_B, NOV_5, 240, TimeEntryStatus.APPROVED);

    service.createTimeEntry(LAWYER_A, newEntry(open.id(), NOV_5, "8"));
  }

  // Scenario: Registrar horas de hoy
  @Test
  void todayIsAllowed() {
    service.createTimeEntry(LAWYER_A, newEntry(open.id(), NOV_7, "1"));
  }

  // Scenario: Registrar horas de mañana
  @Test
  void tomorrowIsFuture() {
    assertCode(
        () -> service.createTimeEntry(LAWYER_A, newEntry(open.id(), NOV_8, "1")),
        FutureDateException.class);
  }

  // Scenario: Borde de medianoche entre UTC y La Paz
  @Test
  void utcMidnightIsStillYesterdayInLaPaz() {
    service = serviceAt(Instant.parse("2026-11-08T02:30:00Z"));

    assertCode(
        () -> service.createTimeEntry(LAWYER_A, newEntry(open.id(), NOV_8, "1")),
        FutureDateException.class);
    assertThat(service.createTimeEntry(LAWYER_A, newEntry(open.id(), NOV_7, "1")).entry())
        .isNotNull();
  }

  @Test
  void earlyUtcMorningIsStillThePreviousDayInLaPaz() {
    // 2026-11-07T03:30Z = 2026-11-06 23:30 en La Paz.
    service = serviceAt(Instant.parse("2026-11-07T03:30:00Z"));

    assertCode(
        () -> service.createTimeEntry(LAWYER_A, newEntry(open.id(), NOV_7, "1")),
        FutureDateException.class);
    service.createTimeEntry(LAWYER_A, newEntry(open.id(), NOV_6, "1"));
  }

  // Scenario: Asunto cerrado
  @Test
  void closedMatterIsRejected() {
    assertCode(
        () -> service.createTimeEntry(LAWYER_A, newEntry(closed.id(), NOV_6, "1")),
        MatterClosedException.class);
    assertThat(entries.count()).isZero();
  }

  @Test
  void createEvaluationOrderIsClosedThenFutureThenIncrementThenRangeThenDaily() {
    existing(LAWYER_A, NOV_8, 240, TimeEntryStatus.PENDING);
    assertCode(
        () -> service.createTimeEntry(LAWYER_A, newEntry(closed.id(), NOV_8, "30.25")),
        MatterClosedException.class);
    assertCode(
        () -> service.createTimeEntry(LAWYER_A, newEntry(open.id(), NOV_8, "30.25")),
        FutureDateException.class);
    existing(LAWYER_A, NOV_6, 240, TimeEntryStatus.PENDING);
    assertCode(
        () -> service.createTimeEntry(LAWYER_A, newEntry(open.id(), NOV_6, "30.25")),
        HoursInvalidIncrementException.class);
    assertCode(
        () -> service.createTimeEntry(LAWYER_A, newEntry(open.id(), NOV_6, "30")),
        HoursOutOfRangeException.class);
    assertCode(
        () -> service.createTimeEntry(LAWYER_A, newEntry(open.id(), NOV_6, "1")),
        DailyHoursExceededException.class);
  }

  // Scenario: Alta con campos de solo lectura
  @Test
  void createIgnoresClientControlledFieldsByConstruction() {
    TimeEntryView view = service.createTimeEntry(LAWYER_A, newEntry(open.id(), NOV_6, "2"));

    assertThat(view.entry().lawyerId()).isEqualTo(LAWYER_A.userId());
    assertThat(view.entry().status()).isEqualTo(TimeEntryStatus.PENDING);
    assertThat(view.entry().rejectionReason()).isNull();
    assertThat(view.entry().createdAt()).isEqualTo(NOW);
  }

  // ---------------------------------------------------------------- edición

  // Scenario: Edición válida
  @Test
  void authorEditsPendingEntry() {
    TimeEntry entry = existing(LAWYER_A, NOV_5, 20, TimeEntryStatus.PENDING);

    TimeEntryView view =
        service.updateTimeEntry(LAWYER_A, entry.id(), changes(open.id(), NOV_5, "3.5", 0));

    assertThat(view.entry().hours().tenths()).isEqualTo(35);
    assertThat(view.entry().description()).isEqualTo("Nueva descripción");
    assertThat(view.entry().status()).isEqualTo(TimeEntryStatus.PENDING);
    assertThat(view.entry().version()).isEqualTo(1);
    assertThat(view.entry().createdAt()).isEqualTo(entry.createdAt());
  }

  // Scenario: Socio intenta editar un registro
  @Test
  void partnerCannotEdit() {
    TimeEntry entry = existing(LAWYER_A, NOV_5, 20, TimeEntryStatus.PENDING);

    assertCode(
        () -> service.updateTimeEntry(PARTNER, entry.id(), changes(open.id(), NOV_5, "1", 0)),
        ForbiddenException.class);
  }

  // Scenario: Abogado edita un registro ajeno
  @Test
  void otherLawyerCannotEdit() {
    TimeEntry entry = existing(LAWYER_A, NOV_5, 20, TimeEntryStatus.PENDING);

    assertCode(
        () -> service.updateTimeEntry(LAWYER_B, entry.id(), changes(open.id(), NOV_5, "1", 0)),
        NotEntryOwnerException.class);
    assertThat(entries.stored(entry.id()).orElseThrow().hours().tenths()).isEqualTo(20);
  }

  // Scenario: Registro inexistente
  @Test
  void unknownEntryIs404() {
    assertCode(
        () ->
            service.updateTimeEntry(LAWYER_A, UUID.randomUUID(), changes(open.id(), NOV_5, "1", 0)),
        TimeEntryNotFoundException.class);
  }

  @Test
  void unknownTargetMatterIs404() {
    TimeEntry entry = existing(LAWYER_A, NOV_5, 20, TimeEntryStatus.PENDING);

    assertCode(
        () ->
            service.updateTimeEntry(
                LAWYER_A, entry.id(), changes(UUID.randomUUID(), NOV_5, "1", 0)),
        MatterNotFoundException.class);
  }

  // Scenario: Editar un registro aprobado / Editar un registro rechazado
  @Test
  void terminalEntriesCannotBeEdited() {
    TimeEntry approved = existing(LAWYER_A, NOV_5, 20, TimeEntryStatus.APPROVED);
    TimeEntry rejected = existing(LAWYER_A, NOV_5, 20, TimeEntryStatus.REJECTED);

    assertCode(
        () -> service.updateTimeEntry(LAWYER_A, approved.id(), changes(open.id(), NOV_5, "1", 0)),
        EntryNotPendingException.class);
    assertCode(
        () -> service.updateTimeEntry(LAWYER_A, rejected.id(), changes(open.id(), NOV_5, "1", 0)),
        EntryNotPendingException.class);
  }

  // Scenario: Versión obsoleta
  @Test
  void staleVersionIsRejectedWithoutChanges() {
    TimeEntry entry = existing(LAWYER_A, NOV_5, 20, TimeEntryStatus.PENDING);
    entries.touchConcurrently(entry.id());

    assertCode(
        () -> service.updateTimeEntry(LAWYER_A, entry.id(), changes(open.id(), NOV_5, "9", 0)),
        ConcurrentModificationException.class);
    assertThat(entries.stored(entry.id()).orElseThrow().hours().tenths()).isEqualTo(20);
  }

  @Test
  void raceBetweenReadAndWriteIsConcurrentModification() {
    TimeEntry entry = existing(LAWYER_A, NOV_5, 20, TimeEntryStatus.PENDING);
    entries.beforeUpdate(entries::touchConcurrently);

    assertCode(
        () -> service.updateTimeEntry(LAWYER_A, entry.id(), changes(open.id(), NOV_5, "9", 0)),
        ConcurrentModificationException.class);
  }

  @Test
  void raceOnApproveOrRejectIsConcurrentModification() {
    TimeEntry first = existing(LAWYER_A, NOV_5, 20, TimeEntryStatus.PENDING);
    TimeEntry second = existing(LAWYER_A, NOV_5, 20, TimeEntryStatus.PENDING);
    entries.beforeUpdate(entries::touchConcurrently);

    assertCode(
        () -> service.approveTimeEntry(PARTNER, first.id()), ConcurrentModificationException.class);
    assertCode(
        () -> service.rejectTimeEntry(PARTNER, second.id(), "Motivo"),
        ConcurrentModificationException.class);
  }

  // Scenario: Versión ausente
  @Test
  void missingVersionIsValidationError() {
    TimeEntry entry = existing(LAWYER_A, NOV_5, 20, TimeEntryStatus.PENDING);

    assertThatThrownBy(
            () ->
                service.updateTimeEntry(
                    LAWYER_A,
                    entry.id(),
                    new TimeEntryChanges(open.id(), NOV_5, BigDecimal.ONE, "d", null)))
        .isInstanceOfSatisfying(
            InvalidValueException.class, e -> assertThat(e.field()).isEqualTo("version"));
  }

  // Scenario: Último día de la ventana
  @Test
  void lastDayOfTheWindowIsEditable() {
    TimeEntry entry =
        existing(
            LAWYER_A,
            LocalDate.parse("2026-10-31"),
            20,
            TimeEntryStatus.PENDING,
            Instant.parse("2026-10-31T14:00:00Z"));

    service.updateTimeEntry(
        LAWYER_A, entry.id(), changes(open.id(), LocalDate.parse("2026-10-31"), "3", 0));
  }

  // Scenario: Ventana vencida
  @Test
  void expiredWindowIsRejected() {
    TimeEntry entry =
        existing(
            LAWYER_A,
            LocalDate.parse("2026-10-30"),
            20,
            TimeEntryStatus.PENDING,
            Instant.parse("2026-10-30T14:00:00Z"));

    assertCode(
        () ->
            service.updateTimeEntry(
                LAWYER_A, entry.id(), changes(open.id(), LocalDate.parse("2026-10-30"), "3", 0)),
        EditWindowExpiredException.class);
  }

  // Scenario: Las horas previas del propio registro no cuentan
  @Test
  void ownPreviousHoursAreExcluded() {
    TimeEntry entry = existing(LAWYER_A, NOV_5, 80, TimeEntryStatus.PENDING);
    existing(LAWYER_A, NOV_5, 100, TimeEntryStatus.PENDING);
    existing(LAWYER_A, NOV_5, 60, TimeEntryStatus.APPROVED);

    service.updateTimeEntry(LAWYER_A, entry.id(), changes(open.id(), NOV_5, "8", 0));
  }

  // Scenario: La edición supera el límite diario
  @Test
  void editExceedingTheDailyLimit() {
    TimeEntry entry = existing(LAWYER_A, NOV_5, 80, TimeEntryStatus.PENDING);
    existing(LAWYER_A, NOV_5, 160, TimeEntryStatus.PENDING);

    assertThatThrownBy(
            () ->
                service.updateTimeEntry(LAWYER_A, entry.id(), changes(open.id(), NOV_5, "8.1", 0)))
        .isInstanceOfSatisfying(
            DailyHoursExceededException.class,
            e -> assertThat(e.alreadyLogged().tenths()).isEqualTo(160));
  }

  // Scenario: Mover el registro a otro día lleno
  @Test
  void movingToAFullDay() {
    TimeEntry entry = existing(LAWYER_A, NOV_5, 50, TimeEntryStatus.PENDING);
    existing(LAWYER_A, NOV_4, 200, TimeEntryStatus.APPROVED);

    assertThatThrownBy(
            () -> service.updateTimeEntry(LAWYER_A, entry.id(), changes(open.id(), NOV_4, "5", 0)))
        .isInstanceOfSatisfying(
            DailyHoursExceededException.class,
            e -> assertThat(e.alreadyLogged().tenths()).isEqualTo(200));
    // Se bloquearon ambas fechas en orden ascendente.
    assertThat(lock.locked())
        .containsExactly(LAWYER_A.userId() + ":" + NOV_4, LAWYER_A.userId() + ":" + NOV_5);
  }

  // Scenario: Edición con fecha futura
  @Test
  void editToFutureDate() {
    TimeEntry entry = existing(LAWYER_A, NOV_5, 20, TimeEntryStatus.PENDING);

    assertCode(
        () -> service.updateTimeEntry(LAWYER_A, entry.id(), changes(open.id(), NOV_8, "1", 0)),
        FutureDateException.class);
  }

  // Scenario: Edición hacia un asunto cerrado
  @Test
  void editToClosedMatter() {
    TimeEntry entry = existing(LAWYER_A, NOV_5, 20, TimeEntryStatus.PENDING);

    assertCode(
        () -> service.updateTimeEntry(LAWYER_A, entry.id(), changes(closed.id(), NOV_5, "1", 0)),
        MatterClosedException.class);
  }

  // Scenario: El asunto original se cerró después de la carga
  @Test
  void originalMatterClosedAfterwards() {
    TimeEntry entry =
        TimeEntry.restore(
            UUID.randomUUID(),
            closed.id(),
            LAWYER_A.userId(),
            NOV_5,
            Hours.ofTenths(20),
            "Trabajo",
            TimeEntryStatus.PENDING,
            null,
            NOW.minusSeconds(86_400),
            0);
    entries.with(entry);

    assertCode(
        () -> service.updateTimeEntry(LAWYER_A, entry.id(), changes(closed.id(), NOV_5, "3", 0)),
        MatterClosedException.class);
    assertThat(entries.stored(entry.id()).orElseThrow().hours().tenths()).isEqualTo(20);
  }

  // Scenario: Edición con incremento inválido / Edición fuera de rango
  @Test
  void editWithInvalidHours() {
    TimeEntry entry = existing(LAWYER_A, NOV_5, 20, TimeEntryStatus.PENDING);

    assertCode(
        () -> service.updateTimeEntry(LAWYER_A, entry.id(), changes(open.id(), NOV_5, "2.05", 0)),
        HoursInvalidIncrementException.class);
    assertCode(
        () -> service.updateTimeEntry(LAWYER_A, entry.id(), changes(open.id(), NOV_5, "0", 0)),
        HoursOutOfRangeException.class);
  }

  @Test
  void editCanMoveToAnotherOpenMatter() {
    TimeEntry entry = existing(LAWYER_A, NOV_5, 20, TimeEntryStatus.PENDING);

    TimeEntryView view =
        service.updateTimeEntry(LAWYER_A, entry.id(), changes(otherOpen.id(), NOV_5, "2", 0));

    assertThat(view.entry().matterId()).isEqualTo(otherOpen.id());
    assertThat(view.matterCode()).isEqualTo("AS-2026-0002");
  }

  @Test
  void editEvaluationOrder() {
    TimeEntry foreignApproved = existing(LAWYER_B, NOV_5, 20, TimeEntryStatus.APPROVED);
    // 404 del asunto antes que 403 not-entry-owner (design D5).
    assertCode(
        () ->
            service.updateTimeEntry(
                LAWYER_A, foreignApproved.id(), changes(UUID.randomUUID(), NOV_5, "1", 0)),
        MatterNotFoundException.class);
    // not-entry-owner antes que entry-not-pending.
    assertCode(
        () ->
            service.updateTimeEntry(
                LAWYER_A, foreignApproved.id(), changes(open.id(), NOV_5, "1", 0)),
        NotEntryOwnerException.class);
    // entry-not-pending antes que concurrent-modification.
    TimeEntry approved = existing(LAWYER_A, NOV_5, 20, TimeEntryStatus.APPROVED);
    assertCode(
        () -> service.updateTimeEntry(LAWYER_A, approved.id(), changes(open.id(), NOV_5, "1", 7)),
        EntryNotPendingException.class);
    // concurrent-modification antes que edit-window-expired.
    TimeEntry old =
        existing(
            LAWYER_A,
            LocalDate.parse("2026-10-01"),
            20,
            TimeEntryStatus.PENDING,
            Instant.parse("2026-10-01T14:00:00Z"));
    assertCode(
        () -> service.updateTimeEntry(LAWYER_A, old.id(), changes(closed.id(), NOV_8, "1", 7)),
        ConcurrentModificationException.class);
    // edit-window-expired antes que matter-closed.
    assertCode(
        () -> service.updateTimeEntry(LAWYER_A, old.id(), changes(closed.id(), NOV_8, "1", 0)),
        EditWindowExpiredException.class);
  }

  // Scenario: Edición con campos de solo lectura
  @Test
  void editKeepsServerControlledFields() {
    TimeEntry entry = existing(LAWYER_A, NOV_5, 20, TimeEntryStatus.PENDING);

    TimeEntryView view =
        service.updateTimeEntry(LAWYER_A, entry.id(), changes(open.id(), NOV_5, "2", 0));

    assertThat(view.entry().lawyerId()).isEqualTo(LAWYER_A.userId());
    assertThat(view.entry().status()).isEqualTo(TimeEntryStatus.PENDING);
    assertThat(view.entry().createdAt()).isEqualTo(entry.createdAt());
    assertThat(view.editableUntil()).isEqualTo(entry.editableUntil(LA_PAZ));
  }

  // ---------------------------------------------------------------- aprobación y rechazo

  // Scenario: Socio aprueba un registro pendiente
  @Test
  void partnerApproves() {
    TimeEntry entry = existing(LAWYER_A, NOV_5, 20, TimeEntryStatus.PENDING);

    TimeEntryView view = service.approveTimeEntry(PARTNER, entry.id());

    assertThat(view.entry().status()).isEqualTo(TimeEntryStatus.APPROVED);
    assertThat(view.entry().version()).isEqualTo(1);
  }

  // Scenario: Aprobar un registro ya aprobado / Aprobar un registro rechazado
  @Test
  void approvingTerminalEntriesIsConflict() {
    TimeEntry approved = existing(LAWYER_A, NOV_5, 20, TimeEntryStatus.APPROVED);
    TimeEntry rejected = existing(LAWYER_A, NOV_5, 20, TimeEntryStatus.REJECTED);

    assertCode(
        () -> service.approveTimeEntry(PARTNER, approved.id()), EntryNotPendingException.class);
    assertCode(
        () -> service.approveTimeEntry(PARTNER, rejected.id()), EntryNotPendingException.class);
    assertThat(entries.stored(rejected.id()).orElseThrow().status())
        .isEqualTo(TimeEntryStatus.REJECTED);
  }

  // Scenario: Abogado intenta aprobar
  @Test
  void lawyerCannotApprove() {
    TimeEntry entry = existing(LAWYER_A, NOV_5, 20, TimeEntryStatus.PENDING);

    assertCode(() -> service.approveTimeEntry(LAWYER_A, entry.id()), ForbiddenException.class);
    assertThat(entries.stored(entry.id()).orElseThrow().status())
        .isEqualTo(TimeEntryStatus.PENDING);
  }

  // Scenario: Aprobar un registro inexistente
  @Test
  void approvingUnknownEntryIs404() {
    assertCode(
        () -> service.approveTimeEntry(PARTNER, UUID.randomUUID()),
        TimeEntryNotFoundException.class);
  }

  // Scenario: Socio rechaza con motivo
  @Test
  void partnerRejectsWithReason() {
    TimeEntry entry = existing(LAWYER_A, NOV_5, 20, TimeEntryStatus.PENDING);

    TimeEntryView view = service.rejectTimeEntry(PARTNER, entry.id(), "Descripción insuficiente");

    assertThat(view.entry().status()).isEqualTo(TimeEntryStatus.REJECTED);
    assertThat(view.entry().rejectionReason()).isEqualTo("Descripción insuficiente");
  }

  // Scenario: Rechazo sin motivo / Motivo de 501 caracteres
  @Test
  void rejectionRequiresValidReason() {
    TimeEntry entry = existing(LAWYER_A, NOV_5, 20, TimeEntryStatus.PENDING);

    for (String reason : new String[] {null, "", "  ", "r".repeat(501)}) {
      assertCode(
          () -> service.rejectTimeEntry(PARTNER, entry.id(), reason),
          RejectionReasonRequiredException.class);
    }
    assertThat(entries.stored(entry.id()).orElseThrow().status())
        .isEqualTo(TimeEntryStatus.PENDING);
  }

  // Scenario: Rechazar un registro ya rechazado / Rechazar un registro aprobado
  @Test
  void rejectingTerminalEntriesIsConflictEvenWithoutReason() {
    TimeEntry approved = existing(LAWYER_A, NOV_5, 20, TimeEntryStatus.APPROVED);
    TimeEntry rejected = existing(LAWYER_A, NOV_5, 20, TimeEntryStatus.REJECTED);

    assertCode(
        () -> service.rejectTimeEntry(PARTNER, approved.id(), "Motivo"),
        EntryNotPendingException.class);
    assertCode(
        () -> service.rejectTimeEntry(PARTNER, rejected.id(), null),
        EntryNotPendingException.class);
  }

  // Scenario: Abogado intenta rechazar
  @Test
  void lawyerCannotReject() {
    TimeEntry entry = existing(LAWYER_A, NOV_5, 20, TimeEntryStatus.PENDING);

    assertCode(
        () -> service.rejectTimeEntry(LAWYER_A, entry.id(), "Motivo"), ForbiddenException.class);
    assertCode(
        () -> service.rejectTimeEntry(PARTNER, UUID.randomUUID(), "Motivo"),
        TimeEntryNotFoundException.class);
  }

  @Test
  void pendingEntriesOfClosedMattersCanStillBeApprovedOrRejected() {
    TimeEntry first =
        TimeEntry.restore(
            UUID.randomUUID(),
            closed.id(),
            LAWYER_A.userId(),
            NOV_5,
            Hours.ofTenths(20),
            "Trabajo",
            TimeEntryStatus.PENDING,
            null,
            NOW.minusSeconds(86_400),
            0);
    TimeEntry second =
        TimeEntry.restore(
            UUID.randomUUID(),
            closed.id(),
            LAWYER_A.userId(),
            NOV_5,
            Hours.ofTenths(20),
            "Trabajo",
            TimeEntryStatus.PENDING,
            null,
            NOW.minusSeconds(86_400),
            0);
    entries.with(first).with(second);

    assertThat(service.approveTimeEntry(PARTNER, first.id()).entry().status())
        .isEqualTo(TimeEntryStatus.APPROVED);
    assertThat(service.rejectTimeEntry(PARTNER, second.id(), "Asunto cerrado").entry().status())
        .isEqualTo(TimeEntryStatus.REJECTED);
  }

  // ---------------------------------------------------------------- listado

  // Scenario: Socio filtra pendientes de un abogado
  @Test
  void partnerFiltersPendingOfOneLawyer() {
    existing(LAWYER_A, NOV_5, 20, TimeEntryStatus.PENDING);
    existing(LAWYER_A, NOV_5, 20, TimeEntryStatus.APPROVED);
    existing(LAWYER_B, NOV_5, 20, TimeEntryStatus.PENDING);

    PageResult<TimeEntryView> page =
        service.listTimeEntries(
            PARTNER,
            new TimeEntryFilter(null, LAWYER_A.userId(), TimeEntryStatus.PENDING, null, null),
            TimeEntryOrder.WORK_DATE_DESC,
            PageQuery.of(null, null));

    assertThat(page.items())
        .hasSize(1)
        .allSatisfy(
            v -> {
              assertThat(v.entry().lawyerId()).isEqualTo(LAWYER_A.userId());
              assertThat(v.entry().status()).isEqualTo(TimeEntryStatus.PENDING);
            });
  }

  // Scenario: Socio filtra por asunto y rango de fechas
  @Test
  void partnerFiltersByMatterAndInclusiveDateRange() {
    existing(LAWYER_A, LocalDate.parse("2026-10-31"), 10, TimeEntryStatus.PENDING);
    existing(LAWYER_A, LocalDate.parse("2026-11-01"), 10, TimeEntryStatus.PENDING);
    existing(LAWYER_B, NOV_5, 10, TimeEntryStatus.APPROVED);
    existing(LAWYER_B, NOV_6, 10, TimeEntryStatus.APPROVED);

    PageResult<TimeEntryView> page =
        service.listTimeEntries(
            PARTNER,
            new TimeEntryFilter(open.id(), null, null, LocalDate.parse("2026-11-01"), NOV_5),
            null,
            PageQuery.of(null, null));

    assertThat(page.items())
        .extracting(v -> v.entry().workDate())
        .containsExactly(NOV_5, LocalDate.parse("2026-11-01"));
  }

  // Scenario: Abogado solo ve lo suyo
  @Test
  void lawyerOnlySeesOwnEntriesAndLawyerFilterIsIgnored() {
    existing(LAWYER_A, NOV_5, 20, TimeEntryStatus.PENDING);
    existing(LAWYER_B, NOV_5, 20, TimeEntryStatus.PENDING);

    PageResult<TimeEntryView> page =
        service.listTimeEntries(
            LAWYER_A,
            new TimeEntryFilter(null, LAWYER_B.userId(), null, null, null),
            TimeEntryOrder.WORK_DATE_DESC,
            PageQuery.of(null, null));

    assertThat(page.items())
        .hasSize(1)
        .allSatisfy(v -> assertThat(v.entry().lawyerId()).isEqualTo(LAWYER_A.userId()));
    assertThat(page.items().get(0).lawyerName()).isEqualTo("Abogado Uno");
  }

  // Scenario: Rango de fechas invertido
  @Test
  void invertedDateRangeIsValidationError() {
    assertThatThrownBy(
            () ->
                service.listTimeEntries(
                    PARTNER,
                    new TimeEntryFilter(null, null, null, NOV_5, LocalDate.parse("2026-11-01")),
                    null,
                    PageQuery.of(null, null)))
        .isInstanceOfSatisfying(
            InvalidValueException.class, e -> assertThat(e.field()).isEqualTo("from"));
    assertCode(
        () -> service.listTimeEntries(null, null, null, PageQuery.of(null, null)),
        ForbiddenException.class);
  }

  // Scenario: Orden por defecto / Orden de la cola de aprobaciones
  @Test
  void ordersByWorkDateDescOrCreatedAtAsc() {
    TimeEntry nov3 =
        existing(
            LAWYER_A,
            LocalDate.parse("2026-11-03"),
            10,
            TimeEntryStatus.PENDING,
            NOW.minusSeconds(400));
    TimeEntry nov4Early =
        existing(LAWYER_A, NOV_4, 10, TimeEntryStatus.PENDING, NOW.minusSeconds(300));
    TimeEntry nov5 = existing(LAWYER_A, NOV_5, 10, TimeEntryStatus.PENDING, NOW.minusSeconds(500));
    TimeEntry nov4Late =
        existing(LAWYER_A, NOV_4, 10, TimeEntryStatus.PENDING, NOW.minusSeconds(100));

    PageResult<TimeEntryView> byDate =
        service.listTimeEntries(LAWYER_A, null, null, PageQuery.of(null, null));
    PageResult<TimeEntryView> queue =
        service.listTimeEntries(
            PARTNER,
            new TimeEntryFilter(null, null, TimeEntryStatus.PENDING, null, null),
            TimeEntryOrder.CREATED_AT_ASC,
            PageQuery.of(null, null));

    assertThat(byDate.items())
        .extracting(v -> v.entry().id())
        .containsExactly(nov5.id(), nov4Late.id(), nov4Early.id(), nov3.id());
    assertThat(queue.items())
        .extracting(v -> v.entry().id())
        .containsExactly(nov5.id(), nov3.id(), nov4Early.id(), nov4Late.id());
  }

  // Scenario: Fecha límite de edición en la respuesta
  @Test
  void listExposesEditableUntil() {
    existing(
        LAWYER_A,
        LocalDate.parse("2026-10-31"),
        10,
        TimeEntryStatus.APPROVED,
        Instant.parse("2026-10-31T14:00:00Z"));

    PageResult<TimeEntryView> page =
        service.listTimeEntries(LAWYER_A, TimeEntryFilter.none(), null, PageQuery.of(0, 20));

    assertThat(page.items().get(0).editableUntil()).isEqualTo(NOV_7);
  }

  // Scenario: Paginación por defecto
  @Test
  void defaultPagination() {
    for (int i = 0; i < 45; i++) {
      existing(LAWYER_A, NOV_5.minusDays(i), 10, TimeEntryStatus.PENDING);
    }

    PageResult<TimeEntryView> page =
        service.listTimeEntries(PARTNER, null, null, PageQuery.of(null, null));

    assertThat(page.items()).hasSize(20);
    assertThat(page.page()).isZero();
    assertThat(page.size()).isEqualTo(20);
    assertThat(page.totalItems()).isEqualTo(45);
    assertThat(page.totalPages()).isEqualTo(3);
  }
}
