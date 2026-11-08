package com.emba.asuntoshoras.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.emba.asuntoshoras.AbstractTimeEntriesIT;
import com.emba.asuntoshoras.application.port.out.DashboardReadRepository.Aggregate;
import com.emba.asuntoshoras.domain.dashboard.DashboardScope;
import com.emba.asuntoshoras.domain.dashboard.DateWindow;
import com.emba.asuntoshoras.domain.dashboard.HoursTotals;
import com.emba.asuntoshoras.domain.dashboard.LawyerHours;
import com.emba.asuntoshoras.domain.dashboard.MatterHours;
import com.emba.asuntoshoras.domain.timeentry.Hours;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Read model del tablero contra PostgreSQL real (tareas 2.5 y 2.7): sumas condicionales en décimas,
 * exclusión de {@code REJECTED}, ventana inclusiva, alcance, orden y plan de consulta.
 */
class DashboardReadAdapterIT extends AbstractTimeEntriesIT {

  private static final DateWindow NOVEMBER =
      DateWindow.of(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-07"));

  @Autowired private DashboardReadAdapter adapter;
  @Autowired private NamedParameterJdbcTemplate namedJdbc;
  @Autowired private TransactionTemplate transactions;

  private static HoursTotals h(int approvedTenths, int pendingTenths) {
    return new HoursTotals(new Hours(approvedTenths), new Hours(pendingTenths));
  }

  @Test
  void aggregatesCountedTenthsPerMatterAndLawyer() {
    UUID b = insertMatter(clientId, "AS-2026-0020", "Asunto B sintético", false);
    UUID a = insertMatter(clientId, "AS-2026-0010", "Asunto A sintético", true);
    insertEntry(LAWYER_ID, a, "2026-11-01", 20, "APPROVED", NOW);
    insertEntry(LAWYER_ID, a, "2026-11-07", 15, "APPROVED", NOW);
    insertEntry(LAWYER_B_ID, a, "2026-11-03", 5, "PENDING", NOW);
    insertEntry(LAWYER_B_ID, b, "2026-11-04", 40, "PENDING", NOW);
    insertEntry(LAWYER_B_ID, b, "2026-11-04", 80, "REJECTED", NOW);
    insertEntry(LAWYER_ID, b, "2026-10-31", 99, "APPROVED", NOW); // fuera
    insertEntry(LAWYER_ID, b, "2026-11-08", 99, "APPROVED", NOW); // fuera

    Aggregate all = adapter.aggregate(NOVEMBER, DashboardScope.all());

    assertThat(all.byMatter())
        .containsExactly(
            new MatterHours(a, "AS-2026-0010", "Asunto A sintético", h(35, 5)),
            new MatterHours(b, "AS-2026-0020", "Asunto B sintético", h(0, 40)));
    assertThat(all.byLawyer())
        .containsExactly(
            new LawyerHours(UUID.fromString(LAWYER_B_ID), "Abogada Dos", h(0, 45)),
            new LawyerHours(UUID.fromString(LAWYER_ID), "Abogado Uno", h(35, 0)));

    Aggregate own = adapter.aggregate(NOVEMBER, DashboardScope.own(UUID.fromString(LAWYER_ID)));

    assertThat(own.byMatter())
        .containsExactly(new MatterHours(a, "AS-2026-0010", "Asunto A sintético", h(35, 0)));
    assertThat(own.byLawyer())
        .containsExactly(new LawyerHours(UUID.fromString(LAWYER_ID), "Abogado Uno", h(35, 0)));
  }

  @Test
  void onlyRejectedOrOutsideGivesEmptyAggregate() {
    UUID a = insertMatter(clientId, "AS-2026-0030", "Asunto C sintético", false);
    insertEntry(LAWYER_ID, a, "2026-11-02", 30, "REJECTED", NOW);

    Aggregate all = adapter.aggregate(NOVEMBER, DashboardScope.all());

    assertThat(all.byMatter()).isEmpty();
    assertThat(all.byLawyer()).isEmpty();
  }

  /** Tarea 2.7: con escaneo secuencial desalentado, ninguna consulta recorre toda la tabla. */
  @Test
  void queriesUseAnIndexOnTimeEntries() {
    UUID a = insertMatter(clientId, "AS-2026-0040", "Asunto D sintético", false);
    for (int i = 1; i <= 28; i++) {
      insertEntry(LAWYER_ID, a, "2026-10-" + String.format("%02d", i), 10, "APPROVED", NOW);
    }
    jdbc.execute("ANALYZE time_entries");
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("from", Date.valueOf(NOVEMBER.from()))
            .addValue("to", Date.valueOf(NOVEMBER.to()))
            .addValue("lawyerId", UUID.fromString(LAWYER_ID));

    for (String sql :
        List.of(
            DashboardReadAdapter.BY_MATTER_ALL,
            DashboardReadAdapter.BY_LAWYER_ALL,
            DashboardReadAdapter.BY_MATTER_OWN,
            DashboardReadAdapter.BY_LAWYER_OWN)) {
      String plan = explain(sql, params);
      assertThat(plan).as(sql).doesNotContain("Seq Scan on time_entries");
      assertThat(plan)
          .as(sql)
          .containsPattern("ix_time_entries_(work_date_status|lawyer_work_date)");
    }
    assertThat(explain(DashboardReadAdapter.BY_MATTER_ALL, params))
        .contains("ix_time_entries_work_date_status");
  }

  private String explain(String sql, MapSqlParameterSource params) {
    return transactions.execute(
        status -> {
          jdbc.execute("SET LOCAL enable_seqscan = off");
          return String.join("\n", namedJdbc.queryForList("EXPLAIN " + sql, params, String.class));
        });
  }
}
