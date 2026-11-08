package com.emba.asuntoshoras.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.emba.asuntoshoras.AbstractTimeEntriesIT;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Tarea 2.13: precedencia 401 > 403 > 400 > 404 > 409 > 422 (design D5) con las reglas por URL de
 * {@code SecurityFilterChain}, y 405 para métodos no declarados.
 */
class TimeEntriesPrecedenceIT extends AbstractTimeEntriesIT {

  private static final Map<String, Object> INVALID = Map.of("description", " ");

  @Test
  void unauthenticatedWithInvalidBodyIs401() throws Exception {
    expectProblem(
        mvc.perform(
            post("/api/time-entries").contentType(MediaType.APPLICATION_JSON).content("{}")),
        401,
        "unauthenticated");
  }

  @Test
  void partnerWithInvalidBodyOnCreateIs403NotValidation() throws Exception {
    expectProblem(create(partner(), INVALID), 403, "forbidden");
  }

  @Test
  void partnerWithInvalidBodyOnEditIs403NotValidation() throws Exception {
    UUID id = insertPending("2026-11-05", 20);

    expectProblem(update(partner(), id, INVALID), 403, "forbidden");
    expectProblem(update(partner(), UUID.randomUUID(), INVALID), 403, "forbidden");
  }

  @Test
  void lawyerApprovingUnknownEntryIs403NotNotFound() throws Exception {
    expectProblem(approve(lawyer(), UUID.randomUUID()), 403, "forbidden");
  }

  @Test
  void lawyerRejectingWithoutReasonIs403NotUnprocessable() throws Exception {
    UUID id = insertPending("2026-11-05", 20);

    expectProblem(reject(lawyer(), id, null), 403, "forbidden");
    expectProblem(
        mvc.perform(auth(post("/api/time-entries/{id}/reject", id), lawyer())), 403, "forbidden");
  }

  @Test
  void otherLawyerWithInvalidBodyIs400NotNotEntryOwner() throws Exception {
    UUID id = insertPending("2026-11-05", 20);

    expectProblem(update(lawyerB(), id, INVALID), 400, "validation-error")
        .andExpect(jsonPath("$.errors[?(@.field == 'description')]").exists());
  }

  @Test
  void unknownEntryOfAnotherLawyerIs404NotNotEntryOwner() throws Exception {
    expectProblem(
        update(lawyerB(), UUID.randomUUID(), withVersion(body("2026-11-05", "2"), 0)),
        404,
        "not-found");
  }

  @Test
  void notEntryOwnerPrecedesEntryNotPending() throws Exception {
    UUID approved = insertEntry(LAWYER_ID, matterId, "2026-11-05", 20, "APPROVED", NOW);

    expectProblem(
        update(lawyerB(), approved, withVersion(body("2026-11-05", "2"), 0)),
        403,
        "not-entry-owner");
  }

  @Test
  void malformedIdIs400() throws Exception {
    expectProblem(
            mvc.perform(
                auth(put("/api/time-entries/{id}", "no-es-uuid"), lawyer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(withVersion(body("2026-11-05", "2"), 0)))),
            400,
            "validation-error")
        .andExpect(jsonPath("$.errors[0].field").value("id"));
    expectProblem(
        mvc.perform(auth(post("/api/time-entries/{id}/approve", "no-es-uuid"), partner())),
        400,
        "validation-error");
  }

  @Test
  void undeclaredMethodsAre405ForAnyRole() throws Exception {
    UUID id = insertPending("2026-11-05", 20);

    for (String token : new String[] {lawyer(), partner()}) {
      expectProblem(
          mvc.perform(auth(delete("/api/time-entries/{id}", id), token)),
          405,
          "method-not-allowed");
      expectProblem(
          mvc.perform(
              auth(patch("/api/time-entries/{id}", id), token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{}")),
          405,
          "method-not-allowed");
      expectProblem(
          mvc.perform(auth(get("/api/time-entries/{id}", id), token)), 405, "method-not-allowed");
    }
    assertThat(countEntries()).isEqualTo(1);
  }
}
