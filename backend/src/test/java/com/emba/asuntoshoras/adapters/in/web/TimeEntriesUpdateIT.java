package com.emba.asuntoshoras.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.emba.asuntoshoras.AbstractTimeEntriesIT;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Escenarios de edición de {@code specs/time-entries/spec.md}: propiedad, estado, ventana de 7
 * días, reglas de negocio, concurrencia optimista y campos controlados por el servidor.
 */
class TimeEntriesUpdateIT extends AbstractTimeEntriesIT {

  private Map<String, Object> edit(String workDate, Object hours, long version) {
    return withVersion(body(workDate, hours), version);
  }

  private int tenthsOf(UUID id) {
    return ((Number) row(id).get("hours_tenths")).intValue();
  }

  // Scenario: Socio intenta editar un registro
  @Test
  void partnerCannotEdit() throws Exception {
    UUID id = insertPending("2026-11-05", 20);

    expectProblem(update(partner(), id, edit("2026-11-05", "3", 0)), 403, "forbidden");
    assertThat(tenthsOf(id)).isEqualTo(20);
  }

  // Scenario: Edición válida
  @Test
  void authorEditsPendingEntry() throws Exception {
    UUID id =
        insertEntry(
            LAWYER_ID,
            matterId,
            "2026-11-05",
            20,
            "PENDING",
            Instant.parse("2026-11-05T14:00:00Z"));
    Map<String, Object> body = withVersion(body(matterId, "2026-11-05", "3.5", " Nueva "), 0);

    update(lawyer(), id, body)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hours").value(3.5))
        .andExpect(jsonPath("$.description").value("Nueva"))
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.editableUntil").value("2026-11-12"));
    assertThat(tenthsOf(id)).isEqualTo(35);
  }

  // Scenario: Abogado edita un registro ajeno
  @Test
  void otherLawyerGets403NotEntryOwner() throws Exception {
    UUID id = insertPending("2026-11-05", 20);

    expectProblem(update(lawyerB(), id, edit("2026-11-05", "3", 0)), 403, "not-entry-owner")
        .andExpect(
            jsonPath("$.detail").value("Solo el autor puede editar este registro de horas."));
    assertThat(tenthsOf(id)).isEqualTo(20);
  }

  // Scenario: Registro inexistente
  @Test
  void unknownEntryIs404() throws Exception {
    expectProblem(
        update(lawyer(), UUID.randomUUID(), edit("2026-11-05", "3", 0)), 404, "not-found");
  }

  // Scenario: Editar un registro aprobado
  @Test
  void approvedEntryIs409() throws Exception {
    UUID id = insertEntry(LAWYER_ID, matterId, "2026-11-05", 20, "APPROVED", NOW);

    expectProblem(update(lawyer(), id, edit("2026-11-05", "3", 0)), 409, "entry-not-pending")
        .andExpect(
            jsonPath("$.detail").value("El registro ya fue aprobado o rechazado; no se modificó."));
    assertThat(tenthsOf(id)).isEqualTo(20);
  }

  // Scenario: Editar un registro rechazado
  @Test
  void rejectedEntryIs409() throws Exception {
    UUID id = insertEntry(LAWYER_ID, matterId, "2026-11-05", 20, "REJECTED", NOW);

    expectProblem(update(lawyer(), id, edit("2026-11-05", "3", 0)), 409, "entry-not-pending");
  }

  // Scenario: Último día de la ventana
  @Test
  void lastDayOfTheWindowIsEditable() throws Exception {
    UUID id =
        insertEntry(
            LAWYER_ID,
            matterId,
            "2026-10-31",
            20,
            "PENDING",
            Instant.parse("2026-10-31T14:00:00Z"));

    update(lawyer(), id, edit("2026-10-31", "3", 0)).andExpect(status().isOk());
  }

  // Scenario: Ventana vencida
  @Test
  void expiredWindowIs422() throws Exception {
    UUID id =
        insertEntry(
            LAWYER_ID,
            matterId,
            "2026-10-30",
            20,
            "PENDING",
            Instant.parse("2026-10-30T14:00:00Z"));

    expectProblem(update(lawyer(), id, edit("2026-10-30", "3", 0)), 422, "edit-window-expired")
        .andExpect(
            jsonPath("$.detail")
                .value("El registro solo podía editarse hasta 7 días después de su creación."));
    assertThat(tenthsOf(id)).isEqualTo(20);
  }

  // Scenario: Las horas previas del propio registro no cuentan
  @Test
  void ownPreviousHoursDoNotCount() throws Exception {
    UUID id = insertPending("2026-11-05", 80);
    insertEntry(LAWYER_ID, matterId, "2026-11-05", 160, "PENDING", NOW.minusSeconds(60));

    update(lawyer(), id, edit("2026-11-05", "8", 0)).andExpect(status().isOk());
  }

  // Scenario: La edición supera el límite diario
  @Test
  void editExceedingTheDailyLimit() throws Exception {
    UUID id = insertPending("2026-11-05", 80);
    insertEntry(LAWYER_ID, matterId, "2026-11-05", 100, "PENDING", NOW.minusSeconds(60));
    insertEntry(LAWYER_ID, matterId, "2026-11-05", 60, "APPROVED", NOW.minusSeconds(60));

    expectProblem(update(lawyer(), id, edit("2026-11-05", "8.1", 0)), 422, "daily-hours-exceeded")
        .andExpect(jsonPath("$.alreadyLoggedHours").value(16))
        .andExpect(jsonPath("$.limitHours").value(24));
    assertThat(tenthsOf(id)).isEqualTo(80);
  }

