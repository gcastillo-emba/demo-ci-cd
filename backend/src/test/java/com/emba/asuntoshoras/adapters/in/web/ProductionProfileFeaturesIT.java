package com.emba.asuntoshoras.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.emba.asuntoshoras.TestcontainersConfiguration;
import com.emba.asuntoshoras.adapters.out.assistant.fake.FakeDescriptionAssistant;
import com.emba.asuntoshoras.application.port.out.DescriptionAssistant;
import com.emba.asuntoshoras.application.port.out.TokenIssuer;
import com.emba.asuntoshoras.domain.user.Email;
import com.emba.asuntoshoras.domain.user.Role;
import com.emba.asuntoshoras.domain.user.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tarea 2.7: perfil de producción (sin perfil {@code demo} ni configuración adicional del
 * asistente). Sin seed de usuarios: el token se emite con el {@link TokenIssuer} real para un
 * usuario sintético. Solo se fija el secreto JWT, obligatorio fuera de demo (ADR-006).
 */
@SpringBootTest(properties = "app.jwt.secret=secreto-de-prueba-solo-para-it-0123456789abcdef")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProductionProfileFeaturesIT {

  @Autowired private MockMvc mvc;
  @Autowired private TokenIssuer tokenIssuer;
  @Autowired private DescriptionAssistant assistant;

  private String token(Role role) {
    User user =
        User.restore(
            UUID.randomUUID(),
            Email.of("sintetico@asuntos.test"),
            "Usuario Sintético",
            "hash-falso",
            role,
            true);
    return tokenIssuer.issue(user).value();
  }

  // Scenario: Perfil de producción con el asistente apagado
  @Test
  void productionProfileHasTheAssistantOff() throws Exception {
    mvc.perform(
            get("/api/features").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(Role.LAWYER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.descriptionAssistant").value(false));
  }

  @Test
  void productionEndpointIs404FeatureDisabledAndProviderIsFake() throws Exception {
    mvc.perform(
            post("/api/assistant/description-suggestions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(Role.LAWYER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"llamada con cliente\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("feature-disabled"));
    assertThat(assistant).isInstanceOf(FakeDescriptionAssistant.class);
  }
}
