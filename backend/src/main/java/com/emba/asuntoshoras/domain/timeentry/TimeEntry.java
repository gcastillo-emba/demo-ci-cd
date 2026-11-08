package com.emba.asuntoshoras.domain.timeentry;

import com.emba.asuntoshoras.domain.shared.InvalidValueException;
import com.emba.asuntoshoras.domain.shared.Text;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado Registro de horas. Nace {@code PENDING}; {@code APPROVED} y {@code REJECTED} son
 * terminales. Solo su autor lo edita, mientras esté pendiente y dentro de la ventana de {@value
 * #EDIT_WINDOW_DAYS} días calendario desde su creación (en la zona de la firma). {@code version}
 * sostiene el bloqueo optimista de la edición.
 */
public final class TimeEntry {

  public static final int DESCRIPTION_MAX_LENGTH = 2000;
  public static final int REJECTION_REASON_MAX_LENGTH = 500;
  public static final int EDIT_WINDOW_DAYS = 7;

  private final UUID id;
  private final UUID lawyerId;
  private final Instant createdAt;
  private final long version;
  private UUID matterId;
  private LocalDate workDate;
  private Hours hours;
  private String description;
  private TimeEntryStatus status;
  private String rejectionReason;

  private TimeEntry(
      UUID id,
      UUID matterId,
      UUID lawyerId,
      LocalDate workDate,
      Hours hours,
      String description,
      TimeEntryStatus status,
      String rejectionReason,
      Instant createdAt,
      long version) {
    if (id == null) {
      throw new InvalidValueException("id", "El identificador del registro es obligatorio.");
    }
    if (lawyerId == null) {
      throw new InvalidValueException("lawyerId", "El abogado del registro es obligatorio.");
    }
    if (status == null) {
      throw new InvalidValueException("status", "El estado del registro es obligatorio.");
    }
    if (createdAt == null) {
      throw new InvalidValueException("createdAt", "La fecha de creación es obligatoria.");
    }
    if (version < 0) {
      throw new InvalidValueException("version", "La versión no puede ser negativa.");
    }
    if ((status == TimeEntryStatus.REJECTED) != (rejectionReason != null)) {
      throw new InvalidValueException(
          "rejectionReason", "Un registro tiene motivo de rechazo si y solo si está rechazado.");
    }
    this.id = id;
    this.lawyerId = lawyerId;
    this.createdAt = createdAt;
    this.version = version;
    this.status = status;
    this.rejectionReason =
        rejectionReason == null ? null : validReason(rejectionReason, InvalidValueException::new);
    assign(matterId, workDate, hours, description);
  }

  /** Registra horas nuevas: estado {@code PENDING}, sin motivo de rechazo, versión 0. */
  public static TimeEntry record(
      UUID id,
      UUID matterId,
      UUID lawyerId,
      LocalDate workDate,
      Hours hours,
      String description,
      Instant createdAt) {
    return new TimeEntry(
        id,
        matterId,
        lawyerId,
        workDate,
        hours,
        description,
        TimeEntryStatus.PENDING,
        null,
        createdAt,
        0);
  }

  /** Reconstruye un registro existente (por ejemplo, desde persistencia). */
  public static TimeEntry restore(
      UUID id,
      UUID matterId,
      UUID lawyerId,
      LocalDate workDate,
      Hours hours,
      String description,
      TimeEntryStatus status,
      String rejectionReason,
      Instant createdAt,
      long version) {
    return new TimeEntry(
        id,
        matterId,
        lawyerId,
        workDate,
        hours,
        description,
        status,
        rejectionReason,
        createdAt,
        version);
  }

  /** Última fecha (en {@code zone}) en que puede editarse: fecha de creación + 7 días. */
  public LocalDate editableUntil(ZoneId zone) {
    return createdAt.atZone(zone).toLocalDate().plusDays(EDIT_WINDOW_DAYS);
  }

  /**
   * @throws NotEntryOwnerException si {@code userId} no es el autor
   */
  public void ensureOwnedBy(UUID userId) {
    if (!lawyerId.equals(userId)) {
      throw new NotEntryOwnerException();
    }
  }

  /**
   * @throws EntryNotPendingException si el registro está en un estado terminal
   */
  public void ensurePending() {
    if (status != TimeEntryStatus.PENDING) {
      throw new EntryNotPendingException();
    }
  }

  /**
   * @throws ConcurrentModificationException si {@code expectedVersion} no es la vigente
   */
  public void ensureVersion(long expectedVersion) {
    if (expectedVersion != version) {
      throw new ConcurrentModificationException();
    }
  }

  /**
   * @throws EditWindowExpiredException si {@code today} es posterior a {@link #editableUntil}
   */
  public void ensureEditableOn(LocalDate today, ZoneId zone) {
    if (today.isAfter(editableUntil(zone))) {
      throw new EditWindowExpiredException();
    }
  }

  /**
   * Cambia los datos editables. No toca autor, estado, motivo, creación ni versión (la versión la
   * incrementa la persistencia).
   *
   * @throws EntryNotPendingException si el registro no está pendiente
   */
  public void revise(UUID matterId, LocalDate workDate, Hours hours, String description) {
    ensurePending();
    assign(matterId, workDate, hours, description);
  }

  /**
   * Aprueba el registro.
   *
   * @throws EntryNotPendingException si no está pendiente
   */
  public void approve() {
    ensurePending();
    this.status = TimeEntryStatus.APPROVED;
  }

  /**
   * Rechaza el registro con un motivo (recortado) de 1 a {@value #REJECTION_REASON_MAX_LENGTH}
   * caracteres. Evalúa el estado antes que el motivo (design D5).
   *
   * @throws EntryNotPendingException si no está pendiente
   * @throws RejectionReasonRequiredException si el motivo falta, está en blanco o es muy largo
   */
  public void reject(String reason) {
    ensurePending();
    String valid = validReason(reason, (field, message) -> new RejectionReasonRequiredException());
    this.status = TimeEntryStatus.REJECTED;
    this.rejectionReason = valid;
  }

  private void assign(UUID matterId, LocalDate workDate, Hours hours, String description) {
    if (matterId == null) {
      throw new InvalidValueException("matterId", "El asunto del registro es obligatorio.");
    }
    if (workDate == null) {
      throw new InvalidValueException("workDate", "La fecha de trabajo es obligatoria.");
    }
    if (hours == null || hours.tenths() < Hours.MIN_TENTHS) {
      throw new InvalidValueException("hours", "Las horas son obligatorias.");
    }
    if (hours.tenths() > Hours.MAX_TENTHS) {
      throw new HoursOutOfRangeException();
    }
    String validDescription = Text.required("description", description, DESCRIPTION_MAX_LENGTH);
    this.matterId = matterId;
    this.workDate = workDate;
    this.hours = hours;
    this.description = validDescription;
  }

  private static <E extends RuntimeException> String validReason(
      String reason, java.util.function.BiFunction<String, String, E> error) {
    if (reason == null || reason.isBlank()) {
      throw error.apply("rejectionReason", "El motivo de rechazo es obligatorio.");
    }
    String trimmed = reason.trim();
    if (trimmed.length() > REJECTION_REASON_MAX_LENGTH) {
      throw error.apply("rejectionReason", "El motivo de rechazo no puede superar 500 caracteres.");
    }
    return trimmed;
  }

  public boolean isPending() {
    return status == TimeEntryStatus.PENDING;
  }

  public UUID id() {
    return id;
  }

  public UUID matterId() {
    return matterId;
  }

  public UUID lawyerId() {
    return lawyerId;
  }

  public LocalDate workDate() {
    return workDate;
  }

  public Hours hours() {
    return hours;
  }

  public String description() {
    return description;
  }

  public TimeEntryStatus status() {
    return status;
  }

  /** Motivo de rechazo o {@code null} si no está rechazado. */
  public String rejectionReason() {
    return rejectionReason;
  }

  public Instant createdAt() {
    return createdAt;
  }

  /** Versión leída de persistencia (bloqueo optimista). */
  public long version() {
    return version;
  }

  @Override
  public boolean equals(Object o) {
    return this == o || (o instanceof TimeEntry other && id.equals(other.id));
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "TimeEntry[id=" + id + ", status=" + status + ", workDate=" + workDate + "]";
  }
}
