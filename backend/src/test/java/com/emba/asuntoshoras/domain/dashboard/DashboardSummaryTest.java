package com.emba.asuntoshoras.domain.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emba.asuntoshoras.domain.shared.DomainValidationException;
import com.emba.asuntoshoras.domain.timeentry.Hours;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Totales derivados de las filas y valores del resumen (design D4). */
class DashboardSummaryTest {

  private static final DateWindow WINDOW =
      DateWindow.of(LocalDate.parse("2026-10-09"), LocalDate.parse("2026-11-07"));

  private static HoursTotals h(int approvedTenths, int pendingTenths) {
    return new HoursTotals(new Hours(approvedTenths), new Hours(pendingTenths));
  }

  // Scenario: Totales consistentes con las filas
  @Test
  void totalsAreTheSumOfMatterRows() {
    UUID ana = UUID.randomUUID();
    List<MatterHours> byMatter =
        List.of(
            new MatterHours(UUID.randomUUID(), "AS-2026-0001", "Asunto A", h(35, 5)),
            new MatterHours(UUID.randomUUID(), "AS-2026-0002", "Asunto B", h(0, 40)));
    List<LawyerHours> byLawyer = List.of(new LawyerHours(ana, "Ana Pérez", h(35, 45)));

    DashboardSummary summary = DashboardSummary.of(WINDOW, byMatter, byLawyer);

    assertThat(summary.totals()).isEqualTo(h(35, 45));
    assertThat(DashboardSummary.sumOfLawyers(summary.byLawyer())).isEqualTo(summary.totals());
    assertThat(summary.window()).isEqualTo(WINDOW);
  }

  // Scenario: Ventana sin registros para el socio
  @Test
  void emptyRowsGiveZeroTotals() {
    DashboardSummary summary = DashboardSummary.of(WINDOW, List.of(), List.of());

    assertThat(summary.totals()).isEqualTo(HoursTotals.ZERO);
    assertThat(summary.totals().isZero()).isTrue();
    assertThat(summary.byMatter()).isEmpty();
    assertThat(summary.byLawyer()).isEmpty();
  }

  @Test
  void hoursTotalsAddAndDetectZero() {
    assertThat(h(10, 0).plus(h(5, 7))).isEqualTo(h(15, 7));
    assertThat(h(0, 1).isZero()).isFalse();
    assertThat(h(1, 0).isZero()).isFalse();
    assertThatThrownBy(() -> new HoursTotals(null, Hours.ZERO))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new HoursTotals(Hours.ZERO, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void emptyLawyerRowHasZeroHours() {
    UUID id = UUID.randomUUID();
    LawyerHours row = LawyerHours.empty(id, "Ana Pérez");
    assertThat(row.hours()).isEqualTo(HoursTotals.ZERO);
    assertThat(row.lawyerId()).isEqualTo(id);
  }

  @Test
  void rowsRequireTheirFields() {
    UUID id = UUID.randomUUID();
    assertThatThrownBy(() -> new MatterHours(null, "c", "t", HoursTotals.ZERO))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new MatterHours(id, null, "t", HoursTotals.ZERO))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new MatterHours(id, "c", null, HoursTotals.ZERO))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new MatterHours(id, "c", "t", null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new LawyerHours(null, "n", HoursTotals.ZERO))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new LawyerHours(id, null, HoursTotals.ZERO))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new LawyerHours(id, "n", null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new DashboardSummary(null, List.of(), List.of(), HoursTotals.ZERO))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new DashboardSummary(WINDOW, List.of(), List.of(), null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void scopeFactories() {
    UUID id = UUID.randomUUID();
    assertThat(DashboardScope.all()).isSameAs(DashboardScope.All.INSTANCE);
    assertThat(DashboardScope.own(id)).isEqualTo(new DashboardScope.Own(id));
    assertThatThrownBy(() -> DashboardScope.own(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void validationExceptionRequiresViolations() {
    assertThatThrownBy(() -> new DomainValidationException(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new DomainValidationException(null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new DomainValidationException.Violation(null, "m"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new DomainValidationException.Violation("f", null))
        .isInstanceOf(NullPointerException.class);
  }
}