  // Scenario: Mover el registro a otro día lleno
  @Test
  void movingToAFullDay() throws Exception {
    UUID id = insertPending("2026-11-05", 50);
    insertEntry(LAWYER_ID, matterId, "2026-11-04", 200, "APPROVED", NOW.minusSeconds(60));

    expectProblem(update(lawyer(), id, edit("2026-11-04", "5", 0)), 422, "daily-hours-exceeded")
        .andExpect(jsonPath("$.alreadyLoggedHours").value(20));
  }

  // Scenario: Edición con fecha futura
  @Test
  void editToFutureDate() throws Exception {
    UUID id = insertPending("2026-11-05", 20);

    expectProblem(update(lawyer(), id, edit("2026-11-08", "2", 0)), 422, "future-date");
  }

  // Scenario: Edición hacia un asunto cerrado
  @Test
  void editToClosedMatter() throws Exception {
    UUID id = insertPending("2026-11-05", 20);
    UUID closed = closeMatter(createMatter(clientId, "Asunto que se cierra"));

    expectProblem(
        update(lawyer(), id, withVersion(body(closed, "2026-11-05", "2", DESCRIPTION), 0)),
        422,
        "matter-closed");
    assertThat(row(id).get("matter_id")).isEqualTo(matterId);
  }

  // Scenario: El asunto original se cerró después de la carga
  @Test
  void originalMatterClosedAfterTheEntry() throws Exception {
    UUID id = insertPending("2026-11-05", 20);
    closeMatter(matterId);

    expectProblem(update(lawyer(), id, edit("2026-11-05", "3", 0)), 422, "matter-closed");
    assertThat(tenthsOf(id)).isEqualTo(20);
    assertThat(((Number) row(id).get("version")).longValue()).isZero();
  }

  // Scenario: Edición con incremento inválido
  @Test
  void editWithInvalidIncrement() throws Exception {
    UUID id = insertPending("2026-11-05", 20);

    expectProblem(
        update(lawyer(), id, edit("2026-11-05", "2.05", 0)), 422, "hours-invalid-increment");
  }

  // Scenario: Edición fuera de rango
  @Test
  void editOutOfRange() throws Exception {
    UUID id = insertPending("2026-11-05", 20);

    expectProblem(update(lawyer(), id, edit("2026-11-05", "0", 0)), 422, "hours-out-of-range");
  }

  // Scenario: Versión obsoleta
  @Test
  void staleVersionIs409WithoutChanges() throws Exception {
    UUID id = insertPending("2026-11-05", 20);
    jdbc.update("UPDATE time_entries SET version = 1 WHERE id = ?", id);

    expectProblem(update(lawyer(), id, edit("2026-11-05", "9", 0)), 409, "concurrent-modification")
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "El registro fue modificado por otra operación. Recarga y vuelve a intentarlo."));
    assertThat(tenthsOf(id)).isEqualTo(20);
  }

  @Test
  void secondEditWithTheSameVersionIs409() throws Exception {
    UUID id = insertPending("2026-11-05", 20);

    update(lawyer(), id, edit("2026-11-05", "3", 0))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));
    expectProblem(update(lawyer(), id, edit("2026-11-05", "4", 0)), 409, "concurrent-modification");
    update(lawyer(), id, edit("2026-11-05", "4", 1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2));
  }

  // Scenario: Versión ausente
  @Test
  void missingVersionIs400() throws Exception {
    UUID id = insertPending("2026-11-05", 20);

    expectProblem(update(lawyer(), id, body("2026-11-05", "3")), 400, "validation-error")
        .andExpect(jsonPath("$.errors[?(@.field == 'version')]").exists());
  }

  // Scenario: Edición con campos de solo lectura
  @Test
  void readOnlyFieldsAreIgnoredOnEdit() throws Exception {
    Instant created = Instant.parse("2026-11-05T14:00:00Z");
    UUID id = insertEntry(LAWYER_ID, matterId, "2026-11-05", 20, "PENDING", created);
    Map<String, Object> body = edit("2026-11-05", "2.5", 0);
    body.put("lawyerId", LAWYER_B_ID);
    body.put("status", "APPROVED");
    body.put("rejectionReason", "x");
    body.put("createdAt", "2026-01-01T00:00:00Z");
    body.put("editableUntil", "2030-01-01");
    body.put("id", UUID.randomUUID().toString());

    update(lawyer(), id, body)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id.toString()))
        .andExpect(jsonPath("$.lawyerId").value(LAWYER_ID))
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.rejectionReason").isEmpty())
        .andExpect(jsonPath("$.createdAt").value("2026-11-05T14:00:00Z"))
        .andExpect(jsonPath("$.editableUntil").value("2026-11-12"));
    Map<String, Object> stored = row(id);
    assertThat(stored.get("lawyer_id")).hasToString(LAWYER_ID);
    assertThat(stored.get("status")).isEqualTo("PENDING");
    assertThat(stored.get("rejection_reason")).isNull();
  }
}
