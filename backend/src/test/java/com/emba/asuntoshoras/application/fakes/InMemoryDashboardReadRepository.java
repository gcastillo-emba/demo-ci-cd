package com.emba.asuntoshoras.application.fakes;

import com.emba.asuntoshoras.application.port.out.DashboardReadRepository;
import com.emba.asuntoshoras.domain.dashboard.DashboardScope;
import com.emba.asuntoshoras.domain.dashboard.DateWindow;
import com.emba.asuntoshoras.domain.dashboard.HoursTotals;
import com.emba.asuntoshoras.domain.dashboard.LawyerHours;
import com.emba.asuntoshoras.domain.dashboard.MatterHours;
import com.emba.asuntoshoras.domain.timeentry.Hours;
import com.emba.asuntoshoras.domain.timeentry.TimeEntryStatus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fake en memoria del read model del tablero (sin Mockito, ADR-002): agrega en Java con las mismas
 * reglas que el SQL (ventana inclusiva, {@code REJECTED} excluido, alcance, orden). Registra el
 * último alcance recibido para verificar que la aplicación lo resuelve.
 */
public final class InMemoryDashboardReadRepository implements DashboardReadRepository {

  /** Registro sintético con los datos de asunto y abogado ya resueltos. */
  public record Entry(
      UUID lawyerId,
      String lawyerName,
      UUID matterId,
      String matterCode,
      String matterTitle,
      LocalDate workDate,
      int tenths,
      TimeEntryStatus status) {}

  private final List<Entry> entries = new ArrayList<>();
  private DashboardScope lastScope;
  private DateWindow lastWindow;

  public InMemoryDashboardReadRepository with(Entry entry) {
    entries.add(entry);
    return this;
  }

  public DashboardScope lastScope() {
    return lastScope;
  }

  public DateWindow lastWindow() {
    return lastWindow;
  }

  @Override
  public Aggregate aggregate(DateWindow window, DashboardScope scope) {
    lastScope = scope;
    lastWindow = window;
    List<Entry> counted =
        entries.stream()
            .filter(e -> window.contains(e.workDate()))
            .filter(e -> e.status() != TimeEntryStatus.REJECTED)
            .filter(
                e ->
                    !(scope instanceof DashboardScope.Own own)
                        || own.lawyerId().equals(e.lawyerId()))
            .toList();

    Map<UUID, MatterHours> byMatter = new LinkedHashMap<>();
    Map<UUID, LawyerHours> byLawyer = new LinkedHashMap<>();
    for (Entry e : counted) {
      HoursTotals hours = hoursOf(e);
      byMatter.merge(
          e.matterId(),
          new MatterHours(e.matterId(), e.matterCode(), e.matterTitle(), hours),
          (a, b) ->
              new MatterHours(
                  a.matterId(), a.matterCode(), a.matterTitle(), a.hours().plus(b.hours())));
      byLawyer.merge(
          e.lawyerId(),
          new LawyerHours(e.lawyerId(), e.lawyerName(), hours),
          (a, b) -> new LawyerHours(a.lawyerId(), a.lawyerName(), a.hours().plus(b.hours())));
    }
    return new Aggregate(
        byMatter.values().stream().sorted(Comparator.comparing(MatterHours::matterCode)).toList(),
        byLawyer.values().stream().sorted(Comparator.comparing(LawyerHours::lawyerName)).toList());
  }

  private static HoursTotals hoursOf(Entry e) {
    Hours hours = new Hours(e.tenths());
    return e.status() == TimeEntryStatus.APPROVED
        ? new HoursTotals(hours, Hours.ZERO)
        : new HoursTotals(Hours.ZERO, hours);
  }
}
