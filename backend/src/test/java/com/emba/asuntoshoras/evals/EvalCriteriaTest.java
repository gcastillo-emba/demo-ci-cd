package com.emba.asuntoshoras.evals;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Los criterios de las evals detectan incumplimientos (un eval que siempre pasa no protege). */
class EvalCriteriaTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  private static JsonNode suggestion(String text) {
    return JSON.createObjectNode().put("suggestion", text).put("provider", "fake");
  }

  @Test
  void passesAGoodSpanishSuggestion() {
    Map<String, Object> expect =
        Map.of(
            "status",
            200,
            "nonEmpty",
            true,
            "maxLength",
            300,
            "language",
            "es",
            "mustContainAny",
            List.of("reunión"),
            "noNewNumbers",
            true,
            "noNewProperNames",
            true);

    assertThat(
            DescriptionAssistantEvalsIT.check(
                expect,
                "reunion cliente",
                200,
                suggestion("Reunión con el cliente. Revisión del contrato.")))
        .isEmpty();
  }

  @Test
  void detectsEveryViolation() {
    Map<String, Object> expect =
        Map.of(
            "status",
            200,
            "nonEmpty",
            true,
            "maxLength",
            20,
            "language",
            "es",
            "mustContainAny",
            List.of("audiencia"),
            "mustNotContain",
            List.of("poema"),
            "singleLine",
            true,
            "noNewNumbers",
            true,
            "noNewProperNames",
            true);

    List<String> failures =
        DescriptionAssistantEvalsIT.check(
            expect,
            "nota sin datos",
            200,
            suggestion("Here is a poema\nabout Pedro paying 1500 dollars"));

    assertThat(failures).hasSize(7);
  }

  @Test
  void detectsWrongStatusAndMissingFieldCode() {
    Map<String, Object> rejected = Map.of("status", 400, "fieldCode", "assistant-note-empty");

    assertThat(DescriptionAssistantEvalsIT.check(rejected, "", 200, suggestion("x")))
        .containsExactly("estado 200 (se esperaba 400)");
    JsonNode problem =
        JSON.createObjectNode()
            .set("errors", JSON.createArrayNode().add(JSON.createObjectNode().put("code", "otro")));
    assertThat(DescriptionAssistantEvalsIT.check(rejected, "", 400, problem))
        .containsExactly("falta errors[].code=assistant-note-empty");
    assertThat(
            DescriptionAssistantEvalsIT.check(
                Map.of("status", 200, "nonEmpty", true), "n", 200, suggestion(" ")))
        .containsExactly("sugerencia vacía");
  }

  @Test
  void noteFillBuildsExactLengths() {
    String note =
        DescriptionAssistantEvalsIT.note(
            Map.of("noteFill", Map.of("pattern", "revision contrato ", "length", 501)));

    assertThat(note).hasSize(501).isEqualTo(note.strip());
  }
}
