package com.emba.asuntoshoras.domain.timeentry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emba.asuntoshoras.domain.matter.Matter;
import com.emba.asuntoshoras.domain.matter.MatterCode;
import com.emba.asuntoshoras.domain.shared.InvalidValueException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Reglas del agregado TimeEntry con {@link Clock#fixed} en {@code America/La_Paz} (design D2). */
class TimeEntryTest {

  private static final ZoneId LA_PAZ = ZoneId.of("America/La_Paz");
  private static final UUID LAWYER = UUID.randomUUID();
  private static final UUID OTHER_LAWYER = UUID.randomUUID();
  private static final UUID MATTER = UUID.randomUUID();

  /** 2026-10-31 10:00 en La Paz. */
  private static final Instant CREATED = Instant.parse("2026-10-31T14:00:00Z");

  private static TimeEntry pending() {
    return TimeEntry.record(
        UUID.randomUUID(),
        MATTER,
        LAWYER,
        LocalDate.parse("2026-10-30"),
        Hours.ofTenths(15),
        "Revisión de contrato ficticio",
        CREATED);
  }

  private static TimeEntry withStatus(TimeEntryStatus status, String reason) {
    return TimeEntry.restore(
        UUID.randomUUID(),
        MATTER,
        LAWYER,
        LocalDate.parse("2026-10-30"),
        Hours.ofTenths(15),
        "Revisión",
        status,
        reason,
        CREATED,
        3);
  }

  private static LocalDate today(Clock clock) {
    return TimeEntryRules.today(clock.instant(), clock.getZone());
  }

  // Scenario: Registro válido
  @Test
  void recordStartsPendingWithoutReasonAndVersionZero() {
    TimeEntry entry = pending();

    assertThat(entry.status()).isEqualTo(TimeEntryStatus.PENDING);
    assertThat(entry.isPending()).isTrue();
    assertThat(entry.rejectionReason()).isNull();
    assertThat(entry.version()).isZero();
    assertThat(entry.lawyerId()).isEqualTo(LAWYER);
    assertThat(entry.matterId()).isEqualTo(MATTER);
    assertThat(entry.hours().tenths()).isEqualTo(15);
    assertThat(entry.createdAt()).isEqualTo(CREATED);
    assertThat(entry.description()).isEqualTo("Revisión de contrato ficticio");
    assertThat(entry.workDate()).isEqualTo(LocalDate.parse("2026-10-30"));
    assertThat(entry)
        .hasToString("TimeEntry[id=" + entry.id() + ", status=PENDING, workDate=2026-10-30]");
  }

  @Test
  void descriptionIsTrimmedAndLimitedTo2000() {
    TimeEntry entry =
        TimeEntry.record(
            UUID.randomUUID(),
            MATTER,
            LAWYER,
            LocalDate.parse("2026-10-30"),
            Hours.ofTenths(1),
            "  " + "d".repeat(2000) + "  ",
            CREATED);
    assertThat(entry.description()).hasSize(2000);

    assertThatThrownBy(
            () ->
                TimeEntry.record(
                    UUID.randomUUID(),
                    MATTER,
                    LAWYER,
                    LocalDate.parse("2026-10-30"),
                    Hours.ofTenths(1),
                    "d".repeat(2001),
                    CREATED))
        .isInstanceOf(InvalidValueException.class)
        .extracting(e -> ((InvalidValueException) e).field())
        .isEqualTo("description");
  }

  @Test
  void requiredFieldsAreValidated() {
    LocalDate day = LocalDate.parse("2026-10-30");
    Hours one = Hours.ofTenths(10);
    assertThatThrownBy(() -> TimeEntry.record(null, MATTER, LAWYER, day, one, "d", CREATED))
        .isInstanceOf(InvalidValueException.class);
    assertThatThrownBy(
            () -> TimeEntry.record(UUID.randomUUID(), null, LAWYER, day, one, "d", CREATED))
        .isInstanceOf(InvalidValueException.class);
    assertThatThrownBy(
            () -> TimeEntry.record(UUID.randomUUID(), MATTER, null, day, one, "d", CREATED))
        .isInstanceOf(InvalidValueException.class);
    assertThatThrownBy(
            () -> TimeEntry.record(UUID.randomUUID(), MATTER, LAWYER, null, one, "d", CREATED))
        .isInstanceOf(InvalidValueException.class);
    assertThatThrownBy(
            () -> TimeEntry.record(UUID.randomUUID(), MATTER, LAWYER, day, null, "d", CREATED))
        .isInstanceOf(InvalidValueException.class);
    assertThatThrownBy(
            () ->
                TimeEntry.record(UUID.randomUUID(), MATTER, LAWYER, day, Hours.ZERO, "d", CREATED))
        .isInstanceOf(InvalidValueException.class);
    assertThatThrownBy(
            () ->
                TimeEntry.record(
                    UUID.randomUUID(), MATTER, LAWYER, day, new Hours(241), "d", CREATED))
        .isInstanceOf(HoursOutOfRangeException.class);
    assertThatThrownBy(
            () -> TimeEntry.record(UUID.randomUUID(), MATTER, LAWYER, day, one, " ", CREATED))
        .isInstanceOf(InvalidValueException.class);
    assertThatThrownBy(
            () -> TimeEntry.record(UUID.randomUUID(), MATTER, LAWYER, day, one, "d", null))
        .isInstanceOf(InvalidValueException.class);
  }

  @Test
  void restoreValidatesStatusReasonAndVersion() {
    assertThat(withStatus(TimeEntryStatus.REJECTED, " Motivo ").rejectionReason())
        .isEqualTo("Motivo");
    assertThat(withStatus(TimeEntryStatus.APPROVED, null).version()).isEqualTo(3);
    assertThatThrownBy(() -> withStatus(null, null)).isInstanceOf(InvalidValueException.class);
    assertThatThrownBy(() -> withStatus(TimeEntryStatus.REJECTED, null))
        .isInstanceOf(InvalidValueException.class);
    assertThatThrownBy(() -> withStatus(TimeEntryStatus.APPROVED, "x"))
        .isInstanceOf(InvalidValueException.class);
    assertThatThrownBy(() -> withStatus(TimeEntryStatus.REJECTED, " "))
        .isInstanceOf(InvalidValueException.class);
    assertThatThrownBy(() -> withStatus(TimeEntryStatus.REJECTED, "r".repeat(501)))
        .isInstanceOf(InvalidValueException.class);
    assertThatThrownBy(
            () ->
                TimeEntry.restore(
                    UUID.randomUUID(),
                    MATTER,
                    LAWYER,
                    LocalDate.parse("2026-10-30"),
                    Hours.ofTenths(1),
                    "d",
                    TimeEntryStatus.PENDING,
                    null,
                    CREATED,
                    -1))
        .isInstanceOf(InvalidValueException.class);
  }

  // Scenario: Fecha límite de edición en la respuesta
  @Test
  void editableUntilIsCreationDateInLaPazPlusSevenDays() {
    assertThat(pending().editableUntil(LA_PAZ)).isEqualTo(LocalDate.parse("2026-11-07"));
  }

  @Test
  void editableUntilUsesTheLaPazDateNotTheUtcDate() {
    // 2026-11-01T02:30Z es todavía 2026-10-31 22:30 en La Paz.
    TimeEntry lateNight =
        TimeEntry.record(
            UUID.randomUUID(),
            MATTER,
            LAWYER,
            LocalDate.parse("2026-10-31"),
            Hours.ofTenths(1),
            "d",
            Instant.parse("2026-11-01T02:30:00Z"));

    assertThat(lateNight.editableUntil(LA_PAZ)).isEqualTo(LocalDate.parse("2026-11-07"));
    assertThat(lateNight.editableUntil(ZoneId.of("UTC"))).isEqualTo(LocalDate.parse("2026-11-08"));
  }

  // Scenario: Último día de la ventana
  @Test
  void lastDayOfTheWindowIsEditable() {
    Clock lastMinute = Clock.fixed(Instant.parse("2026-11-08T03:59:59Z"), LA_PAZ);

    pending().ensureEditableOn(today(lastMinute), LA_PAZ);
  }

  // Scenario: Ventana vencida
  @Test
  void dayAfterTheWindowIsExpired() {
    TimeEntry createdOn30 =
        TimeEntry.record(
            UUID.randomUUID(),
            MATTER,
            LAWYER,
            LocalDate.parse("2026-10-30"),
            Hours.ofTenths(1),
            "d",
            Instant.parse("2026-10-30T14:00:00Z"));
    Clock nov7 = Clock.fixed(Instant.parse("2026-11-07T14:00:00Z"), LA_PAZ);

    assertThatThrownBy(() -> createdOn30.ensureEditableOn(today(nov7), LA_PAZ))
        .isInstanceOf(EditWindowExpiredException.class)
        .extracting(e -> ((EditWindowExpiredException) e).code())
        .isEqualTo("edit-window-expired");
  }

  @Test
  void windowBoundaryFollowsLaPazMidnight() {
    // 2026-11-08T04:00Z = 2026-11-08 00:00 en La Paz: la ventana (hasta el 2026-11-07) venció.
    Clock midnight = Clock.fixed(Instant.parse("2026-11-08T04:00:00Z"), LA_PAZ);

    assertThatThrownBy(() -> pending().ensureEditableOn(today(midnight), LA_PAZ))
        .isInstanceOf(EditWindowExpiredException.class);
  }

  // Scenario: Abogado edita un registro ajeno
  @Test
  void onlyTheAuthorOwnsTheEntry() {
    TimeEntry entry = pending();
    entry.ensureOwnedBy(LAWYER);

    assertThatThrownBy(() -> entry.ensureOwnedBy(OTHER_LAWYER))
        .isInstanceOf(NotEntryOwnerException.class)
        .extracting(e -> ((NotEntryOwnerException) e).code())
        .isEqualTo("not-entry-owner");
  }

  // Scenario: Versión obsoleta
  @Test
  void staleVersionIsConcurrentModification() {
    TimeEntry entry = withStatus(TimeEntryStatus.PENDING, null);
    entry.ensureVersion(3);

    assertThatThrownBy(() -> entry.ensureVersion(2))
        .isInstanceOf(ConcurrentModificationException.class)
        .extracting(e -> ((ConcurrentModificationException) e).code())
        .isEqualTo("concurrent-modification");
  }

  // Scenario: Edición válida
  @Test
  void reviseChangesOnlyEditableFields() {
    TimeEntry entry = pending();
    UUID otherMatter = UUID.randomUUID();

    entry.revise(otherMatter, LocalDate.parse("2026-11-01"), Hours.ofTenths(35), " Nueva ");

    assertThat(entry.matterId()).isEqualTo(otherMatter);
    assertThat(entry.workDate()).isEqualTo(LocalDate.parse("2026-11-01"));
    assertThat(entry.hours().tenths()).isEqualTo(35);
    assertThat(entry.description()).isEqualTo("Nueva");
    assertThat(entry.status()).isEqualTo(TimeEntryStatus.PENDING);
    assertThat(entry.lawyerId()).isEqualTo(LAWYER);
    assertThat(entry.createdAt()).isEqualTo(CREATED);
    assertThat(entry.rejectionReason()).isNull();
  }

  // Scenario: Editar un registro aprobado / Editar un registro rechazado
  @Test
  void terminalEntriesCannotBeRevised() {
    TimeEntry approved = withStatus(TimeEntryStatus.APPROVED, null);
    TimeEntry rejected = withStatus(TimeEntryStatus.REJECTED, "Motivo");

    assertThatThrownBy(approved::ensurePending).isInstanceOf(EntryNotPendingException.class);
    assertThatThrownBy(
            () -> rejected.revise(MATTER, LocalDate.parse("2026-11-01"), Hours.ofTenths(1), "d"))
        .isInstanceOf(EntryNotPendingException.class)
        .extracting(e -> ((EntryNotPendingException) e).code())
        .isEqualTo("entry-not-pending");
    assertThat(rejected.hours().tenths()).isEqualTo(15);
  }

  // Scenario: Socio aprueba un registro pendiente
  @Test
  void approveMovesPendingToApproved() {
    TimeEntry entry = pending();

    entry.approve();

    assertThat(entry.status()).isEqualTo(TimeEntryStatus.APPROVED);
    assertThat(entry.isPending()).isFalse();
  }

  // Scenario: Aprobar un registro ya aprobado / Aprobar un registro rechazado
  @Test
  void approvingTerminalEntriesFails() {
    TimeEntry approved = withStatus(TimeEntryStatus.APPROVED, null);
    TimeEntry rejected = withStatus(TimeEntryStatus.REJECTED, "Motivo");

    assertThatThrownBy(approved::approve).isInstanceOf(EntryNotPendingException.class);
    assertThatThrownBy(rejected::approve).isInstanceOf(EntryNotPendingException.class);
    assertThat(rejected.status()).isEqualTo(TimeEntryStatus.REJECTED);
  }

  // Scenario: Socio rechaza con motivo
  @Test
  void rejectStoresTheTrimmedReason() {
    TimeEntry entry = pending();

    entry.reject("  Descripción insuficiente ");

    assertThat(entry.status()).isEqualTo(TimeEntryStatus.REJECTED);
    assertThat(entry.rejectionReason()).isEqualTo("Descripción insuficiente");
  }

  // Scenario: Rechazo sin motivo / Motivo de 501 caracteres
  @Test
  void rejectRequiresAReasonOfOneTo500Characters() {
    TimeEntry entry = pending();

    for (String invalid : new String[] {null, "", "   ", "r".repeat(501)}) {
      assertThatThrownBy(() -> entry.reject(invalid))
          .isInstanceOf(RejectionReasonRequiredException.class)
          .extracting(e -> ((RejectionReasonRequiredException) e).code())
          .isEqualTo("rejection-reason-required");
    }
    assertThat(entry.status()).isEqualTo(TimeEntryStatus.PENDING);

    entry.reject("r".repeat(500));
    assertThat(entry.rejectionReason()).hasSize(500);
  }

  // Scenario: Rechazar un registro aprobado / Rechazar un registro ya rechazado
  @Test
  void rejectingTerminalEntriesFailsBeforeValidatingTheReason() {
    TimeEntry approved = withStatus(TimeEntryStatus.APPROVED, null);
    TimeEntry rejected = withStatus(TimeEntryStatus.REJECTED, "Previo");

    assertThatThrownBy(() -> approved.reject(null)).isInstanceOf(EntryNotPendingException.class);
    assertThatThrownBy(() -> rejected.reject("Otro")).isInstanceOf(EntryNotPendingException.class);
    assertThat(approved.status()).isEqualTo(TimeEntryStatus.APPROVED);
    assertThat(rejected.rejectionReason()).isEqualTo("Previo");
  }

  // Scenario: Registrar horas de hoy / Registrar horas de mañana
  @Test
  void workDateCannotBeAfterTodayInLaPaz() {
    Clock nov7 = Clock.fixed(Instant.parse("2026-11-07T14:00:00Z"), LA_PAZ);

    TimeEntryRules.ensureNotFuture(LocalDate.parse("2026-11-07"), today(nov7));
    assertThatThrownBy(
            () -> TimeEntryRules.ensureNotFuture(LocalDate.parse("2026-11-08"), today(nov7)))
        .isInstanceOf(FutureDateException.class)
        .extracting(e -> ((FutureDateException) e).code())
        .isEqualTo("future-date");
  }

  // Scenario: Borde de medianoche entre UTC y La Paz
  @Test
  void midnightBoundaryUsesLaPazDate() {
    Clock utcNextDay = Clock.fixed(Instant.parse("2026-11-08T02:30:00Z"), LA_PAZ);
    Clock alsoNov6 = Clock.fixed(Instant.parse("2026-11-07T03:30:00Z"), LA_PAZ);

    assertThat(today(utcNextDay)).isEqualTo(LocalDate.parse("2026-11-07"));
    assertThat(today(alsoNov6)).isEqualTo(LocalDate.parse("2026-11-06"));
    assertThatThrownBy(
            () -> TimeEntryRules.ensureNotFuture(LocalDate.parse("2026-11-08"), today(utcNextDay)))
        .isInstanceOf(FutureDateException.class);
    assertThatThrownBy(
            () -> TimeEntryRules.ensureNotFuture(LocalDate.parse("2026-11-07"), today(alsoNov6)))
        .isInstanceOf(FutureDateException.class);
  }

  // Scenario: Asunto cerrado
  @Test
  void closedMatterRejectsTimeEntries() {
    Matter matter =
        Matter.open(MATTER, MatterCode.of(2026, 1), UUID.randomUUID(), "Asunto", null, CREATED);
    TimeEntryRules.ensureMatterOpen(matter);
    matter.close(CREATED.plusSeconds(60));

    assertThatThrownBy(() -> TimeEntryRules.ensureMatterOpen(matter))
        .isInstanceOf(MatterClosedException.class)
        .extracting(e -> ((MatterClosedException) e).code())
        .isEqualTo("matter-closed");
  }

  @Test
  void equalityIsById() {
    TimeEntry entry = pending();
    TimeEntry same =
        TimeEntry.restore(
            entry.id(),
            MATTER,
            LAWYER,
            LocalDate.parse("2026-10-01"),
            Hours.ofTenths(5),
            "otra",
            TimeEntryStatus.APPROVED,
            null,
            CREATED,
            9);

    assertThat(entry).isEqualTo(same).hasSameHashCodeAs(same).isNotEqualTo(pending());
    assertThat(entry.equals(entry)).isTrue();
    assertThat(entry.equals("x")).isFalse();
  }

  @Test
  void notFoundExposesTheIdAndCode() {
    UUID id = UUID.randomUUID();
    TimeEntryNotFoundException e = new TimeEntryNotFoundException(id);

    assertThat(e.timeEntryId()).isEqualTo(id);
    assertThat(e.code()).isEqualTo("not-found");
    assertThat(e.properties()).isEmpty();
  }

  @Test
  void statusesThatCountTowardsTheDailyLimit() {
    assertThat(TimeEntryStatus.PENDING.countsTowardsDailyLimit()).isTrue();
    assertThat(TimeEntryStatus.APPROVED.countsTowardsDailyLimit()).isTrue();
    assertThat(TimeEntryStatus.REJECTED.countsTowardsDailyLimit()).isFalse();
  }
}
