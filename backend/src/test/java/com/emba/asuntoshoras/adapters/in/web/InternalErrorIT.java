package com.emba.asuntoshoras.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.emba.asuntoshoras.AbstractControlledIT;
import com.emba.asuntoshoras.ControlledBeansConfiguration.FailingClientRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Requisito "Errores sin detalles internos" (compartido por toda la API). */
class InternalErrorIT extends AbstractControlledIT {

  // Scenario: Error inesperado de persistencia
  @Test
  void unexpectedPersistenceFailureIs500WithoutInternalDetails() throws Exception {
    failingClients.failNextInsert();

    String body =
        mvc.perform(
                auth(post("/api/clients"), partnerToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("name", "Comercial Illimani"))))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("internal-error"))
            .andExpect(jsonPath("$.type").value(PROBLEM_TYPE + "internal-error"))
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.traceId").isNotEmpty())
            .andExpect(
                jsonPath("$.detail")
                    .value(
                        "Ocurrió un error inesperado. Si persiste, informe el identificador de"
                            + " traza."))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body)
        .doesNotContain(FailingClientRepository.LEAKY_MESSAGE)
        .doesNotContainIgnoringCase("exception")
        .doesNotContainIgnoringCase("insert into")
        .doesNotContainIgnoringCase("sql")
        .doesNotContain("PSQL")
        .doesNotContain("terminating connection")
        .doesNotContain("org.")
        .doesNotContain("java.")
        .doesNotContain("\tat ")
        .doesNotContain("trace\"");
    assertThat(countClients()).isZero();
  }
}
