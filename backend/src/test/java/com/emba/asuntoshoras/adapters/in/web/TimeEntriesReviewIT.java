package com.emba.asuntoshoras.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.emba.asuntoshoras.AbstractTimeEntriesIT;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Escenarios de aprobación, rechazo y estados terminales, más la interacción con C1. */
class TimeEntriesReviewIT extends AbstractTimeEntriesIT {

  // Scenario: Socio aprueba un registro pendiente
  @Test
  void partnerApprovesPendingEntry() throws Exception {
    UUID id = insertPending("2026-11-05", 20);

    approve(partner(), id)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.id").value(id.toString()))
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.rejectionReason").isEmpty());
    assertThat(statusOf(id)).isEqualTo("APPROVED");
  }

  // Scenario: Aprobar un registro ya aprobado
  @Test
  void approvingApprovedIs409() throws Exception {
    UUID id = insertEntry(LAWYER_ID, matterId, "2026-11-05", 20, "APPROVED", NOW);

    expectProblem(approve(partner(), id), 409, "entry-not-pending");
  }

  // Scenario: Abogado intenta aprobar
  @Test
  void lawyerCannotApprove() throws Exception {
    UUID id = insertPending("2026-11-05", 20);

    expectProblem(approve(lawyer(), id), 403, "forbidden");
    assertThat(statusOf(id)).isEqualTo("PENDING");
  }

  // Scenario: Aprobar un registro inexistente
  @Test
  void approvingUnknownIs404() throws Exception {
    expectProblem(approve(partner(), UUID.randomUUID()), 404, "not-found");
  }

  // Scenario: Socio rechaza con motivo
  @Test
  void partnerRejectsWithReason() throws Exception {
    UUID id = insertPending("2026-11-05", 20);

    reject(partner(), id, "  Descripción insuficiente ")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"))
        .andExpect(jsonPath("$.rejectionReason").value("Descripción insuficiente"));
    assertThat(row(id).get("rejection_reason")).isEqualTo("Descripción insuficiente");
  }

  // Scenario: Rechazo sin motivo
  @Test
  void rejectionWithoutReasonIs422() throws Exception {
    UUID id = insertPending("2026-11-05", 20);

    expectProblem(reject(partner(), id, null), 422, "rejection-reason-required")
        .andExpect(
            jsonPath("$.detail").value("Indica un motivo de rechazo de 1 a 500 caracteres."));
    expectProblem(reject(partner(), id, ""), 422, "rejection-reason-required");
    expectProblem(reject(partner(), id, "   "), 422, "rejection-reason-required");
    assertThat(statusOf(id)).isEqualTo("PENDING");
  }

  // Scenario: Motivo de 501 caracteres
  @Test
  void reasonOf501Is422() throws Exception {
    UUID id = insertPending("2026-11-05", 20);

    expectProblem(reject(partner(), id, "r".repeat(501)), 422, "rejection-reason-required");
    reject(partner(), id, "r".repeat(500)).andExpect(status().isOk());
  }

  @Test
  void rejectWithoutBodyIs400() throws Exception {
    UUID id = insertPending("2026-11-05", 20);

    // Cuerpo JSON ausente → 400 (sin Content-Type la respuesta es 415 unsupported-media-type).
    expectProblem(
        mvc.perform(
            auth(post("/api/time-entries/{id}/reject", id), partner())
                .contentType(MediaType.APPLICATION_JSON)),
        400,
        "validation-error");
    expectProblem(
        mvc.perform(auth(post("/api/time-entries/{id}/reject", id), partner())),
        415,
        "unsupported-media-type");
    assertThat(statusOf(id)).isEqualTo("PENDING");
  }

  // Scenario: Rechazar un registro ya rechazado
  @Test
  void rejectingRejectedIs409() throws Exception {
    UUID id = insertEntry(LAWYER_ID, matterId, "2026-11-05", 20, "REJECTED", NOW);

    expectProblem(reject(partner(), id, "Otro motivo"), 409, "entry-not-pending");
    assertThat(row(id).get("rejection_reason")).isEqualTo("Motivo previo");
  }

  // Scenario: Abogado intenta rechazar
  @Test
  void lawyerCannotReject() throws Exception {
    UUID id = insertPending("2026-11-05", 20);

    expectProblem(reject(lawyer(), id, "Motivo válido"), 403, "forbidden");
    assertThat(statusOf(id)).isEqualTo("PENDING");
  }

  // Scenario: Rechazar un registro aprobado
  @Test
  void rejectingApprovedIs409AndStaysApproved() throws Exception {
    UUID id = insertEntry(LAWYER_ID, matterId, "2026-11-05", 20, "APPROVED", NOW);

    expectProblem(reject(partner(), id, "Motivo válido"), 409, "entry-not-pending");
    assertThat(statusOf(id)).isEqualTo("APPROVED");
  }

  // Scenario: Aprobar un registro rechazado
  @Test
  void approvingRejectedIs409AndStaysRejected() throws Exception {
    UUID id = insertEntry(LAWYER_ID, matterId, "2026-11-05", 20, "REJECTED", NOW);

    expectProblem(approve(partner(), id), 409, "entry-not-pending");
    assertThat(statusOf(id)).isEqualTo("REJECTED");
  }

  @Test
  void rejectionBeforeStateIsEvaluatedAfterState() throws Exception {
    UUID id = insertEntry(LAWYER_ID, matterId, "2026-11-05", 20, "APPROVED", NOW);

    // 409 entry-not-pending precede a 422 rejection-reason-required (design D5).
    expectProblem(reject(partner(), id, null), 409, "entry-not-pending");
  }

  /** Tarea 2.12 (design Supuesto 13): cerrar un asunto no altera sus registros pendientes. */
  @Test
  void closingAMatterKeepsPendingEntriesReviewable() throws Exception {
    UUID first = insertPending("2026-11-05", 20);
    UUID second = insertPending("2026-11-04", 20);

    mvc.perform(auth(post("/api/matters/{id}/close", matterId), partner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CLOSED"));
    assertThat(statusOf(first)).isEqualTo("PENDING");
    assertThat(statusOf(second)).isEqualTo("PENDING");

    approve(partner(), first).andExpect(status().isOk());
    reject(partner(), second, "Asunto cerrado").andExpect(status().isOk());
    assertThat(statusOf(first)).isEqualTo("APPROVED");
    assertThat(statusOf(second)).isEqualTo("REJECTED");
  }
}
