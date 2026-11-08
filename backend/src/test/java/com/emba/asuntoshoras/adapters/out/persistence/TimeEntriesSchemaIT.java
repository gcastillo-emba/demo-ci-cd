package com.emba.asuntoshoras.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emba.asuntoshoras.AbstractClientsMattersIT;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** Migración V3 (tarea 2.1) y seed de demo idempotente de registros de horas (tarea 2.11). */
class TimeEntriesSchemaIT extends AbstractClientsMattersIT {

  private static final Timestamp NOW = Timestamp.from(Instant.parse("2026-11-07T14:00:00Z"));
  private static final UUID LAWYER = UUID.fromString("00000000-0000-4000-8000-000000000002");

  @Autowired private DataSource dataSource;

  private UUID matterId;

  @BeforeEach
  void createMatter() {
    UUID clientId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO clients (id, name, created_at) VALUES (?, 'Cliente de prueba', ?)",
        clientId,
        NOW);
    matterId = insertMatter(clientId, "AS-2026-0001", "Asunto de prueba", false);
  }

  private void insert(int tenths, String status, String reason) {
    insert(matterId, LAWYER, tenths, status, reason);
  }

  private void insert(UUID matter, UUID lawyer, int tenths, String status, String reason) {
    jdbc.update(
        "INSERT INTO time_entries (id, matter_id, lawyer_id, work_date, hours_tenths, description,"
            + " status, rejection_reason, created_at) VALUES (?, ?, ?, ?, ?, 'd', ?, ?, ?)",
        UUID.randomUUID(),
        matter,
        lawyer,
        Date.valueOf(LocalDate.parse("2026-11-05")),
        tenths,
        status,
        reason,
        NOW);
  }

  @Test
  void flywayAppliedV3() {
    assertThat(
            jdbc.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '3'", Boolean.class))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE indexname = 'ix_time_entries_lawyer_work_date'",
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  void validRowsAreAcceptedWithVersionZero() {
    insert(1, "PENDING", null);
    insert(240, "APPROVED", null);
    insert(10, "REJECTED", "Motivo");

    assertThat(jdbc.queryForObject("SELECT count(*) FROM time_entries", Integer.class))
        .isEqualTo(3);
    assertThat(jdbc.queryForObject("SELECT max(version) FROM time_entries", Long.class)).isZero();
  }

  @Test
  void hoursMustBeBetweenOneAnd240Tenths() {
    assertThatThrownBy(() -> insert(0, "PENDING", null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_time_entries_hours");
    assertThatThrownBy(() -> insert(241, "PENDING", null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_time_entries_hours");
  }

  @Test
  void statusMustBeKnownAndRejectionReasonConsistent() {
    assertThatThrownBy(() -> insert(10, "DRAFT", null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_time_entries_status");
    assertThatThrownBy(() -> insert(10, "REJECTED", null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_time_entries_rejection_reason");
    assertThatThrownBy(() -> insert(10, "APPROVED", "x"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_time_entries_rejection_reason");
  }

  @Test
  void matterAndLawyerMustExist() {
    assertThatThrownBy(() -> insert(UUID.randomUUID(), LAWYER, 10, "PENDING", null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("fk_time_entries_matter");
    assertThatThrownBy(() -> insert(matterId, UUID.randomUUID(), 10, "PENDING", null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("fk_time_entries_lawyer");
  }

  @Test
  void demoSeedIsIdempotentAndRespectsTheBusinessRules() {
    jdbc.update("DELETE FROM time_entries");
    jdbc.update("DELETE FROM matters");
    ResourceDatabasePopulator seed =
        new ResourceDatabasePopulator(
            new ClassPathResource("db/seed/R__seed_clients_matters.sql"),
            new ClassPathResource("db/seed/R__seed_time_entries.sql"));

    seed.execute(dataSource);
    seed.execute(dataSource);

    assertThat(jdbc.queryForObject("SELECT count(*) FROM time_entries", Integer.class))
        .isEqualTo(13);
    for (String status : new String[] {"PENDING", "APPROVED", "REJECTED"}) {
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM time_entries WHERE status = ?", Integer.class, status))
          .as(status)
          .isPositive();
    }
    // Nunca fechas futuras (hoy en La Paz) ni más de 24 h por abogado y día.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM time_entries"
                    + " WHERE work_date >= (now() AT TIME ZONE 'America/La_Paz')::date",
                Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT coalesce(max(total), 0) FROM (SELECT sum(hours_tenths) AS total"
                    + " FROM time_entries WHERE status IN ('PENDING', 'APPROVED')"
                    + " GROUP BY lawyer_id, work_date) t",
                Integer.class))
        .isLessThanOrEqualTo(240);
  }
}
