package com.emba.asuntoshoras.application.service;

import com.emba.asuntoshoras.application.port.in.CurrentUser;
import com.emba.asuntoshoras.application.port.in.MatterView;
import com.emba.asuntoshoras.application.port.in.PageQuery;
import com.emba.asuntoshoras.application.port.in.PageResult;
import com.emba.asuntoshoras.application.port.in.TimeEntryUseCases;
import com.emba.asuntoshoras.application.port.in.TimeEntryView;
import com.emba.asuntoshoras.application.port.out.DailyHoursLock;
import com.emba.asuntoshoras.application.port.out.MatterRepository;
import com.emba.asuntoshoras.application.port.out.TimeEntryRepository;
import com.emba.asuntoshoras.application.port.out.TimeEntryRepository.TimeEntryListing;
import com.emba.asuntoshoras.domain.matter.Matter;
import com.emba.asuntoshoras.domain.matter.MatterNotFoundException;
import com.emba.asuntoshoras.domain.shared.InvalidValueException;
import com.emba.asuntoshoras.domain.shared.Text;
import com.emba.asuntoshoras.domain.timeentry.ConcurrentModificationException;
import com.emba.asuntoshoras.domain.timeentry.DailyHoursPolicy;
import com.emba.asuntoshoras.domain.timeentry.Hours;
import com.emba.asuntoshoras.domain.timeentry.TimeEntry;
import com.emba.asuntoshoras.domain.timeentry.TimeEntryNotFoundException;
import com.emba.asuntoshoras.domain.timeentry.TimeEntryRules;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta, edición, aprobación, rechazo y listado de registros de horas. La autorización por rol y por
 * propiedad vive aquí (defensa en profundidad: las reglas por URL ya devuelven 403 por rol antes de
 * la validación del cuerpo). Orden de evaluación (design D5): rol (403) → forma (400) → existencia
 * del registro y del asunto (404) → propiedad (403) → estado (409) → versión (409) → ventana (422)
 * → asunto cerrado → fecha futura → incremento → rango → límite diario (422).
 */
public class TimeEntryService implements TimeEntryUseCases {

  private final TimeEntryRepository entries;
  private final MatterRepository matters;
  private final DailyHoursLock dailyHoursLock;
  private final Clock clock;

