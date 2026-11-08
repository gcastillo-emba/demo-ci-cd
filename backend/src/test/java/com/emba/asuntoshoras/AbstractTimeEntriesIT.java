package com.emba.asuntoshoras;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Base de los IT de registros de horas: reloj fijado en 2026-11-07 10:00 en La Paz
 * (2026-11-07T14:00Z), tablas vacías, un asunto {@code OPEN} y ayudas para crear registros por la
 * API o directamente en la base (para fijar estados y fechas de creación).
 */
public abstract class AbstractTimeEntriesIT extends AbstractControlledIT {

  protected static final Instant NOW = Instant.parse("2026-11-07T14:00:00Z");
  protected static final String LAWYER_ID = "00000000-0000-4000-8000-000000000002";
  protected static final String LAWYER_B_EMAIL = "abogado2@asuntos.test";
  protected static final String LAWYER_B_ID = "00000000-0000-4000-8000-000000000003";
  protected static final String DESCRIPTION = "Revisión del contrato de suministro ficticio";

  private final Map<String, String> tokensByInstant = new HashMap<>();

  protected UUID clientId;
  protected UUID matterId;

  @BeforeEach
  void prepareTimeEntries() throws Exception {
    clock.set(NOW);
    clientId = createClient("Inversiones Ficticias del Sur S.A.", null);
    matterId = createMatter(clientId, "Asesoría societaria de prueba");
  }

  /** Token vigente para el reloj actual (se renueva si el test movió el reloj). */
  protected String tokenFor(String email) throws Exception {
    String key = email + "@" + clock.instant();
    String token = tokensByInstant.get(key);
    if (token == null) {
      token = accessToken(email);
      tokensByInstant.put(key, token);
    }
    return token;
  }

  protected String lawyer() throws Exception {
    return tokenFor(LAWYER_EMAIL);
  }

  protected String lawyerB() throws Exception {
    return tokenFor(LAWYER_B_EMAIL);
  }

  protected String partner() throws Exception {
    return tokenFor(PARTNER_EMAIL);
  }

  protected static Map<String, Object> body(
      UUID matter, String workDate, Object hours, String description) {
    Map<String, Object> body = new LinkedHashMap<>();
    if (matter != null) {
      body.put("matterId", matter.toString());
    }
    if (workDate != null) {
      body.put("workDate", workDate);
    }
    if (hours != null) {
      body.put("hours", hours instanceof String s ? new BigDecimal(s) : hours);
    }
    if (description != null) {
      body.put("description", description);
    }
    return body;
  }

  protected Map<String, Object> body(String workDate, Object hours) {
    return body(matterId, workDate, hours, DESCRIPTION);
  }

  protected ResultActions create(String token, Map<String, Object> body) throws Exception {
    return mvc.perform(
        auth(post("/api/time-entries"), token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(body)));
  }

  protected ResultActions update(String token, UUID id, Map<String, Object> body) throws Exception {
    return mvc.perform(
        auth(put("/api/time-entries/{id}", id), token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(body)));
  }

  protected Map<String, Object> withVersion(Map<String, Object> body, long version) {
    Map<String, Object> copy = new LinkedHashMap<>(body);
    copy.put("version", version);
    return copy;
  }

  protected ResultActions approve(String token, UUID id) throws Exception {
    return mvc.perform(auth(post("/api/time-entries/{id}/approve", id), token));
  }

  protected ResultActions reject(String token, UUID id, String reason) throws Exception {
    Map<String, Object> body = new HashMap<>();
    if (reason != null) {
      body.put("reason", reason);
    }
    return mvc.perform(
        auth(post("/api/time-entries/{id}/reject", id), token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(body)));
  }

  protected JsonNode read(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  /** Verifica la estructura completa de un Problem Details (RFC 9457) de un slug. */
  protected ResultActions expectProblem(ResultActions actions, int httpStatus, String code)
      throws Exception {
    return actions
        .andExpect(status().is(httpStatus))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(PROBLEM_TYPE + code))
        .andExpect(jsonPath("$.status").value(httpStatus))
        .andExpect(jsonPath("$.code").value(code))
        .andExpect(jsonPath("$.title").isNotEmpty())
        .andExpect(jsonPath("$.detail").isNotEmpty())
        .andExpect(jsonPath("$.traceId").isNotEmpty());
  }

  /** Inserta un registro directamente en la base (estado y creación arbitrarios). */
  protected UUID insertEntry(
      String lawyerId, UUID matter, String workDate, int tenths, String status, Instant createdAt) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO time_entries (id, matter_id, lawyer_id, work_date, hours_tenths, description,"
            + " status, rejection_reason, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        id,
        matter,
        UUID.fromString(lawyerId),
        Date.valueOf(LocalDate.parse(workDate)),
        tenths,
        "Trabajo sintético previo",
        status,
        "REJECTED".equals(status) ? "Motivo previo" : null,
        Timestamp.from(createdAt));
    return id;
  }

  /** Registro {@code PENDING} del abogado 1 creado hace un día (editable). */
  protected UUID insertPending(String workDate, int tenths) {
    return insertEntry(LAWYER_ID, matterId, workDate, tenths, "PENDING", NOW.minusSeconds(86_400));
  }

  protected Map<String, Object> row(UUID id) {
    return jdbc.queryForMap("SELECT * FROM time_entries WHERE id = ?", id);
  }

  protected String statusOf(UUID id) {
    return jdbc.queryForObject("SELECT status FROM time_entries WHERE id = ?", String.class, id);
  }

  protected int countEntries() {
    return jdbc.queryForObject("SELECT count(*) FROM time_entries", Integer.class);
  }

  protected int countedTenths(String lawyerId, String workDate) {
    return jdbc.queryForObject(
        "SELECT coalesce(sum(hours_tenths), 0) FROM time_entries WHERE lawyer_id = ?"
            + " AND work_date = ? AND status IN ('PENDING', 'APPROVED')",
        Integer.class,
        UUID.fromString(lawyerId),
        Date.valueOf(LocalDate.parse(workDate)));
  }

  protected UUID closeMatter(UUID matter) throws Exception {
    mvc.perform(auth(post("/api/matters/{id}/close", matter), partner()))
        .andExpect(status().isOk());
    return matter;
  }
}
