package com.emba.asuntoshoras.adapters.in.web;

import com.emba.asuntoshoras.adapters.in.web.api.DashboardApi;
import com.emba.asuntoshoras.adapters.in.web.api.model.DashboardSummary;
import com.emba.asuntoshoras.adapters.in.web.api.model.HoursTotals;
import com.emba.asuntoshoras.adapters.in.web.api.model.LawyerHoursRow;
import com.emba.asuntoshoras.adapters.in.web.api.model.MatterHoursRow;
import com.emba.asuntoshoras.application.port.in.GetDashboardSummary;
import com.emba.asuntoshoras.domain.dashboard.LawyerHours;
import com.emba.asuntoshoras.domain.dashboard.MatterHours;
import com.emba.asuntoshoras.domain.shared.DomainValidationException;
import com.emba.asuntoshoras.domain.shared.DomainValidationException.Violation;
import com.emba.asuntoshoras.domain.timeentry.Hours;
import java.beans.PropertyEditorSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implementa {@link DashboardApi} (generada desde el contrato, add-hours-dashboard). Traduce las
 * fechas de la consulta y el resumen; la ventana y el alcance los decide el caso de uso.
 *
 * <p>Las fechas se convierten con un editor propio y estricto ({@code AAAA-MM-DD}, sin fechas
 * inexistentes como {@code 2026-02-30}). Un valor mal formado no corta el binding: se marca con un
 * centinela y se reportan juntos todos los parámetros mal formados (un elemento de {@code errors[]}
 * por parámetro), sin copiar el valor recibido.
 */
@RestController
public class DashboardController implements DashboardApi {

  static final String INVALID_DATE_MESSAGE = "Debe ser una fecha válida con formato AAAA-MM-DD.";

  /** Centinela de fecha mal formada: el editor nunca la produce desde un texto válido. */
  private static final LocalDate MALFORMED = LocalDate.MIN;

  private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

  private final GetDashboardSummary dashboard;

  public DashboardController(GetDashboardSummary dashboard) {
    this.dashboard = dashboard;
  }

  @InitBinder
  void registerDateEditor(WebDataBinder binder) {
    binder.registerCustomEditor(LocalDate.class, new StrictIsoDateEditor());
  }

  @Override
  public ResponseEntity<DashboardSummary> getDashboardSummary(LocalDate from, LocalDate to) {
    List<Violation> malformed = new ArrayList<>();
    if (MALFORMED.equals(from)) {
      malformed.add(new Violation("from", INVALID_DATE_MESSAGE));
    }
    if (MALFORMED.equals(to)) {
      malformed.add(new Violation("to", INVALID_DATE_MESSAGE));
    }
    if (!malformed.isEmpty()) {
      throw new DomainValidationException(malformed);
    }
    return ResponseEntity.ok(
        toDto(dashboard.getDashboardSummary(CurrentUserId.requireUser(), from, to)));
  }

  static DashboardSummary toDto(com.emba.asuntoshoras.domain.dashboard.DashboardSummary summary) {
    return new DashboardSummary()
        .from(summary.window().from())
        .to(summary.window().to())
        .totals(toDto(summary.totals()))
        .byMatter(summary.byMatter().stream().map(DashboardController::toDto).toList())
        .byLawyer(summary.byLawyer().stream().map(DashboardController::toDto).toList());
  }

  private static MatterHoursRow toDto(MatterHours row) {
    return new MatterHoursRow()
        .matterId(row.matterId())
        .matterCode(row.matterCode())
        .matterTitle(row.matterTitle())
        .approvedHours(decimal(row.hours().approved()))
        .pendingHours(decimal(row.hours().pending()));
  }

  private static LawyerHoursRow toDto(LawyerHours row) {
    return new LawyerHoursRow()
        .lawyerId(row.lawyerId())
        .lawyerName(row.lawyerName())
        .approvedHours(decimal(row.hours().approved()))
        .pendingHours(decimal(row.hours().pending()));
  }

  private static HoursTotals toDto(com.emba.asuntoshoras.domain.dashboard.HoursTotals totals) {
    return new HoursTotals()
        .approvedHours(decimal(totals.approved()))
        .pendingHours(decimal(totals.pending()));
  }

  /** Décimas enteras → decimal con exactamente una décima ({@code 35 → 3.5}, {@code 0 → 0.0}). */
  static BigDecimal decimal(Hours hours) {
    return BigDecimal.valueOf(hours.tenths(), 1);
  }

  /** Editor estricto de fechas ISO; un texto vacío equivale a parámetro ausente. */
  private static final class StrictIsoDateEditor extends PropertyEditorSupport {

    @Override
    public void setAsText(String text) {
      if (text == null || text.isBlank()) {
        setValue(null);
        return;
      }
      if (!ISO_DATE.matcher(text).matches()) {
        setValue(MALFORMED);
        return;
      }
      try {
        // ISO_LOCAL_DATE es STRICT: rechaza 2026-02-30.
        setValue(LocalDate.parse(text));
      } catch (DateTimeParseException e) {
        setValue(MALFORMED);
      }
    }
  }
}
