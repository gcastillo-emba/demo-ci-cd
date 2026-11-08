package com.emba.asuntoshoras.application.fakes;

import com.emba.asuntoshoras.application.port.in.PageQuery;
import com.emba.asuntoshoras.application.port.in.PageResult;
import com.emba.asuntoshoras.application.port.in.TimeEntryUseCases.TimeEntryFilter;
import com.emba.asuntoshoras.application.port.in.TimeEntryUseCases.TimeEntryOrder;
import com.emba.asuntoshoras.application.port.out.MatterRepository;
import com.emba.asuntoshoras.application.port.out.TimeEntryRepository;
import com.emba.asuntoshoras.domain.timeentry.Hours;
import com.emba.asuntoshoras.domain.timeentry.TimeEntry;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Fake en memoria del puerto {@link TimeEntryRepository} (sin Mockito, ADR-002). Guarda copias para
 * imitar la persistencia: los cambios del agregado leído no se ven hasta {@code update}, que
 * incrementa la versión.
 */
public final class InMemoryTimeEntryRepository implements TimeEntryRepository {

  private final Map<UUID, TimeEntry> entries = new LinkedHashMap<>();
  private final Map<UUID, String> lawyerNames = new HashMap<>();
  private final MatterRepository matters;
  private Consumer<UUID> beforeUpdate = id -> {};

  public InMemoryTimeEntryRepository(MatterRepository matters) {
    this.matters = matters;
  }

  public InMemoryTimeEntryRepository lawyer(UUID id, String name) {
    lawyerNames.put(id, name);
    return this;
  }

  public InMemoryTimeEntryRepository with(TimeEntry entry) {
    entries.put(entry.id(), copy(entry, 0));
    return this;
  }

  /** Acción que se ejecuta justo antes de persistir una modificación (simula una carrera). */
  public InMemoryTimeEntryRepository beforeUpdate(Consumer<UUID> action) {
    this.beforeUpdate = action;
    return this;
  }

  /** Simula que otro proceso modificó el registro (incrementa la versión). */
  public void touchConcurrently(UUID id) {
    entries.put(id, copy(entries.get(id), 1));
  }

  public Optional<TimeEntry> stored(UUID id) {
    return Optional.ofNullable(entries.get(id)).map(e -> copy(e, 0));
  }

  public int count() {
    return entries.size();
  }

  @Override
  public void insert(TimeEntry entry) {
    entries.put(entry.id(), copy(entry, 0));
  }

  @Override
  public Optional<TimeEntry> findByIdForUpdate(UUID id) {
    return stored(id);
  }

  @Override
  public Optional<TimeEntryListing> findListing(UUID id) {
    return stored(id).map(this::listing);
  }

  @Override
  public boolean update(TimeEntry entry) {
    beforeUpdate.accept(entry.id());
    TimeEntry current = entries.get(entry.id());
    if (current == null || current.version() != entry.version()) {
      return false;
    }
    entries.put(entry.id(), copy(entry, 1));
    return true;
  }

  @Override
  public Hours sumCountedHours(UUID lawyerId, LocalDate workDate, UUID excludedEntryId) {
    return Hours.sumOfTenths(
        entries.values().stream()
            .filter(e -> e.lawyerId().equals(lawyerId))
            .filter(e -> e.workDate().equals(workDate))
            .filter(e -> e.status().countsTowardsDailyLimit())
            .filter(e -> !e.id().equals(excludedEntryId))
            .mapToLong(e -> e.hours().tenths())
            .sum());
  }

  @Override
  public PageResult<TimeEntryListing> search(
      TimeEntryFilter filter, TimeEntryOrder order, PageQuery page) {
    Comparator<TimeEntry> comparator =
        order == TimeEntryOrder.CREATED_AT_ASC
            ? Comparator.comparing(TimeEntry::createdAt)
            : Comparator.comparing(TimeEntry::workDate)
                .thenComparing(TimeEntry::createdAt)
                .reversed();
    List<TimeEntryListing> matching =
        entries.values().stream()
            .filter(e -> filter.matterId() == null || filter.matterId().equals(e.matterId()))
            .filter(e -> filter.lawyerId() == null || filter.lawyerId().equals(e.lawyerId()))
            .filter(e -> filter.status() == null || filter.status() == e.status())
            .filter(e -> filter.from() == null || !e.workDate().isBefore(filter.from()))
            .filter(e -> filter.to() == null || !e.workDate().isAfter(filter.to()))
            .sorted(comparator)
            .map(this::listing)
            .toList();
    return Pages.slice(matching, page);
  }

  private TimeEntryListing listing(TimeEntry entry) {
    String code =
        matters.findById(entry.matterId()).map(v -> v.matter().code().value()).orElse("?");
    return new TimeEntryListing(entry, code, lawyerNames.getOrDefault(entry.lawyerId(), "?"));
  }

  private static TimeEntry copy(TimeEntry e, long versionIncrement) {
    return TimeEntry.restore(
        e.id(),
        e.matterId(),
        e.lawyerId(),
        e.workDate(),
        e.hours(),
        e.description(),
        e.status(),
        e.rejectionReason(),
        e.createdAt(),
        e.version() + versionIncrement);
  }
}
