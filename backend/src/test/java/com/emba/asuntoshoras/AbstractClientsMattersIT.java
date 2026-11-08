package com.emba.asuntoshoras;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Base de los IT de clientes y asuntos: cada test parte de tablas vacías (el seed de demo se borra)
 * y dispone de tokens de Socio y Abogado y de ayudas para crear datos sintéticos.
 */
public abstract class AbstractClientsMattersIT extends AbstractIT {

  protected static final String LAWYER_EMAIL = "abogado1@asuntos.test";
  protected static final String PROBLEM_TYPE = "https://asuntos-horas.example/problems/";

  @Autowired protected JdbcTemplate jdbc;

  private final Map<String, String> tokens = new HashMap<>();

  @BeforeEach
  void cleanClientsAndMatters() {
    jdbc.update("DELETE FROM matters");
    jdbc.update("DELETE FROM clients");
    jdbc.update("DELETE FROM matter_code_seq");
  }

  protected String partnerToken() throws Exception {
    return token(PARTNER_EMAIL);
  }

  protected String lawyerToken() throws Exception {
    return token(LAWYER_EMAIL);
  }

  private String token(String email) throws Exception {
    String cached = tokens.get(email);
    if (cached == null) {
      cached = accessToken(email);
      tokens.put(email, cached);
    }
    return cached;
  }

  /** Agrega el encabezado {@code Authorization: Bearer}. */
  protected static MockHttpServletRequestBuilder auth(
      MockHttpServletRequestBuilder request, String token) {
    return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
  }

  protected String json(Object body) throws Exception {
    return objectMapper.writeValueAsString(body);
  }

  /** Crea un cliente por la API como Socio y devuelve su id. */
  protected UUID createClient(String name, String taxId) throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", name);
    if (taxId != null) {
      body.put("taxId", taxId);
    }
    String response =
        mvc.perform(
                auth(post("/api/clients"), partnerToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(body)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(objectMapper.readTree(response).get("id").asText());
  }

  /** Crea un asunto por la API como Socio y devuelve su id. */
  protected UUID createMatter(UUID clientId, String title) throws Exception {
    String response =
        mvc.perform(
                auth(post("/api/matters"), partnerToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("clientId", clientId.toString(), "title", title))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(objectMapper.readTree(response).get("id").asText());
  }

  /** Inserta un asunto directamente en la base (para fijar códigos y estados de partida). */
  protected UUID insertMatter(UUID clientId, String code, String title, boolean closed) {
    UUID id = UUID.randomUUID();
    Instant created = Instant.parse("2026-11-01T12:00:00Z");
    jdbc.update(
        "INSERT INTO matters (id, code, client_id, title, status, created_at, closed_at)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?)",
        id,
        code,
        clientId,
        title,
        closed ? "CLOSED" : "OPEN",
        Timestamp.from(created),
        closed ? Timestamp.from(created.plusSeconds(3600)) : null);
    return id;
  }

  protected int countClients() {
    return jdbc.queryForObject("SELECT count(*) FROM clients", Integer.class);
  }

  protected int countMatters() {
    return jdbc.queryForObject("SELECT count(*) FROM matters", Integer.class);
  }
}
