package com.emba.asuntoshoras.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.emba.asuntoshoras.AbstractAssistantIT;
import org.junit.jupiter.api.Test;

/**
 * Tarea 2.11: indicador y precedencia {@code 401 > 404 feature-disabled > 403 > 400 > 404 not-found
 * > 503} (design D1/D2) con {@code FeatureGateFilter} y la regla por URL. En ningún caso se invoca
 * al proveedor.
 */
class AssistantPrecedenceIT extends AbstractAssistantIT {

  // Scenario: Llamada con el indicador apagado
  @Test
  void flagOffIs404FeatureDisabledForTheLawyer() throws Exception {
    flags.setDescriptionAssistant(false);

    expectProblem(suggest(lawyer(), note("llamada con cliente")), 404, "feature-disabled")
        .andExpect(jsonPath("$.type").value(PROBLEM_TYPE + "feature-disabled"))
        .andExpect(jsonPath("$.title").value("Funcionalidad no disponible"))
        .andExpect(jsonPath("$.detail").value("El asistente de descripción no está habilitado."))
        .andExpect(jsonPath("$.suggestion").doesNotExist());
    assertThat(provider.invocations()).isZero();
  }

  // Scenario: Socio con el indicador apagado
  @Test
  void flagOffIs404FeatureDisabledForThePartnerNot403() throws Exception {
    flags.setDescriptionAssistant(false);

    expectProblem(suggest(partner(), note("revision de escritura")), 404, "feature-disabled");
    assertThat(provider.invocations()).isZero();
  }

  @Test
  void flagOffPrecedesValidationAndMatterChecks() throws Exception {
    flags.setDescriptionAssistant(false);

    expectProblem(suggest(lawyer(), note("", "no-es-un-uuid")), 404, "feature-disabled");
    expectProblem(suggestRaw(lawyer(), "{\"note\":"), 404, "feature-disabled");
    assertThat(provider.invocations()).isZero();
  }

  @Test
  void withoutTokenIs401EvenWithTheFlagOff() throws Exception {
    flags.setDescriptionAssistant(false);

    expectProblem(suggest(null, note("llamada con cliente")), 401, "unauthenticated");
    expectProblem(suggest("token-invalido", note("llamada")), 401, "unauthenticated");
  }

  @Test
  void partnerWithInvalidBodyIs403NotValidation() throws Exception {
    expectProblem(suggest(partner(), note("redaccion", "no-es-un-uuid")), 403, "forbidden");
    expectProblem(suggest(partner(), note("")), 403, "forbidden");
    expectProblem(suggestRaw(partner(), "{\"note\":"), 403, "forbidden");
    assertThat(provider.invocations()).isZero();
  }

  @Test
  void lawyerWithInvalidMatterIdIs400() throws Exception {
    expectProblem(suggest(lawyer(), note("redaccion", "no-es-un-uuid")), 400, "validation-error")
        .andExpect(jsonPath("$.errors[0].field").value("matterId"));
    assertThat(provider.invocations()).isZero();
  }

  @Test
  void validationPrecedesUnknownMatter() throws Exception {
    expectProblem(suggest(lawyer(), note("", java.util.UUID.randomUUID())), 400, "validation-error")
        .andExpect(jsonPath("$.errors[0].code").value("assistant-note-empty"));
  }

  @Test
  void unknownMatterPrecedesTheProvider() throws Exception {
    provider.failing();

    expectProblem(
        suggest(lawyer(), note("redaccion", java.util.UUID.randomUUID())), 404, "not-found");
    assertThat(provider.invocations()).isZero();
  }

  @Test
  void otherMethodsAreNotGatedAndStay405() throws Exception {
    flags.setDescriptionAssistant(false);

    expectProblem(mvc.perform(auth(get(SUGGESTIONS), lawyer())), 405, "method-not-allowed");
  }

  @Test
  void featuresReflectTheFlagForAnyAuthenticatedUser() throws Exception {
    flags.setDescriptionAssistant(false);
    mvc.perform(auth(get("/api/features"), partner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.descriptionAssistant").value(false));

    flags.setDescriptionAssistant(true);
    mvc.perform(auth(get("/api/features"), lawyer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.descriptionAssistant").value(true));
  }
}
