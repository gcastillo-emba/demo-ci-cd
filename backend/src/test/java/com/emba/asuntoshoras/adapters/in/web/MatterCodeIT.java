package com.emba.asuntoshoras.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.emba.asuntoshoras.AbstractControlledIT;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

/** Escenarios del requisito "Código de asunto generado por el servidor" con reloj controlado. */
class MatterCodeIT extends AbstractControlledIT {

  private UUID clientId;

  @BeforeEach
  void createClientAt20261107() throws Exception {
    clock.set(Instant.parse("2026-11-07T14:00:00Z"));
    clientId = createClient("Inversiones Andinas S.A.", null);
  }

  private MockHttpServletResponse postMatter(String token, String title) throws Exception {
    return mvc.perform(
            auth(post("/api/matters"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("clientId", clientId.toString(), "title", title))))
        .andReturn()
        .getResponse();
  }

  private String code(MockHttpServletResponse response) throws Exception {
    assertThat(response.getStatus()).isEqualTo(201);
    return objectMapper.readTree(response.getContentAsString()).get("code").asText();
  }

  // Scenario: Primer asunto del año
  @Test
  void firstMatterOf2026IsAs20260001() throws Exception {
    assertThat(code(postMatter(partnerToken(), "Primer asunto"))).isEqualTo("AS-2026-0001");
  }

  // Scenario: Correlativo siguiente
  @Test
  void nextMatterAfterAs20260041IsAs20260042() throws Exception {
    insertMatter(clientId, "AS-2026-0041", "Último existente", false);
    jdbc.update("INSERT INTO matter_code_seq (year, last_value) VALUES (2026, 41)");

    assertThat(code(postMatter(partnerToken(), "Siguiente"))).isEqualTo("AS-2026-0042");
  }

  // Scenario: Cambio de año según la zona de la firma
  @Test
  void newYearsEveInLaPazStillUses2026() throws Exception {
    jdbc.update("INSERT INTO matter_code_seq (year, last_value) VALUES (2026, 42)");
    clock.set(Instant.parse("2027-01-01T02:00:00Z"));
    // Token emitido con el reloj ya movido: su vigencia se valida con el mismo reloj.
    String token = accessToken(PARTNER_EMAIL);

    assertThat(code(postMatter(token, "Fin de año"))).isEqualTo("AS-2026-0043");
  }

  // Scenario: Altas concurrentes
  @Test
  void concurrentCreationsGetDistinctCodes() throws Exception {
    String partnerA = partnerToken();
    String partnerB = accessToken(PARTNER_EMAIL);
    CountDownLatch start = new CountDownLatch(1);
    List<Callable<MockHttpServletResponse>> tasks = new ArrayList<>();
    for (String token : List.of(partnerA, partnerB, partnerA, partnerB)) {
      tasks.add(
          () -> {
            start.await(10, TimeUnit.SECONDS);
            return postMatter(token, "Alta concurrente");
          });
    }

    List<String> codes = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(tasks.size())) {
      List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
      for (Callable<MockHttpServletResponse> task : tasks) {
        futures.add(pool.submit(task));
      }
      start.countDown();
      for (Future<MockHttpServletResponse> future : futures) {
        codes.add(code(future.get(30, TimeUnit.SECONDS)));
      }
    }

    assertThat(codes)
        .doesNotHaveDuplicates()
        .containsExactlyInAnyOrder("AS-2026-0001", "AS-2026-0002", "AS-2026-0003", "AS-2026-0004");
  }
}
