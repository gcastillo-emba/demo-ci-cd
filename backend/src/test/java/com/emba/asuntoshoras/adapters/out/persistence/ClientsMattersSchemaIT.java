package com.emba.asuntoshoras.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emba.asuntoshoras.AbstractClientsMattersIT;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** Migración V2 (tarea 2.1) y seed de demo idempotente (tarea 2.8). */
class ClientsMattersSchemaIT extends AbstractClientsMattersIT {

  private static final Timestamp NOW = Timestamp.from(Instant.parse("2026-11-07T14:00:00Z"));

  @Autowired private DataSource dataSource;

  private void insertClient(UUID id, String taxId) {
    jdbc.update(
        "INSERT INTO clients (id, name, tax_id, created_at) VALUES (?, ?, ?, ?)",
        id,
        "Cliente " + id,
        taxId,
        NOW);
  }

  @Test
  void flywayAppliedV2() {
    assertThat(
            jdbc.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '2'", Boolean.class))
        .isTrue();
  }

  @Test
  void partialUniqueIndexAllowsSeveralNullTaxIdsButNotDuplicates() {
    insertClient(UUID.randomUUID(), null);
    insertClient(UUID.randomUUID(), null);
    insertClient(UUID.randomUUID(), "0000000001");

    assertThat(countClients()).isEqualTo(3);
    assertThatThrownBy(() -> insertClient(UUID.randomUUID(), "0000000001"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ux_clients_tax_id");
  }

  @Test
  void statusAndClosedAtMustBeConsistent() {
    UUID clientId = UUID.randomUUID();
    insertClient(clientId, null);

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO matters (id, code, client_id, title, status, created_at)"
                        + " VALUES (?, 'AS-2026-0001', ?, 'T', 'CLOSED', ?)",
                    UUID.randomUUID(),
                    clientId,
                    NOW))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO matters (id, code, client_id, title, status, created_at)"
                        + " VALUES (?, 'AS-2026-0002', ?, 'T', 'ARCHIVED', ?)",
                    UUID.randomUUID(),
                    clientId,
                    NOW))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void matterRequiresExistingClientAndUniqueCode() {
    UUID clientId = UUID.randomUUID();
    insertClient(clientId, null);
    insertMatter(clientId, "AS-2026-0001", "Uno", false);

    assertThatThrownBy(() -> insertMatter(clientId, "AS-2026-0001", "Duplicado", false))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insertMatter(UUID.randomUUID(), "AS-2026-0002", "Sin cliente", false))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void demoSeedIsIdempotentAndAlignsTheSequence() {
    ResourceDatabasePopulator seed =
        new ResourceDatabasePopulator(new ClassPathResource("db/seed/R__seed_clients_matters.sql"));

    seed.execute(dataSource);
    seed.execute(dataSource);

    assertThat(countClients()).isEqualTo(25);
    assertThat(countMatters()).isEqualTo(30);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM matters WHERE status = 'CLOSED'", Integer.class))
        .isPositive()
        .isLessThan(30);
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM clients WHERE tax_id IS NULL", Integer.class))
        .isGreaterThan(1);
    // Mismo "ahora" que usa el seed: el de la base, en la zona de la firma.
    int year =
        jdbc.queryForObject(
            "SELECT extract(year FROM now() AT TIME ZONE 'America/La_Paz')::int", Integer.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT last_value FROM matter_code_seq WHERE year = ?", Integer.class, year))
        .isEqualTo(30);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM clients WHERE tax_id IS NOT NULL AND tax_id NOT LIKE 'DEMO-%'",
                Integer.class))
        .isZero();
  }
}
