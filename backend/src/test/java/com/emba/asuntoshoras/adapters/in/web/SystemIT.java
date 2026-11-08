package com.emba.asuntoshoras.adapters.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.emba.asuntoshoras.AbstractIT;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class SystemIT extends AbstractIT {

  @Test
  void featuresRequireAuthentication() throws Exception {
    mvc.perform(get("/api/features"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("unauthenticated"));
  }

  // Scenario: Perfil demo con el asistente encendido
  @Test
  void featuresExposeDescriptionAssistantEnabledInDemo() throws Exception {
    String token = accessToken("abogado1@asuntos.test");

    mvc.perform(get("/api/features").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.descriptionAssistant").value(true));
  }

  @Test
  void readinessAndLivenessAreUpAndPublic() throws Exception {
    mvc.perform(get("/api/actuator/health/readiness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
    mvc.perform(get("/api/actuator/health/liveness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
    mvc.perform(get("/api/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
    mvc.perform(get("/api/actuator/info")).andExpect(status().isOk());
  }

  @Test
  void otherActuatorEndpointsAreNotExposed() throws Exception {
    String token = accessToken("socia@asuntos.test");

    mvc.perform(get("/api/actuator/env").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNotFound());
  }

  @Test
  void unknownApiPathIs404Problem() throws Exception {
    String token = accessToken("socia@asuntos.test");

    mvc.perform(get("/api/no-existe").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("not-found"))
        .andExpect(jsonPath("$.traceId").isNotEmpty());
  }

  @Test
  void wrongMethodIs405Problem() throws Exception {
    mvc.perform(get("/api/auth/login")).andExpect(status().isUnauthorized());
    String token = accessToken("socia@asuntos.test");
    mvc.perform(get("/api/auth/login").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value("method-not-allowed"));
  }

  @Test
  void staticContractAndSwaggerUiArePublic() throws Exception {
    mvc.perform(get("/openapi.yaml"))
        .andExpect(status().isOk())
        .andExpect(content().string(Matchers.containsString("operationId: login")));
    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    mvc.perform(get("/v3/api-docs/swagger-config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").value("/openapi.yaml"));
  }

  @Test
  void codeGeneratedApiDocsAreNotPublic() throws Exception {
    mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
  }
}
