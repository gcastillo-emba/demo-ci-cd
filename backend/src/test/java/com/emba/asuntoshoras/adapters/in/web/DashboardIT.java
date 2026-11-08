package com.emba.asuntoshoras.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.emba.asuntoshoras.AbstractTimeEntriesIT;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Escenarios HTTP de {@code GET /api/dashboard/summary} (add-hours-dashboard): autenticación,
 * ventana por defecto y personalizada con reloj fijo (hoy = 2026-11-07 en La Paz), validación de la
 * ventana, sumas, exclusión de rechazados, alcance por rol y período vacío.
 *
 * <p>Nombres sintéticos de la spec → usuarios de demo: "Ana Pérez" = Abogada Dos ({@code
 * abogado2}), "Bruno Rojas" = Abogado Uno ({@code abogado1}), tercer abogado = Abogado Tres. Los
 * códigos {@code MAT-00n} de la spec son ilustrativos; aquí se usan {@code AS-2026-001n}.
 */
class DashboardIT extends AbstractTimeEntriesIT {

  private static final String PATH = "/api/dashboard/summary";
  private static final String ANA_ID = LAWYER_B_ID;
  private static final String ANA_NAME = "Abogada Dos";
  private static final String BRUNO_ID = LAWYER_ID;
  private static final String BRUNO_NAME = "Abogado Uno";
  private static final String CARLA_ID = "00000000-0000-4000-8000-000000000004";
  private static final String CARLA_NAME = "Abogado Tres";
  private static final Instant CREATED = NOW.minusSeconds(3_600);

  @Autowired private JwtEncoder jwtEncoder;

  private UUID mat1;
  private UUID mat2;
  private UUID mat3;
  private UUID mat4;

  @BeforeEach
  void prepareMatters() {
    mat1 = insertMatter(clientId, "AS-2026-0011", "Asesoría laboral ficticia", false);
    mat2 = insertMatter(clientId, "AS-2026-0012", "Litigio comercial de prueba", false);
    mat3 = insertMatter(clientId, "AS-2026-0013", "Registro de marca sintética", false);
    mat4 = insertMatter(clientId, "AS-2026-0014", "Due diligence simulada", true);
  }

  private void entry(String lawyerId, UUID matter, String workDate, int tenths, String status) {
    insertEntry(lawyerId, matter, workDate, tenths, status, CREATED);
  }

  private ResultActions summary(String token, String... params) throws Exception {
    var request = auth(get(PATH), token);
    for (int i = 0; i < params.length; i += 2) {
      request = request.param(params[i], params[i + 1]);
    }
    return mvc.perform(request);
  }

  private String anaToken() throws Exception {
    return lawyerB();
  }

