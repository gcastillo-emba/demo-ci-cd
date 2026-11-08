package com.emba.asuntoshoras.adapters.in.web;

import com.emba.asuntoshoras.adapters.in.web.api.TimeEntriesApi;
import com.emba.asuntoshoras.adapters.in.web.api.model.RejectRequest;
import com.emba.asuntoshoras.adapters.in.web.api.model.TimeEntry;
import com.emba.asuntoshoras.adapters.in.web.api.model.TimeEntryCreate;
import com.emba.asuntoshoras.adapters.in.web.api.model.TimeEntryPage;
import com.emba.asuntoshoras.adapters.in.web.api.model.TimeEntrySort;
import com.emba.asuntoshoras.adapters.in.web.api.model.TimeEntryStatus;
import com.emba.asuntoshoras.adapters.in.web.api.model.TimeEntryUpdate;
import com.emba.asuntoshoras.application.port.in.PageQuery;
import com.emba.asuntoshoras.application.port.in.TimeEntryUseCases;
import com.emba.asuntoshoras.application.port.in.TimeEntryUseCases.NewTimeEntry;
import com.emba.asuntoshoras.application.port.in.TimeEntryUseCases.TimeEntryChanges;
import com.emba.asuntoshoras.application.port.in.TimeEntryUseCases.TimeEntryFilter;
import com.emba.asuntoshoras.application.port.in.TimeEntryView;
import java.beans.PropertyEditorSupport;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implementa {@link TimeEntriesApi} (generada desde el contrato). Solo traduce DTOs: las horas
 * llegan crudas ({@code BigDecimal}) y el dominio aplica incremento y rango (design D1).
 */
@RestController
public class TimeEntriesController implements TimeEntriesApi {

  private final TimeEntryUseCases timeEntries;

  public TimeEntriesController(TimeEntryUseCases timeEntries) {
    this.timeEntries = timeEntries;
  }

  /**
   * {@code sort} se convierte por el valor del contrato ({@code workDate,desc} | {@code
   * createdAt,asc}) y no por el nombre de la constante Java. Se usa un editor (y no un {@code
   * Converter}) porque, si el {@code ConversionService} falla, Spring reintenta con {@code
   * Enum.valueOf} y aceptaría {@code WORK_DATE_DESC}. Un valor no admitido lanza {@link
   * IllegalArgumentException} → {@code 400 validation-error} con {@code errors[].field = sort}.
   */
  @InitBinder
  void registerSortEditor(WebDataBinder binder) {
    binder.registerCustomEditor(
        TimeEntrySort.class,
        new PropertyEditorSupport() {
          @Override
          public void setAsText(String text) {
            setValue(TimeEntrySort.fromValue(text));
          }
        });
  }

  @Override
  public ResponseEntity<TimeEntry> createTimeEntry(TimeEntryCreate body) {
    TimeEntryView created =
        timeEntries.createTimeEntry(
            CurrentUserId.requireUser(),
            new NewTimeEntry(
                body.getMatterId(), body.getWorkDate(), body.getHours(), body.getDescription()));
    return ResponseEntity.created(URI.create("/api/time-entries/" + created.entry().id()))
        .body(ApiMapper.toDto(created));
  }

  @Override
  public ResponseEntity<TimeEntry> updateTimeEntry(UUID id, TimeEntryUpdate body) {
    TimeEntryView updated =
        timeEntries.updateTimeEntry(
            CurrentUserId.requireUser(),
            id,
            new TimeEntryChanges(
                body.getMatterId(),
                body.getWorkDate(),
                body.getHours(),
                body.getDescription(),
                body.getVersion()));
    return ResponseEntity.ok(ApiMapper.toDto(updated));
  }

  @Override
  public ResponseEntity<TimeEntry> approveTimeEntry(UUID id) {
    return ResponseEntity.ok(
        ApiMapper.toDto(timeEntries.approveTimeEntry(CurrentUserId.requireUser(), id)));
  }

  @Override
  public ResponseEntity<TimeEntry> rejectTimeEntry(UUID id, RejectRequest body) {
    return ResponseEntity.ok(
        ApiMapper.toDto(
            timeEntries.rejectTimeEntry(CurrentUserId.requireUser(), id, body.getReason())));
  }

  @Override
  public ResponseEntity<TimeEntryPage> listTimeEntries(
      UUID matterId,
      UUID lawyerId,
      TimeEntryStatus status,
      LocalDate from,
      LocalDate to,
      Integer page,
      Integer size,
      TimeEntrySort sort) {
    TimeEntryFilter filter =
        new TimeEntryFilter(matterId, lawyerId, ApiMapper.toDomain(status), from, to);
    return ResponseEntity.ok(
        ApiMapper.toTimeEntryPage(
            timeEntries.listTimeEntries(
                CurrentUserId.requireUser(),
                filter,
                ApiMapper.toDomain(sort),
                PageQuery.of(page, size))));
  }
}
