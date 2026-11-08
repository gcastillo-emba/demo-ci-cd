package com.emba.asuntoshoras.application.port.in;

import com.emba.asuntoshoras.domain.timeentry.TimeEntryStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Casos de uso de registros de horas: alta y edición (solo Abogado, sobre lo propio), aprobación y
 * rechazo (solo Socio) y listado con alcance por rol. Orden de evaluación de errores: design D5.
 */
public interface TimeEntryUseCases {

  /**
   * Registra horas {@code PENDING} del abogado autenticado.
   *
   * @throws com.emba.asuntoshoras.domain.shared.ForbiddenException si el usuario no es Abogado
   * @throws com.emba.asuntoshoras.domain.matter.MatterNotFoundException si el asunto no existe
   */
  TimeEntryView createTimeEntry(CurrentUser user, NewTimeEntry command);

  /**
   * Edita un registro propio, pendiente y dentro de la ventana, con la {@code version} vigente.
   *
   * @throws com.emba.asuntoshoras.domain.timeentry.TimeEntryNotFoundException si no existe
   * @throws com.emba.asuntoshoras.domain.timeentry.NotEntryOwnerException si no es del autor
   */
  TimeEntryView updateTimeEntry(CurrentUser user, UUID timeEntryId, TimeEntryChanges changes);

  /**
   * @throws com.emba.asuntoshoras.domain.shared.ForbiddenException si el usuario no es Socio
   * @throws com.emba.asuntoshoras.domain.timeentry.EntryNotPendingException si no está pendiente
   */
  TimeEntryView approveTimeEntry(CurrentUser user, UUID timeEntryId);

  /**
   * @throws com.emba.asuntoshoras.domain.shared.ForbiddenException si el usuario no es Socio
   * @throws com.emba.asuntoshoras.domain.timeentry.RejectionReasonRequiredException si el motivo
   *     falta, está en blanco o supera 500 caracteres
   */
  TimeEntryView rejectTimeEntry(CurrentUser user, UUID timeEntryId, String reason);

  /**
   * Lista registros: el Socio ve todos (puede filtrar por abogado); el Abogado solo los suyos (su
   * filtro {@code lawyerId} se ignora, Supuesto 3).
   *
   * @throws com.emba.asuntoshoras.domain.shared.InvalidValueException si {@code from > to}
   */
  PageResult<TimeEntryView> listTimeEntries(
      CurrentUser user, TimeEntryFilter filter, TimeEntryOrder order, PageQuery page);

  /** Datos de alta (el autor, el estado y la creación los fija el servidor). */
  record NewTimeEntry(UUID matterId, LocalDate workDate, BigDecimal hours, String description) {}

  /** Datos editables más la {@code version} leída. */
  record TimeEntryChanges(
      UUID matterId, LocalDate workDate, BigDecimal hours, String description, Long version) {}

  /**
   * Filtros opcionales combinables; {@code from}..{@code to} es inclusivo sobre {@code workDate}.
   */
  record TimeEntryFilter(
      UUID matterId, UUID lawyerId, TimeEntryStatus status, LocalDate from, LocalDate to) {

    public static TimeEntryFilter none() {
      return new TimeEntryFilter(null, null, null, null, null);
    }
  }

  /** Orden del listado (enum cerrado, design D7). */
  enum TimeEntryOrder {
    /** {@code workDate} descendente; a igual fecha, {@code createdAt} descendente. */
    WORK_DATE_DESC,
    /** {@code createdAt} ascendente (cola de aprobaciones). */
    CREATED_AT_ASC
  }
}
