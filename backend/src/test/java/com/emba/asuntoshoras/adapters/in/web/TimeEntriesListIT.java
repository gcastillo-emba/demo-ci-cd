package com.emba.asuntoshoras.adapters.in.web;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.emba.asuntoshoras.AbstractTimeEntriesIT;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Escenarios de listado: filtros, alcance por rol, paginación, orden y {@code editableUntil}. */
class TimeEntriesListIT extends AbstractTimeEntriesIT {

  // Scenario: Socio filtra pendientes de un abogado
  @Test
  void partnerFiltersPendingOfOneLawyer() throws Exception {
    insertEntry(LAWYER_ID, matterId, "2026-11-05", 10, "PENDING", NOW);
    insertEntry(LAWYER_ID, matterId, "2026-11-04", 10, "APPROVED", NOW);
    insertEntry(LAWYER_ID, matterId, "2026-11-03", 10, "REJECTED", NOW);
    insertEntry(LAWYER_B_ID, matterId, "2026-11-05", 10, "PENDING", NOW);

    mvc.perform(
            auth(get("/api/time-entries"), partner())
                .param("lawyerId", LAWYER_ID)
                .param("status", "PENDING"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[*].status", everyItem(is("PENDING"))))
        .andExpect(jsonPath("$.items[*].lawyerId", everyItem(is(LAWYER_ID))))
        .andExpect(jsonPath("$.totalItems").value(1));
  }

  // Scenario: Socio filtra por asunto y rango de fechas
  @Test
  void partnerFiltersByMatterAndInclusiveRange() throws Exception {
    UUID other = createMatter(clientId, "Otro asunto de prueba");
    for (String date :
        new String[] {"2026-10-01", "2026-10-31", "2026-11-01", "2026-11-03", "2026-11-05"}) {
      insertEntry(LAWYER_ID, matterId, date, 10, "PENDING", NOW);
    }
    insertEntry(LAWYER_B_ID, matterId, "2026-11-07", 10, "APPROVED", NOW);
    insertEntry(LAWYER_ID, other, "2026-11-03", 10, "PENDING", NOW);

    mvc.perform(
            auth(get("/api/time-entries"), partner())
                .param("matterId", matterId.toString())
                .param("from", "2026-11-01")
                .param("to", "2026-11-05"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(3)))
        .andExpect(jsonPath("$.items[*].matterId", everyItem(is(matterId.toString()))))
        .andExpect(jsonPath("$.items[0].workDate").value("2026-11-05"))
        .andExpect(jsonPath("$.items[1].workDate").value("2026-11-03"))
        .andExpect(jsonPath("$.items[2].workDate").value("2026-11-01"));
  }

  // Scenario: Abogado solo ve lo suyo
  @Test
  void lawyerOnlySeesOwnEntries() throws Exception {
    insertEntry(LAWYER_ID, matterId, "2026-11-05", 10, "PENDING", NOW);
    insertEntry(LAWYER_B_ID, matterId, "2026-11-05", 10, "PENDING", NOW);
    insertEntry(LAWYER_B_ID, matterId, "2026-11-04", 10, "APPROVED", NOW);

    mvc.perform(auth(get("/api/time-entries"), lawyer()).param("lawyerId", LAWYER_B_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[*].lawyerId", everyItem(is(LAWYER_ID))))
        .andExpect(jsonPath("$.items[0].lawyerName").value("Abogado Uno"));
  }

  // Scenario: Paginación por defecto
  @Test
  void defaultPagination() throws Exception {
    LocalDate day = LocalDate.parse("2026-11-06");
    for (int i = 0; i < 45; i++) {
      String lawyer = i % 2 == 0 ? LAWYER_ID : LAWYER_B_ID;
      insertEntry(lawyer, matterId, day.minusDays(i).toString(), 10, "PENDING", NOW);
    }

    mvc.perform(auth(get("/api/time-entries"), partner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.items", hasSize(20)))
        .andExpect(jsonPath("$.totalItems").value(45))
        .andExpect(jsonPath("$.totalPages").value(3));
    mvc.perform(auth(get("/api/time-entries"), partner()).param("page", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(5)));
  }

  // Scenario: Tamaño de página excesivo
  @Test
  void oversizedPageIs400() throws Exception {
    expectProblem(
            mvc.perform(auth(get("/api/time-entries"), lawyer()).param("size", "101")),
            400,
            "validation-error")
        .andExpect(jsonPath("$.errors[?(@.field == 'size')]").exists());
    expectProblem(
        mvc.perform(auth(get("/api/time-entries"), lawyer()).param("size", "0")),
        400,
        "validation-error");
    expectProblem(
        mvc.perform(auth(get("/api/time-entries"), lawyer()).param("page", "-1")),
        400,
        "validation-error");
    expectProblem(
            mvc.perform(auth(get("/api/time-entries"), lawyer()).param("status", "ARCHIVED")),
            400,
            "validation-error")
        .andExpect(jsonPath("$.errors[0].field").value("status"));
  }

  // Scenario: Rango de fechas invertido
  @Test
  void invertedRangeIs400ValidationErrorWithField() throws Exception {
    // Revisión C1: la InvalidValueException de dominio sale como validation-error con errors[].
    expectProblem(
            mvc.perform(
                auth(get("/api/time-entries"), partner())
                    .param("from", "2026-11-05")
                    .param("to", "2026-11-01")),
            400,
            "validation-error")
        .andExpect(jsonPath("$.errors[0].field").value("from"))
        .andExpect(
            jsonPath("$.errors[0].message")
                .value("La fecha inicial no puede ser posterior a la final."));
  }

  // Scenario: Orden por defecto
  @Test
  void defaultOrderIsWorkDateDescThenCreatedAtDesc() throws Exception {
    UUID nov3 = insertEntry(LAWYER_ID, matterId, "2026-11-03", 10, "PENDING", NOW);
    UUID nov4Early =
        insertEntry(
            LAWYER_ID,
            matterId,
            "2026-11-04",
            10,
            "PENDING",
            Instant.parse("2026-11-04T10:00:00Z"));
    UUID nov5 = insertEntry(LAWYER_ID, matterId, "2026-11-05", 10, "PENDING", NOW);
    UUID nov4Late =
        insertEntry(
            LAWYER_ID,
            matterId,
            "2026-11-04",
            10,
            "PENDING",
            Instant.parse("2026-11-04T18:00:00Z"));

    mvc.perform(auth(get("/api/time-entries"), lawyer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(nov5.toString()))
        .andExpect(jsonPath("$.items[1].id").value(nov4Late.toString()))
        .andExpect(jsonPath("$.items[2].id").value(nov4Early.toString()))
        .andExpect(jsonPath("$.items[3].id").value(nov3.toString()));
  }

  // Scenario: Orden de la cola de aprobaciones
  @Test
  void approvalQueueOrderIsCreatedAtAsc() throws Exception {
    UUID second =
        insertEntry(
            LAWYER_ID,
            matterId,
            "2026-11-01",
            10,
            "PENDING",
            Instant.parse("2026-11-05T10:00:00Z"));
    UUID third =
        insertEntry(
            LAWYER_B_ID,
            matterId,
            "2026-11-06",
            10,
            "PENDING",
            Instant.parse("2026-11-06T10:00:00Z"));
    UUID first =
        insertEntry(
            LAWYER_ID,
            matterId,
            "2026-11-05",
            10,
            "PENDING",
            Instant.parse("2026-11-04T10:00:00Z"));
    insertEntry(
        LAWYER_ID, matterId, "2026-11-02", 10, "APPROVED", Instant.parse("2026-11-01T10:00:00Z"));

    mvc.perform(
            auth(get("/api/time-entries"), partner())
                .param("status", "PENDING")
                .param("sort", "createdAt,asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(3)))
        .andExpect(jsonPath("$.items[0].id").value(first.toString()))
        .andExpect(jsonPath("$.items[1].id").value(second.toString()))
        .andExpect(jsonPath("$.items[2].id").value(third.toString()));
  }

  @Test
  void explicitDefaultSortIsAccepted() throws Exception {
    insertEntry(LAWYER_ID, matterId, "2026-11-05", 10, "PENDING", NOW);

    mvc.perform(auth(get("/api/time-entries"), lawyer()).param("sort", "workDate,desc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(1)));
  }

  // Scenario: Orden no admitido
  @Test
  void unsupportedSortIs400() throws Exception {
    expectProblem(
            mvc.perform(auth(get("/api/time-entries"), lawyer()).param("sort", "hours,desc")),
            400,
            "validation-error")
        .andExpect(jsonPath("$.errors[0].field").value("sort"));
    expectProblem(
        mvc.perform(auth(get("/api/time-entries"), lawyer()).param("sort", "WORK_DATE_DESC")),
        400,
        "validation-error");
  }

  // Scenario: Fecha límite de edición en la respuesta
  @Test
  void itemsIncludeEditableUntil() throws Exception {
    insertEntry(
        LAWYER_ID, matterId, "2026-10-31", 10, "APPROVED", Instant.parse("2026-10-31T14:00:00Z"));
    // Creado el 2026-11-01T02:30Z = 2026-10-31 22:30 en La Paz: la fecha de creación es el 31.
    insertEntry(
        LAWYER_ID, matterId, "2026-10-30", 10, "PENDING", Instant.parse("2026-11-01T02:30:00Z"));

    mvc.perform(auth(get("/api/time-entries"), lawyer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].editableUntil").value("2026-11-07"))
        .andExpect(jsonPath("$.items[1].editableUntil").value("2026-11-07"));
  }
}
