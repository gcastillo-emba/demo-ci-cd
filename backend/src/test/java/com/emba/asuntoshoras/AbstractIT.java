package com.emba.asuntoshoras;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base de los IT: contexto completo, perfil {@code demo} (seed sintético + secreto JWT falso) y
 * PostgreSQL 17 en Testcontainers. Todas las subclases comparten el mismo contexto cacheado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIT {

  protected static final String DEMO_PASSWORD = "Demo2026!";
  protected static final String PARTNER_EMAIL = "socia@asuntos.test";
  protected static final String PARTNER_ID = "00000000-0000-4000-8000-000000000001";

  @Autowired protected MockMvc mvc;
  @Autowired protected ObjectMapper objectMapper;

  protected String loginBody(String email, String password) throws Exception {
    return objectMapper.writeValueAsString(java.util.Map.of("email", email, "password", password));
  }

  /** Inicia sesión con un usuario de demo y devuelve el access token. */
  protected String accessToken(String email) throws Exception {
    String json =
        mvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginBody(email, DEMO_PASSWORD)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode node = objectMapper.readTree(json);
    return node.get("accessToken").asText();
  }
}