  public TimeEntryService(
      TimeEntryRepository entries,
      MatterRepository matters,
      DailyHoursLock dailyHoursLock,
      Clock clock) {
    this.entries = Objects.requireNonNull(entries);
    this.matters = Objects.requireNonNull(matters);
    this.dailyHoursLock = Objects.requireNonNull(dailyHoursLock);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  @Transactional
  public TimeEntryView createTimeEntry(CurrentUser user, NewTimeEntry command) {
    Authorization.requireLawyer(user);
    String description =
        validateShape(
            command.matterId(), command.workDate(), command.hours(), command.description());
    Matter matter = findMatter(command.matterId());
    TimeEntryRules.ensureMatterOpen(matter);
    Instant now = now();
    TimeEntryRules.ensureNotFuture(command.workDate(), today(now));
    Hours hours = Hours.of(command.hours());

    dailyHoursLock.lock(user.userId(), List.of(command.workDate()));
    DailyHoursPolicy.check(entries.sumCountedHours(user.userId(), command.workDate(), null), hours);

    TimeEntry entry =
        TimeEntry.record(
            UUID.randomUUID(),
            matter.id(),
            user.userId(),
            command.workDate(),
            hours,
            description,
            now);
    entries.insert(entry);
    return view(entry.id());
  }

  @Override
  @Transactional
  public TimeEntryView updateTimeEntry(
      CurrentUser user, UUID timeEntryId, TimeEntryChanges changes) {
    Authorization.requireLawyer(user);
    String description =
        validateShape(
            changes.matterId(), changes.workDate(), changes.hours(), changes.description());
    if (changes.version() == null) {
      throw new InvalidValueException("version", "La versión es obligatoria.");
    }
    TimeEntry entry = findForUpdate(timeEntryId);
    Matter matter = findMatter(changes.matterId());
    entry.ensureOwnedBy(user.userId());
    entry.ensurePending();
    entry.ensureVersion(changes.version());
    LocalDate today = today(now());
    entry.ensureEditableOn(today, zone());
    TimeEntryRules.ensureMatterOpen(matter);
    TimeEntryRules.ensureNotFuture(changes.workDate(), today);
    Hours hours = Hours.of(changes.hours());

    // Bloquea la fecha original y la nueva (en orden ascendente, design D3).
    dailyHoursLock.lock(user.userId(), List.of(entry.workDate(), changes.workDate()));
    DailyHoursPolicy.check(
        entries.sumCountedHours(user.userId(), changes.workDate(), entry.id()), hours);

    entry.revise(matter.id(), changes.workDate(), hours, description);
    if (!entries.update(entry)) {
      throw new ConcurrentModificationException();
    }
    return view(entry.id());
  }

  @Override
  @Transactional
  public TimeEntryView approveTimeEntry(CurrentUser user, UUID timeEntryId) {
    Authorization.requirePartner(user);
    TimeEntry entry = findForUpdate(timeEntryId);
    // Supuesto 13: aprobar no evalúa matter-closed.
    entry.approve();
    if (!entries.update(entry)) {
      throw new ConcurrentModificationException();
    }
    return view(entry.id());
  }

  @Override
  @Transactional
  public TimeEntryView rejectTimeEntry(CurrentUser user, UUID timeEntryId, String reason) {
    Authorization.requirePartner(user);
    TimeEntry entry = findForUpdate(timeEntryId);
    entry.reject(reason);
    if (!entries.update(entry)) {
      throw new ConcurrentModificationException();
    }
    return view(entry.id());
  }

  @Override
  @Transactional(readOnly = true)
  public PageResult<TimeEntryView> listTimeEntries(
      CurrentUser user, TimeEntryFilter filter, TimeEntryOrder order, PageQuery page) {
    CurrentUser caller = Authorization.requireAuthenticated(user);
    TimeEntryFilter requested = filter == null ? TimeEntryFilter.none() : filter;
    if (requested.from() != null
        && requested.to() != null
        && requested.from().isAfter(requested.to())) {
      throw new InvalidValueException(
          "from", "La fecha inicial no puede ser posterior a la final.");
    }
    // El Abogado solo ve lo suyo: su filtro lawyerId se ignora (Supuesto 3).
    UUID lawyerId = caller.isPartner() ? requested.lawyerId() : caller.userId();
    TimeEntryFilter scoped =
        new TimeEntryFilter(
            requested.matterId(), lawyerId, requested.status(), requested.from(), requested.to());
    return entries
        .search(scoped, order == null ? TimeEntryOrder.WORK_DATE_DESC : order, page)
        .map(this::toView);
  }

  /** Valida la forma (400) y devuelve la descripción recortada. */
  private static String validateShape(
      UUID matterId, LocalDate workDate, BigDecimal hours, String description) {
    if (matterId == null) {
      throw new InvalidValueException("matterId", "El asunto es obligatorio.");
    }
    if (workDate == null) {
      throw new InvalidValueException("workDate", "La fecha de trabajo es obligatoria.");
    }
    if (hours == null) {
      throw new InvalidValueException("hours", "Las horas son obligatorias.");
    }
    return Text.required("description", description, TimeEntry.DESCRIPTION_MAX_LENGTH);
  }

  private TimeEntry findForUpdate(UUID timeEntryId) {
    return entries
        .findByIdForUpdate(timeEntryId)
        .orElseThrow(() -> new TimeEntryNotFoundException(timeEntryId));
  }

  private Matter findMatter(UUID matterId) {
    return matters
        .findById(matterId)
        .map(MatterView::matter)
        .orElseThrow(() -> new MatterNotFoundException(matterId));
  }

  private TimeEntryView view(UUID timeEntryId) {
    return entries
        .findListing(timeEntryId)
        .map(this::toView)
        .orElseThrow(() -> new TimeEntryNotFoundException(timeEntryId));
  }

  private TimeEntryView toView(TimeEntryListing listing) {
    return new TimeEntryView(
        listing.entry(),
        listing.matterCode(),
        listing.lawyerName(),
        listing.entry().editableUntil(zone()));
  }

  private ZoneId zone() {
    return clock.getZone();
  }

  private LocalDate today(Instant now) {
    return TimeEntryRules.today(now, zone());
  }

  /** Instante actual con la precisión de PostgreSQL (microsegundos). */
  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
