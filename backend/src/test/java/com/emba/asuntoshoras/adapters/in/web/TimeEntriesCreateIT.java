package com.emba.asuntoshoras.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.emba.asuntoshoras.AbstractTimeEntriesIT;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Escenarios de alta, autenticación, formato, horas, límite diario, fecha y asunto cerrado de
 * {@code specs/time-entries/spec.md} (HTTP → PostgreSQL real). Hoy es 2026-11-07 en La Paz.
 */
class TimeEntriesCreateIT extends AbstractTimeEntriesIT {

  @Autowired private JwtEncoder jwtEncoder;

  // Scenario: Petición sin token
  @Test
  void listWithoutTokenIs401() throws Exception {
    expectProblem(mvc.perform(get("/api/time-entries")), 401, "unauthenticated");
  }

  // Scenario: Token expirado al registrar horas
  @Test
  void expiredTokenIs401AndCreatesNothing() throws Exception {
    Instant issuedAt = NOW.minus(Duration.ofHours(3));
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(LAWYER_ID)
            .claim("role", "LAWYER")
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plus(Duration.ofHours(2)))
            .build();
    String expired =
        jwtEncoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();

    expectProblem(create(expired, body("2026-11-06", "1.5")), 401, "unauthenticated");
    assertThat(countEntries()).isZero();
  }

  // Scenario: Registro válido
  @Test
  void lawyerRecordsHours() throws Exception {
    MvcResult result =
        create(lawyer(), body(matterId, "2026-11-06", "1.5", "d".repeat(40)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.hours").value(1.5))
            .andExpect(jsonPath("$.lawyerId").value(LAWYER_ID))
            .andExpect(jsonPath("$.lawyerName").value("Abogado Uno"))
            .andExpect(jsonPath("$.matterId").value(matterId.toString()))
            .andExpect(jsonPath("$.matterCode").isNotEmpty())
            .andExpect(jsonPath("$.workDate").value("2026-11-06"))
            .andExpect(jsonPath("$.version").value(0))
            .andExpect(jsonPath("$.editableUntil").value("2026-11-14"))
            .andExpect(jsonPath("$.rejectionReason").hasJsonPath())
            .andExpect(jsonPath("$.rejectionReason").isEmpty())
            .andReturn();

    String id = read(result).get("id").asText();
    assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/time-entries/" + id);
    assertThat(countEntries()).isEqualTo(1);
  }

  // Scenario: Asunto inexistente
  @Test
  void unknownMatterIs404() throws Exception {
    expectProblem(
        create(lawyer(), body(UUID.randomUUID(), "2026-11-06", "1", DESCRIPTION)),
        404,
        "not-found");
    assertThat(countEntries()).isZero();
  }

  // Scenario: Alta con campos de solo lectura
  @Test
  void readOnlyFieldsAreIgnoredOnCreate() throws Exception {
    Map<String, Object> body = body("2026-11-06", "2");
    body.put("id", "11111111-1111-4111-8111-111111111111");
    body.put("lawyerId", LAWYER_B_ID);
    body.put("lawyerName", "Otra persona");
    body.put("matterCode", "AS-1999-0001");
    body.put("status", "APPROVED");
    body.put("rejectionReason", "x");
    body.put("createdAt", "2026-01-01T00:00:00Z");
    body.put("editableUntil", "2030-01-01");
    body.put("version", 7);

    JsonNode created =
        read(
            create(lawyer(), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lawyerId").value(LAWYER_ID))
                .andExpect(jsonPath("$.lawyerName").value("Abogado Uno"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.rejectionReason").isEmpty())
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.editableUntil").value("2026-11-14"))
                .andReturn());

    assertThat(created.get("id").asText()).isNotEqualTo("11111111-1111-4111-8111-111111111111");
    assertThat(created.get("matterCode").asText()).isNotEqualTo("AS-1999-0001");
    assertThat(
            OffsetDateTime.parse(created.get("createdAt").asText())
                .atZoneSameInstant(ZoneId.of("America/La_Paz"))
                .toLocalDate())
        .hasToString("2026-11-07");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM time_entries WHERE lawyer_id = ?::uuid",
                Integer.class,
                LAWYER_B_ID))
        .isZero();
  }

  // Scenario: Descripción vacía
  @Test
  void blankDescriptionIs400() throws Exception {
    expectProblem(create(lawyer(), body(matterId, "2026-11-06", "1", " ")), 400, "validation-error")
        .andExpect(jsonPath("$.errors[?(@.field == 'description')]").exists())
        .andExpect(jsonPath("$.errors[?(@.field == 'description')].message").isNotEmpty());
    assertThat(countEntries()).isZero();
  }

  // Scenario: Descripción de 2001 caracteres
  @Test
  void description2001Is400() throws Exception {
    expectProblem(
            create(lawyer(), body(matterId, "2026-11-06", "1", "d".repeat(2001))),
            400,
            "validation-error")
        .andExpect(jsonPath("$.errors[?(@.field == 'description')]").exists());
  }

  // Scenario: Descripción de 2000 caracteres
  @Test
  void description2000IsAccepted() throws Exception {
    create(lawyer(), body(matterId, "2026-11-06", "1", "  " + "d".repeat(2000) + "  "))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.description").value("d".repeat(2000)));
  }

  // Scenario: Campos obligatorios ausentes
  @Test
  void missingWorkDateAndHoursAre400() throws Exception {
    expectProblem(
            create(lawyer(), body(matterId, null, null, DESCRIPTION)), 400, "validation-error")
        .andExpect(jsonPath("$.errors[?(@.field == 'workDate')]").exists())
        .andExpect(jsonPath("$.errors[?(@.field == 'hours')]").exists());
  }

  @Test
  void nonNumericHoursAndMalformedDateAre400() throws Exception {
    Map<String, Object> nonNumeric = body("2026-11-06", null);
    nonNumeric.put("hours", "uno");
    expectProblem(create(lawyer(), nonNumeric), 400, "validation-error")
        .andExpect(jsonPath("$.errors[0].field").value("hours"));
    expectProblem(
            create(lawyer(), body(matterId, "06/11/2026", "1", DESCRIPTION)),
            400,
            "validation-error")
        .andExpect(jsonPath("$.errors[0].field").value("workDate"));
    assertThat(countEntries()).isZero();
  }

  // Scenario: Límites válidos
  @Test
  void boundaryHoursAreAccepted() throws Exception {
    create(lawyer(), body("2026-11-05", "0.1")).andExpect(status().isCreated());
    create(lawyer(), body("2026-11-05", "23.9"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.hours").value(23.9));
  }

  // Scenario: Incremento inválido
  @Test
  void invalidIncrementIs422() throws Exception {
    expectProblem(create(lawyer(), body("2026-11-06", "1.25")), 422, "hours-invalid-increment")
        .andExpect(
            jsonPath("$.detail").value("Las horas deben registrarse en incrementos de 0,1."));
    assertThat(countEntries()).isZero();
  }

  // Scenario: Cero horas
  @Test
  void zeroHoursIs422() throws Exception {
    expectProblem(create(lawyer(), body("2026-11-06", "0")), 422, "hours-out-of-range")
        .andExpect(
            jsonPath("$.detail").value("Las horas deben ser mayores que 0 y como máximo 24."));
  }

  // Scenario: Más de 24 horas
  @Test
  void moreThan24Is422() throws Exception {
    expectProblem(create(lawyer(), body("2026-11-06", "24.1")), 422, "hours-out-of-range");
  }

  // Scenario: Horas negativas
  @Test
  void negativeHoursIs422() throws Exception {
    expectProblem(create(lawyer(), body("2026-11-06", "-1")), 422, "hours-out-of-range");
  }

  // Scenario: Completar exactamente 24 horas
  @Test
  void completingExactly24() throws Exception {
    insertEntry(LAWYER_ID, matterId, "2026-11-05", 160, "PENDING", NOW.minusSeconds(3600));
    insertEntry(LAWYER_ID, matterId, "2026-11-05", 40, "APPROVED", NOW.minusSeconds(3600));

    create(lawyer(), body("2026-11-05", "4")).andExpect(status().isCreated());
    assertThat(countedTenths(LAWYER_ID, "2026-11-05")).isEqualTo(240);
  }

  // Scenario: Superar el límite diario
  @Test
  void exceedingTheDailyLimitIs422WithExtensionMembers() throws Exception {
    insertEntry(LAWYER_ID, matterId, "2026-11-05", 120, "PENDING", NOW.minusSeconds(3600));
    insertEntry(LAWYER_ID, matterId, "2026-11-05", 80, "APPROVED", NOW.minusSeconds(3600));

    expectProblem(create(lawyer(), body("2026-11-05", "4.1")), 422, "daily-hours-exceeded")
        .andExpect(jsonPath("$.type").value(PROBLEM_TYPE + "daily-hours-exceeded"))
        .andExpect(jsonPath("$.status").value(422))
        .andExpect(jsonPath("$.limitHours").value(24))
        .andExpect(jsonPath("$.alreadyLoggedHours").value(20))
        .andExpect(jsonPath("$.title").value("Límite diario de horas superado"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "Ya tienes 20 h registradas ese día; puedes registrar como máximo 4 h más."));
    assertThat(countedTenths(LAWYER_ID, "2026-11-05")).isEqualTo(200);
  }

  // Scenario: Las horas rechazadas no cuentan
  @Test
  void rejectedHoursDoNotCount() throws Exception {
    insertEntry(LAWYER_ID, matterId, "2026-11-05", 200, "APPROVED", NOW.minusSeconds(3600));
    insertEntry(LAWYER_ID, matterId, "2026-11-05", 80, "REJECTED", NOW.minusSeconds(3600));

    create(lawyer(), body("2026-11-05", "4")).andExpect(status().isCreated());
  }

  // Scenario: Las horas de otro abogado no cuentan
  @Test
  void otherLawyerHoursDoNotCount() throws Exception {
    insertEntry(LAWYER_B_ID, matterId, "2026-11-05", 240, "APPROVED", NOW.minusSeconds(3600));

    create(lawyer(), body("2026-11-05", "8")).andExpect(status().isCreated());
  }

  // Scenario: Registrar horas de hoy
  @Test
  void todayIsAccepted() throws Exception {
    create(lawyer(), body("2026-11-07", "1")).andExpect(status().isCreated());
  }

  // Scenario: Registrar horas de mañana
  @Test
  void tomorrowIs422() throws Exception {
    expectProblem(create(lawyer(), body("2026-11-08", "1")), 422, "future-date")
        .andExpect(
            jsonPath("$.detail")
                .value("No se pueden registrar horas en una fecha posterior a hoy."));
    assertThat(countEntries()).isZero();
  }

  // Scenario: Borde de medianoche entre UTC y La Paz
  @Test
  void utcMidnightBoundaryUsesLaPazDate() throws Exception {
    clock.set(Instant.parse("2026-11-08T02:30:00Z"));

    expectProblem(create(lawyer(), body("2026-11-08", "1")), 422, "future-date");
    create(lawyer(), body("2026-11-07", "1")).andExpect(status().isCreated());
  }

  @Test
  void earlyUtcMorningIsStillPreviousDayInLaPaz() throws Exception {
    // 2026-11-07T03:30Z = 2026-11-06 23:30 en La Paz.
    clock.set(Instant.parse("2026-11-07T03:30:00Z"));

    expectProblem(create(lawyer(), body("2026-11-07", "1")), 422, "future-date");
    create(lawyer(), body("2026-11-06", "1")).andExpect(status().isCreated());
  }

  // Scenario: Asunto cerrado
  @Test
  void closedMatterIs422AndCreatesNothing() throws Exception {
    closeMatter(matterId);

    expectProblem(create(lawyer(), body("2026-11-06", "1")), 422, "matter-closed")
        .andExpect(
            jsonPath("$.detail")
                .value("No se pueden registrar ni editar horas en un asunto cerrado."));
    assertThat(countEntries()).isZero();
  }

  // Scenario: Socio intenta registrar horas
  @Test
  void partnerCannotRecordHours() throws Exception {
    expectProblem(create(partner(), body("2026-11-06", "1")), 403, "forbidden");
    assertThat(countEntries()).isZero();
  }

  @Test
  void createdEntryAppearsInTheAuthorsList() throws Exception {
    create(lawyer(), body("2026-11-06", "1.5")).andExpect(status().isCreated());

    mvc.perform(auth(get("/api/time-entries"), lawyer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].hours").value(1.5));
    mvc.perform(
            auth(post("/api/time-entries"), lawyer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("no es json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation-error"));
  }
}