  private ResultActions expectValidationError(ResultActions actions, String... fields)
      throws Exception {
    return expectProblem(actions, 400, "validation-error")
        .andExpect(jsonPath("$.errors", hasSize(fields.length)))
        .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder((Object[]) fields)))
        .andExpect(jsonPath("$.byMatter").doesNotExist())
        .andExpect(jsonPath("$.byLawyer").doesNotExist())
        .andExpect(jsonPath("$.totals").doesNotExist());
  }

  // ---------------------------------------------------------------- Autenticación

  // Scenario: Petición sin token
  @Test
  void requestWithoutTokenIs401() throws Exception {
    entry(ANA_ID, mat1, "2026-11-01", 10, "APPROVED");

    expectProblem(mvc.perform(get(PATH)), 401, "unauthenticated")
        .andExpect(jsonPath("$.byMatter").doesNotExist())
        .andExpect(jsonPath("$.byLawyer").doesNotExist())
        .andExpect(jsonPath("$.totals").doesNotExist());
  }

  // Scenario: Token expirado
  @Test
  void expiredTokenIs401() throws Exception {
    Instant issuedAt = clock.instant().minus(Duration.ofHours(3));
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(PARTNER_ID)
            .claim("role", "PARTNER")
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plus(Duration.ofHours(2)))
            .build();
    String expired =
        jwtEncoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();

    expectProblem(summary(expired), 401, "unauthenticated")
        .andExpect(jsonPath("$.totals").doesNotExist());
  }

  // ---------------------------------------------------------------- Ventana por defecto

  // Scenario: Límites de la ventana por defecto
  @Test
  void defaultWindowBoundaries() throws Exception {
    entry(ANA_ID, mat1, "2026-10-09", 20, "APPROVED"); // hoy-29
    entry(ANA_ID, mat1, "2026-11-07", 30, "APPROVED"); // hoy
    entry(ANA_ID, mat1, "2026-10-08", 50, "APPROVED"); // hoy-30

    summary(partner())
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.from").value("2026-10-09"))
        .andExpect(jsonPath("$.to").value("2026-11-07"))
        .andExpect(jsonPath("$.totals.approvedHours").value(5.0))
        .andExpect(jsonPath("$.totals.pendingHours").value(0.0));
  }

  // Scenario: "Hoy" se calcula en la zona de la firma y no en UTC
  @Test
  void todayIsComputedInFirmZone() throws Exception {
    clock.set(Instant.parse("2026-11-08T02:00:00Z")); // 2026-11-07 22:00 en La Paz
    entry(ANA_ID, mat1, "2026-11-07", 10, "APPROVED");
    entry(ANA_ID, mat1, "2026-10-09", 10, "PENDING");

    summary(partner())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.to").value("2026-11-07"))
        .andExpect(jsonPath("$.from").value("2026-10-09"))
        .andExpect(jsonPath("$.totals.approvedHours").value(1.0))
        .andExpect(jsonPath("$.totals.pendingHours").value(1.0));
  }

  // ---------------------------------------------------------------- Ventana personalizada

  // Scenario: Rango válido de 92 días
  @Test
  void validNinetyTwoDayRange() throws Exception {
    entry(ANA_ID, mat1, "2026-08-07", 70, "APPROVED"); // fuera
    entry(ANA_ID, mat1, "2026-08-08", 20, "APPROVED"); // extremo inicial
    entry(ANA_ID, mat1, "2026-11-07", 15, "PENDING"); // extremo final

    summary(partner(), "from", "2026-08-08", "to", "2026-11-07")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.from").value("2026-08-08"))
        .andExpect(jsonPath("$.to").value("2026-11-07"))
        .andExpect(jsonPath("$.totals.approvedHours").value(2.0))
        .andExpect(jsonPath("$.totals.pendingHours").value(1.5));
  }

  // Scenario: Rango de un solo día
  @Test
  void singleDayRange() throws Exception {
    entry(ANA_ID, mat1, "2026-11-01", 15, "PENDING");
    entry(ANA_ID, mat1, "2026-11-02", 30, "PENDING");
    entry(ANA_ID, mat1, "2026-10-31", 30, "PENDING");

    summary(partner(), "from", "2026-11-01", "to", "2026-11-01")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totals.pendingHours").value(1.5))
        .andExpect(jsonPath("$.totals.approvedHours").value(0.0));
  }

  // ---------------------------------------------------------------- Validación de la ventana

  // Scenario: Rango mayor a 92 días
  @Test
  void rangeLongerThan92DaysIs400OnTo() throws Exception {
    entry(ANA_ID, mat1, "2026-11-01", 10, "APPROVED");

    JsonNode body =
        read(
            expectValidationError(
                    summary(partner(), "from", "2026-08-07", "to", "2026-11-07"), "to")
                .andReturn());
    String message = body.get("errors").get(0).get("message").asText();
    assertThat(message).contains("92").doesNotContain("2026-08-07").doesNotContain("2026-11-07");
  }

  // Scenario: from posterior a to
  @Test
  void fromAfterToIs400OnFrom() throws Exception {
    expectValidationError(summary(anaToken(), "from", "2026-11-07", "to", "2026-11-01"), "from");
  }

  // Scenario: Solo se envía from
  @Test
  void onlyFromIs400OnTo() throws Exception {
    expectValidationError(summary(partner(), "from", "2026-11-01"), "to");
  }

  // Scenario: Solo se envía to
  @Test
  void onlyToIs400OnFrom() throws Exception {
    expectValidationError(summary(partner(), "to", "2026-11-07"), "from");
  }

  // Scenario: Fecha con formato inválido
  @Test
  void malformedDateIs400OnThatParameter() throws Exception {
    JsonNode body =
        read(
            expectValidationError(
                    summary(partner(), "from", "07-11-2026", "to", "2026-11-07"), "from")
                .andReturn());
    assertThat(body.toString()).doesNotContain("07-11-2026");
  }

  // Scenario: Fecha inexistente
  @Test
  void nonexistentDateIs400OnThatParameter() throws Exception {
    JsonNode body =
        read(
            expectValidationError(
                    summary(partner(), "from", "2026-11-01", "to", "2026-02-30"), "to")
                .andReturn());
    assertThat(body.toString()).doesNotContain("2026-02-30");
  }

  @Test
  void everyMalformedParameterIsReported() throws Exception {
    expectValidationError(
        summary(partner(), "from", "2026-13-01", "to", "<script>alert(1)</script>"), "from", "to");
  }

  @Test
  void signedOrLongYearsAreMalformed() throws Exception {
    expectValidationError(summary(partner(), "from", "+2026-11-01", "to", "2026-11-07"), "from");
  }

  @Test
  void unauthenticatedPrecedesValidation() throws Exception {
    // Precedencia 401 > 400: sin token, una ventana inválida sigue siendo 401.
    expectProblem(
        mvc.perform(get(PATH).param("from", "2026-11-07").param("to", "2026-11-01")),
        401,
        "unauthenticated");
  }

  // ---------------------------------------------------------------- Sumas

  // Scenario: Suma por asunto separando aprobadas y pendientes
  @Test
  void sumsPerMatterSplitApprovedAndPending() throws Exception {
    entry(ANA_ID, mat1, "2026-11-01", 20, "APPROVED");
    entry(BRUNO_ID, mat1, "2026-11-02", 15, "APPROVED");
    entry(ANA_ID, mat1, "2026-11-03", 5, "PENDING");
    entry(ANA_ID, mat2, "2026-11-04", 40, "PENDING");

    String raw =
        summary(partner())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.byMatter", hasSize(2)))
            .andExpect(jsonPath("$.byMatter[0].matterCode").value("AS-2026-0011"))
            .andExpect(jsonPath("$.byMatter[0].matterId").value(mat1.toString()))
            .andExpect(jsonPath("$.byMatter[0].matterTitle").value("Asesoría laboral ficticia"))
            .andExpect(jsonPath("$.byMatter[0].approvedHours").value(3.5))
            .andExpect(jsonPath("$.byMatter[0].pendingHours").value(0.5))
            .andExpect(jsonPath("$.byMatter[1].matterCode").value("AS-2026-0012"))
            .andExpect(jsonPath("$.byMatter[1].approvedHours").value(0.0))
            .andExpect(jsonPath("$.byMatter[1].pendingHours").value(4.0))
            .andReturn()
            .getResponse()
            .getContentAsString();
    // Una décima siempre, también en enteros y ceros.
    assertThat(raw).contains("\"approvedHours\":0.0").contains("\"pendingHours\":4.0");
  }

  // Scenario: Suma por abogado
  @Test
  void sumsPerLawyerOrderedByName() throws Exception {
    entry(ANA_ID, mat1, "2026-11-01", 40, "APPROVED");
    entry(ANA_ID, mat2, "2026-11-02", 20, "APPROVED");
    entry(ANA_ID, mat2, "2026-11-03", 20, "PENDING");
    entry(BRUNO_ID, mat1, "2026-11-03", 10, "PENDING");

    summary(partner())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.byLawyer", hasSize(2)))
        .andExpect(jsonPath("$.byLawyer[0].lawyerName").value(ANA_NAME))
        .andExpect(jsonPath("$.byLawyer[0].lawyerId").value(ANA_ID))
        .andExpect(jsonPath("$.byLawyer[0].approvedHours").value(6.0))
        .andExpect(jsonPath("$.byLawyer[0].pendingHours").value(2.0))
        .andExpect(jsonPath("$.byLawyer[1].lawyerName").value(BRUNO_NAME))
        .andExpect(jsonPath("$.byLawyer[1].approvedHours").value(0.0))
        .andExpect(jsonPath("$.byLawyer[1].pendingHours").value(1.0));
  }

  // Scenario: Totales consistentes con las filas
  @Test
  void totalsMatchRowSums() throws Exception {
    entry(ANA_ID, mat1, "2026-11-01", 20, "APPROVED");
    entry(BRUNO_ID, mat1, "2026-11-02", 15, "APPROVED");
    entry(ANA_ID, mat1, "2026-11-03", 5, "PENDING");
    entry(CARLA_ID, mat2, "2026-11-04", 40, "PENDING");

    JsonNode body = read(summary(partner()).andExpect(status().isOk()).andReturn());

    assertThat(body.at("/totals/approvedHours").decimalValue()).isEqualByComparingTo("3.5");
    assertThat(body.at("/totals/pendingHours").decimalValue()).isEqualByComparingTo("4.5");
    for (String list : new String[] {"byMatter", "byLawyer"}) {
      java.math.BigDecimal approved = java.math.BigDecimal.ZERO;
      java.math.BigDecimal pending = java.math.BigDecimal.ZERO;
      for (JsonNode row : body.get(list)) {
        approved = approved.add(row.get("approvedHours").decimalValue());
        pending = pending.add(row.get("pendingHours").decimalValue());
      }
      assertThat(approved).as(list).isEqualByComparingTo("3.5");
      assertThat(pending).as(list).isEqualByComparingTo("4.5");
    }
  }

  // Scenario: Registro rechazado no suma
  @Test
  void rejectedEntriesDoNotCount() throws Exception {
    entry(ANA_ID, mat3, "2026-11-01", 80, "REJECTED");
    entry(BRUNO_ID, mat1, "2026-11-02", 10, "APPROVED");

    summary(partner())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.byMatter", hasSize(1)))
        .andExpect(jsonPath("$.byMatter[0].matterId").value(mat1.toString()))
        .andExpect(jsonPath("$.byLawyer", hasSize(1)))
        .andExpect(jsonPath("$.byLawyer[0].lawyerName").value(BRUNO_NAME))
        .andExpect(jsonPath("$.totals.approvedHours").value(1.0))
        .andExpect(jsonPath("$.totals.pendingHours").value(0.0));
  }

  // ---------------------------------------------------------------- Alcance por rol (tarea 4.1)

  // Scenario: El socio ve a todos los abogados
  @Test
  void partnerSeesThreeLawyersAndFourMatters() throws Exception {
    entry(ANA_ID, mat1, "2026-11-01", 10, "APPROVED");
    entry(BRUNO_ID, mat2, "2026-11-01", 10, "PENDING");
    entry(CARLA_ID, mat3, "2026-11-01", 10, "APPROVED");
    entry(CARLA_ID, mat4, "2026-11-02", 10, "PENDING"); // asunto cerrado: sí aparece (Supuesto 3)

    summary(partner())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.byLawyer", hasSize(3)))
        .andExpect(jsonPath("$.byMatter", hasSize(4)))
        .andExpect(jsonPath("$.byLawyer[0].lawyerName").value(ANA_NAME))
        .andExpect(jsonPath("$.byLawyer[1].lawyerName").value(CARLA_NAME))
        .andExpect(jsonPath("$.byLawyer[2].lawyerName").value(BRUNO_NAME));
  }

  // Scenario: El abogado solo ve sus horas
  @Test
  void lawyerOnlySeesOwnHours() throws Exception {
    entry(ANA_ID, mat1, "2026-11-01", 30, "APPROVED");
    entry(BRUNO_ID, mat1, "2026-11-02", 50, "APPROVED");
    entry(BRUNO_ID, mat2, "2026-11-03", 20, "PENDING");

    String raw =
        summary(anaToken())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.byLawyer", hasSize(1)))
            .andExpect(jsonPath("$.byLawyer[0].lawyerId").value(ANA_ID))
            .andExpect(jsonPath("$.byLawyer[0].lawyerName").value(ANA_NAME))
            .andExpect(jsonPath("$.byLawyer[0].approvedHours").value(3.0))
            .andExpect(jsonPath("$.byMatter", hasSize(1)))
            .andExpect(jsonPath("$.byMatter[0].matterId").value(mat1.toString()))
            .andExpect(jsonPath("$.byMatter[0].approvedHours").value(3.0))
            .andExpect(jsonPath("$.byMatter[0].pendingHours").value(0.0))
            .andExpect(jsonPath("$.totals.approvedHours").value(3.0))
            .andExpect(jsonPath("$.totals.pendingHours").value(0.0))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(raw)
        .doesNotContain(BRUNO_NAME)
        .doesNotContain(BRUNO_ID)
        .doesNotContain("AS-2026-0012");
  }

  // Scenario: El abogado no puede ampliar su alcance con parámetros
  @Test
  void lawyerCannotWidenScopeWithParameters() throws Exception {
    entry(ANA_ID, mat1, "2026-10-15", 10, "PENDING");
    entry(BRUNO_ID, mat2, "2026-10-20", 50, "APPROVED");

    String raw =
        summary(
                anaToken(),
                "from",
                "2026-10-01",
                "to",
                "2026-11-07",
                "lawyerId",
                BRUNO_ID,
                "scope",
                "all")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.byLawyer", hasSize(1)))
            .andExpect(jsonPath("$.byLawyer[0].lawyerId").value(ANA_ID))
            .andExpect(jsonPath("$.byMatter", hasSize(1)))
            .andExpect(jsonPath("$.totals.approvedHours").value(0.0))
            .andExpect(jsonPath("$.totals.pendingHours").value(1.0))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(raw).doesNotContain(BRUNO_NAME).doesNotContain(BRUNO_ID);
  }

  @Test
  void partnerIgnoresUnknownParameters() throws Exception {
    entry(ANA_ID, mat1, "2026-11-01", 10, "APPROVED");
    entry(BRUNO_ID, mat2, "2026-11-01", 10, "APPROVED");

    summary(partner(), "lawyerId", ANA_ID)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.byLawyer", hasSize(2)));
  }

  // ---------------------------------------------------------------- Período vacío

  // Scenario: Ventana sin registros para el socio
  @Test
  void emptyWindowForPartner() throws Exception {
    entry(ANA_ID, mat1, "2026-10-01", 10, "APPROVED"); // fuera de la ventana

    summary(partner())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.byMatter", hasSize(0)))
        .andExpect(jsonPath("$.byLawyer", hasSize(0)))
        .andExpect(jsonPath("$.totals.approvedHours").value(0.0))
        .andExpect(jsonPath("$.totals.pendingHours").value(0.0));
  }

  // Scenario: Abogado sin horas propias en la ventana
  @Test
  void lawyerWithoutOwnHoursGetsSingleZeroRow() throws Exception {
    entry(BRUNO_ID, mat1, "2026-11-01", 50, "APPROVED");
    entry(CARLA_ID, mat2, "2026-11-02", 20, "PENDING");
    entry(ANA_ID, mat3, "2026-11-03", 30, "REJECTED");

    String raw =
        summary(anaToken())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.byMatter", hasSize(0)))
            .andExpect(jsonPath("$.totals.approvedHours").value(0.0))
            .andExpect(jsonPath("$.totals.pendingHours").value(0.0))
            .andExpect(jsonPath("$.byLawyer", hasSize(1)))
            .andExpect(jsonPath("$.byLawyer[0].lawyerId").value(ANA_ID))
            .andExpect(jsonPath("$.byLawyer[0].lawyerName").value(ANA_NAME))
            .andExpect(jsonPath("$.byLawyer[0].approvedHours").value(0.0))
            .andExpect(jsonPath("$.byLawyer[0].pendingHours").value(0.0))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(raw).doesNotContain(BRUNO_NAME).doesNotContain(CARLA_NAME);
  }
}
