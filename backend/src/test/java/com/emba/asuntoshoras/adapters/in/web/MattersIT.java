package com.emba.asuntoshoras.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.emba.asuntoshoras.AbstractClientsMattersIT;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
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

/** Escenarios de asuntos de {@code specs/clients-matters/spec.md} (HTTP → PostgreSQL real). */
class MattersIT extends AbstractClientsMattersIT {

  private static final String CODE_PATTERN = "^AS-\\d{4}-\\d{4}$";

  @Autowired private JwtEncoder jwtEncoder;
  @Autowired private Clock clock;

  private JsonNode read(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private JsonNode getMatter(UUID id) throws Exception {
    return read(
        mvc.perform(auth(get("/api/matters/{id}", id), partnerToken()))
            .andExpect(status().isOk())
            .andReturn());
  }

  // Scenario: Creación de asunto con token expirado
  @Test
  void creatingMatterWithExpiredTokenIs401AndCreatesNothing() throws Exception {
    UUID clientId = createClient("Inversiones Andinas S.A.", null);
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

    mvc.perform(
            auth(post("/api/matters"), expired)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("clientId", clientId.toString(), "title", "Título válido"))))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("unauthenticated"));
    assertThat(countMatters()).isZero();
  }

  // Scenario: Alta de asunto
  @Test
  void partnerCreatesMatter() throws Exception {
    UUID clientId = createClient("Inversiones Andinas S.A.", null);

    MvcResult result =
        mvc.perform(
                auth(post("/api/matters"), partnerToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json(
                            Map.of(
                                "clientId",
                                clientId.toString(),
                                "title",
                                "Due diligence adquisición planta"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.closedAt").value(nullValue()))
            .andExpect(jsonPath("$.closedAt").hasJsonPath())
            .andExpect(jsonPath("$.description").hasJsonPath())
            .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.matchesPattern(CODE_PATTERN)))
            .andExpect(jsonPath("$.clientId").value(clientId.toString()))
            .andExpect(jsonPath("$.clientName").value("Inversiones Andinas S.A."))
            .andExpect(jsonPath("$.title").value("Due diligence adquisición planta"))
            .andExpect(jsonPath("$.createdAt").isNotEmpty())
            .andReturn();

    String id = read(result).get("id").asText();
    assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/matters/" + id);
  }

  // Scenario: Campos de solo lectura ignorados
  @Test
  void readOnlyFieldsAreIgnored() throws Exception {
    UUID clientId = createClient("Inversiones Andinas S.A.", null);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("clientId", clientId.toString());
    body.put("title", "Título válido");
    body.put("status", "CLOSED");
    body.put("code", "AS-1999-0001");
    body.put("closedAt", "2026-01-01T00:00:00Z");

    mvc.perform(
            auth(post("/api/matters"), partnerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("OPEN"))
        .andExpect(jsonPath("$.closedAt").value(nullValue()))
        .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not("AS-1999-0001")));
  }

  // Scenario: Cliente del asunto inexistente
  @Test
  void unknownClientIs404AndNothingIsCreated() throws Exception {
    mvc.perform(
            auth(post("/api/matters"), partnerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("clientId", UUID.randomUUID().toString(), "title", "Título"))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("not-found"));
    assertThat(countMatters()).isZero();
  }

  // Scenario: Título ausente y clientId ausente
  @Test
  void emptyBodyReportsClientIdAndTitle() throws Exception {
    mvc.perform(
            auth(post("/api/matters"), partnerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation-error"))
        .andExpect(jsonPath("$.errors[?(@.field == 'clientId')]").exists())
        .andExpect(jsonPath("$.errors[?(@.field == 'title')]").exists());
  }

  // Scenario: Descripción de más de 2000 caracteres
  @Test
  void descriptionOver2000Is400() throws Exception {
    UUID clientId = createClient("Inversiones Andinas S.A.", null);

    mvc.perform(
            auth(post("/api/matters"), partnerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "clientId",
                            clientId.toString(),
                            "title",
                            "Título",
                            "description",
                            "d".repeat(2001)))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation-error"))
        .andExpect(jsonPath("$.errors[?(@.field == 'description')]").exists());
    assertThat(countMatters()).isZero();
  }

  // Scenario: Título de más de 200 caracteres
  @Test
  void titleOver200Is400() throws Exception {
    UUID clientId = createClient("Inversiones Andinas S.A.", null);

    mvc.perform(
            auth(post("/api/matters"), partnerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("clientId", clientId.toString(), "title", "t".repeat(201)))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[?(@.field == 'title')]").exists());
    assertThat(countMatters()).isZero();
  }

  @Test
  void malformedClientIdInBodyIs400() throws Exception {
    mvc.perform(
            auth(post("/api/matters"), partnerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("clientId", "no-es-uuid", "title", "Título"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation-error"))
        .andExpect(jsonPath("$.errors[?(@.field == 'clientId')]").exists());
  }

  // Scenario: Filtrar por cliente y estado
  @Test
  void lawyerFiltersByClientAndStatus() throws Exception {
    UUID c = createClient("Inversiones Andinas S.A.", null);
    UUID other = createClient("Andes Logística", null);
    UUID open = insertMatter(c, "AS-2026-0001", "Abierto de C", false);
    insertMatter(c, "AS-2026-0002", "Cerrado de C", true);
    insertMatter(other, "AS-2026-0003", "Abierto de otro", false);

    mvc.perform(
            auth(
                get("/api/matters").param("clientId", c.toString()).param("status", "OPEN"),
                lawyerToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].id").value(open.toString()))
        .andExpect(jsonPath("$.items[0].clientName").value("Inversiones Andinas S.A."));
  }

  // Scenario: Buscar por código
  @Test
  void searchesByCode() throws Exception {
    UUID c = createClient("Inversiones Andinas S.A.", null);
    insertMatter(c, "AS-2026-0007", "Siete", false);
    insertMatter(c, "AS-2026-0012", "Doce", false);

    mvc.perform(auth(get("/api/matters").param("q", "0012"), partnerToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].code").value("AS-2026-0012"));
  }

  // Scenario: Buscar por título
  @Test
  void searchesByTitleIgnoringCase() throws Exception {
    UUID c = createClient("Inversiones Andinas S.A.", null);
    UUID id = insertMatter(c, "AS-2026-0001", "Due diligence adquisición planta", false);
    insertMatter(c, "AS-2026-0002", "Registro de marca", false);

    mvc.perform(auth(get("/api/matters").param("q", "DILIGENCE"), lawyerToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].id").value(id.toString()));
  }

  // Scenario: Estado inválido
  @Test
  void invalidStatusIs400() throws Exception {
    mvc.perform(auth(get("/api/matters").param("status", "ARCHIVED"), lawyerToken()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation-error"))
        .andExpect(jsonPath("$.errors[?(@.field == 'status')]").exists());
  }

  // Scenario: Segunda página de asuntos
  @Test
  void secondPageOfMattersOrderedByCodeDesc() throws Exception {
    UUID c = createClient("Inversiones Andinas S.A.", null);
    for (int i = 1; i <= 25; i++) {
      insertMatter(c, String.format("AS-2026-%04d", i), "Asunto " + i, i % 2 == 0);
    }

    mvc.perform(auth(get("/api/matters").param("page", "1").param("size", "10"), lawyerToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(10)))
        .andExpect(jsonPath("$.items[0].code").value("AS-2026-0015"))
        .andExpect(jsonPath("$.items[9].code").value("AS-2026-0006"))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.size").value(10))
        .andExpect(jsonPath("$.totalItems").value(25))
        .andExpect(jsonPath("$.totalPages").value(3));
  }

  // Scenario: Página negativa
  @Test
  void negativePageIs400() throws Exception {
    mvc.perform(auth(get("/api/matters").param("page", "-1"), lawyerToken()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation-error"))
        .andExpect(jsonPath("$.errors[?(@.field == 'page')]").exists());
  }

  // Scenario: Asunto existente
  @Test
  void lawyerGetsExistingMatter() throws Exception {
    UUID c = createClient("Inversiones Andinas S.A.", null);
    UUID id = insertMatter(c, "AS-2026-0001", "Due diligence adquisición planta", false);

    mvc.perform(auth(get("/api/matters/{id}", id), lawyerToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id.toString()))
        .andExpect(jsonPath("$.code").value("AS-2026-0001"))
        .andExpect(jsonPath("$.clientId").value(c.toString()))
        .andExpect(jsonPath("$.clientName").value("Inversiones Andinas S.A."))
        .andExpect(jsonPath("$.title").value("Due diligence adquisición planta"))
        .andExpect(jsonPath("$.description").hasJsonPath())
        .andExpect(jsonPath("$.status").value("OPEN"))
        .andExpect(jsonPath("$.createdAt").isNotEmpty())
        .andExpect(jsonPath("$.closedAt").hasJsonPath());
  }

  // Scenario: Asunto inexistente
  @Test
  void unknownMatterIs404() throws Exception {
    mvc.perform(auth(get("/api/matters/{id}", UUID.randomUUID()), partnerToken()))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("not-found"))
        .andExpect(jsonPath("$.traceId").isNotEmpty());
  }

  // Scenario: Cierre exitoso
  @Test
  void partnerClosesMatterAndGetReturnsSameClosedAt() throws Exception {
    UUID c = createClient("Inversiones Andinas S.A.", null);
    UUID id = createMatter(c, "Asunto a cerrar");

    JsonNode closed =
        read(
            mvc.perform(auth(post("/api/matters/{id}/close", id), partnerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty())
                .andReturn());

    JsonNode after = getMatter(id);
    assertThat(after.get("status").asText()).isEqualTo("CLOSED");
    assertThat(Instant.parse(after.get("closedAt").asText()))
        .isEqualTo(Instant.parse(closed.get("closedAt").asText()));
  }

  // Scenario: El cierre no impone condiciones sobre registros de horas
  @Test
  void closeNeedsNoBodyAndSaysNothingAboutTimeEntries() throws Exception {
    UUID c = createClient("Inversiones Andinas S.A.", null);
    UUID id = createMatter(c, "Asunto sin horas");

    MvcResult result =
        mvc.perform(auth(post("/api/matters/{id}/close", id), partnerToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CLOSED"))
            .andReturn();
    assertThat(result.getResponse().getContentAsString())
        .doesNotContainIgnoringCase("timeEntr")
        .doesNotContainIgnoringCase("hours");
  }

  // Scenario: Cierre de asunto inexistente
  @Test
  void closingUnknownMatterIs404() throws Exception {
    mvc.perform(auth(post("/api/matters/{id}/close", UUID.randomUUID()), partnerToken()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("not-found"));
  }

  // Scenario: Cerrar dos veces
  @Test
  void closingTwiceIs409AndKeepsClosedAt() throws Exception {
    UUID c = createClient("Inversiones Andinas S.A.", null);
    UUID id = createMatter(c, "Asunto cerrado");
    mvc.perform(auth(post("/api/matters/{id}/close", id), partnerToken()))
        .andExpect(status().isOk());
    String t1 = getMatter(id).get("closedAt").asText();

    mvc.perform(auth(post("/api/matters/{id}/close", id), partnerToken()))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(PROBLEM_TYPE + "matter-already-closed"))
        .andExpect(jsonPath("$.code").value("matter-already-closed"))
        .andExpect(jsonPath("$.traceId").isNotEmpty());

    assertThat(getMatter(id).get("closedAt").asText()).isEqualTo(t1);
  }

  // Scenario: Intento de reabrir con PATCH
  @Test
  void reopeningWithPatchIs405AndMatterStaysClosed() throws Exception {
    UUID c = createClient("Inversiones Andinas S.A.", null);
    UUID id = insertMatter(c, "AS-2026-0001", "Cerrado", true);

    mvc.perform(
            auth(patch("/api/matters/{id}", id), partnerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "OPEN"))))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("method-not-allowed"));

    assertThat(getMatter(id).get("status").asText()).isEqualTo("CLOSED");
  }

  // Scenario: Abogado envía un método no ofrecido
  @Test
  void lawyerUnofferedMethodsAre405Not403() throws Exception {
    UUID c = createClient("Inversiones Andinas S.A.", null);
    UUID id = insertMatter(c, "AS-2026-0001", "Abierto", false);

    mvc.perform(
            auth(patch("/api/matters/{id}", id), lawyerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "CLOSED"))))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value("method-not-allowed"));
    mvc.perform(
            auth(put("/api/matters/{id}", id), lawyerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("title", "Cambiado"))))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value("method-not-allowed"));
    mvc.perform(auth(delete("/api/matters/{id}", id), lawyerToken()))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value("method-not-allowed"));

    JsonNode after = getMatter(id);
    assertThat(after.get("status").asText()).isEqualTo("OPEN");
    assertThat(after.get("title").asText()).isEqualTo("Abierto");
  }

  // Scenario: Abogado intenta crear un asunto
  @Test
  void lawyerCannotCreateMatter() throws Exception {
    UUID c = createClient("Inversiones Andinas S.A.", null);

    mvc.perform(
            auth(post("/api/matters"), lawyerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("clientId", c.toString(), "title", "Título válido"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("forbidden"))
        .andExpect(jsonPath("$.traceId").isNotEmpty());
    assertThat(countMatters()).isZero();
  }

  // Scenario: Abogado intenta cerrar un asunto
  @Test
  void lawyerCannotCloseMatter() throws Exception {
    UUID c = createClient("Inversiones Andinas S.A.", null);
    UUID id = insertMatter(c, "AS-2026-0001", "Abierto", false);

    mvc.perform(auth(post("/api/matters/{id}/close", id), lawyerToken()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("forbidden"));

    assertThat(getMatter(id).get("status").asText()).isEqualTo("OPEN");
  }

  // Scenario: Abogado cierra un asunto inexistente
  @Test
  void lawyerClosingUnknownMatterIs403Not404() throws Exception {
    mvc.perform(auth(post("/api/matters/{id}/close", UUID.randomUUID()), lawyerToken()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("forbidden"));
  }

  // Scenario: Cuerpo inválido con cliente inexistente
  @Test
  void invalidBodyWithUnknownClientIs400Not404() throws Exception {
    mvc.perform(
            auth(post("/api/matters"), partnerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("clientId", UUID.randomUUID().toString()))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation-error"))
        .andExpect(jsonPath("$.errors[?(@.field == 'title')]").exists());
  }
}
