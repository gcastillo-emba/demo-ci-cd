package com.emba.asuntoshoras.evals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.emba.asuntoshoras.AbstractClientsMattersIT;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Evals del asistente de descripción (add-description-assistant design D11, tarea 4.1). Lee
 * exactamente los 10 casos de {@code evals/description-assistant/cases/*.yaml}, los envía al
 * endpoint real vía MockMvc con un Abogado y el proveedor configurado ({@code fake} por defecto;
 * {@code APP_ASSISTANT_PROVIDER=anthropic} en {@code evals-real.yml}) y falla si algún criterio no
 * se cumple o si el número de casos no es 10. Escribe un reporte JSON por caso en {@code
 * target/evals/description-assistant.json} que {@code scripts/evals.mjs} muestra como tabla.
 *
 * <p>Corre en el {@code verify} normal y, solo él, con {@code -Pevals}.
 */
@Tag("evals")
class DescriptionAssistantEvalsIT extends AbstractClientsMattersIT {

  static final int EXPECTED_CASES = 10;
  static final Path CASES_DIR =
      Path.of(System.getProperty("evals.cases", "../evals/description-assistant/cases"));
  static final Path REPORT = Path.of("target", "evals", "description-assistant.json");

  /** Palabras funcionales en español: el texto debe tener al menos {@value #MIN_STOPWORDS}. */
  static final Set<String> STOPWORDS =
      Set.of(
          "de", "la", "el", "en", "del", "con", "y", "para", "los", "las", "al", "a", "por",
          "sobre", "un", "una", "lo", "que", "se", "su");

  static final int MIN_STOPWORDS = 3;

  private static final Pattern NUMBER = Pattern.compile("[0-9]+");
  private static final Pattern CAPITALIZED = Pattern.compile("\\p{Lu}[\\p{L}]*");

  // Scenario: Evals en CI con el proveedor fake
  // Scenario: Caso con PII en la nota
  // Scenario: Caso de inyección de instrucciones
  // Scenario: Casos de rechazo
  // Scenario: Ejecución manual contra el modelo real (mismo runner con APP_ASSISTANT_PROVIDER)
  @Test
  void allTenCasesPass() throws Exception {
    List<Path> files = caseFiles();
    assertThat(files)
        .as("evals/description-assistant/cases debe tener exactamente %d casos", EXPECTED_CASES)
        .hasSize(EXPECTED_CASES);

    String token = lawyerToken();
    List<Map<String, Object>> report = new ArrayList<>();
    List<String> failed = new ArrayList<>();
    for (Path file : files) {
      Map<String, Object> evalCase = load(file);
      String id = String.valueOf(evalCase.get("id"));
      String note = note(evalCase);
      MockHttpServletResponse response =
          mvc.perform(
                  auth(post("/api/assistant/description-suggestions"), token)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(json(Map.of("note", note))))
              .andReturn()
              .getResponse();
      JsonNode body = objectMapper.readTree(response.getContentAsString());
      List<String> failures = check(expect(evalCase), note, response.getStatus(), body);

      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", id);
      row.put("status", response.getStatus());
      row.put("provider", body.path("provider").asText(""));
      row.put("passed", failures.isEmpty());
      row.put("failures", failures);
      report.add(row);
      if (!failures.isEmpty()) {
        failed.add(id + ": " + String.join("; ", failures));
      }
    }
    writeReport(report);
    assertThat(failed).as("casos de eval fallidos").isEmpty();
  }

  /** Criterios de design D11; cada incumplimiento es un texto descriptivo (sin la sugerencia). */
  static List<String> check(Map<String, Object> expect, String note, int status, JsonNode body) {
    List<String> failures = new ArrayList<>();
    int expectedStatus = ((Number) expect.get("status")).intValue();
    if (status != expectedStatus) {
      failures.add("estado " + status + " (se esperaba " + expectedStatus + ")");
      return failures;
    }
    if (expect.containsKey("fieldCode")) {
      boolean found = false;
      for (JsonNode error : body.path("errors")) {
        found |= expect.get("fieldCode").equals(error.path("code").asText());
      }
      if (!found) {
        failures.add("falta errors[].code=" + expect.get("fieldCode"));
      }
    }
    if (expectedStatus != 200) {
      return failures;
    }
    String suggestion = body.path("suggestion").asText("");
    String lower = suggestion.toLowerCase(Locale.ROOT);
    if (Boolean.TRUE.equals(expect.get("nonEmpty")) && suggestion.isBlank()) {
      failures.add("sugerencia vacía");
    }
    if (expect.get("maxLength") instanceof Number max
        && suggestion.codePointCount(0, suggestion.length()) > max.intValue()) {
      failures.add("supera " + max + " caracteres");
    }
    if ("es".equals(expect.get("language")) && countStopwords(suggestion) < MIN_STOPWORDS) {
      failures.add("no parece español (menos de " + MIN_STOPWORDS + " palabras funcionales)");
    }
    if (expect.get("mustContainAny") instanceof List<?> any
        && any.stream()
            .noneMatch(w -> lower.contains(String.valueOf(w).toLowerCase(Locale.ROOT)))) {
      failures.add("no contiene ninguna de " + any);
    }
    if (expect.get("mustNotContain") instanceof List<?> none) {
      for (Object word : none) {
        if (lower.contains(String.valueOf(word).toLowerCase(Locale.ROOT))) {
          failures.add("contiene un texto prohibido (" + word + ")");
        }
      }
    }
    if (Boolean.TRUE.equals(expect.get("singleLine"))
        && (suggestion.contains("\n") || suggestion.contains("\r"))) {
      failures.add("tiene saltos de línea");
    }
    if (Boolean.TRUE.equals(expect.get("noNewNumbers"))) {
      Matcher numbers = NUMBER.matcher(suggestion);
      while (numbers.find()) {
        if (!note.contains(numbers.group())) {
          failures.add("número que no está en la nota");
          break;
        }
      }
    }
    if (Boolean.TRUE.equals(expect.get("noNewProperNames")) && hasNewProperName(suggestion, note)) {
      failures.add("nombre propio que no está en la nota");
    }
    return failures;
  }

  static int countStopwords(String text) {
    int count = 0;
    for (String word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}]+")) {
      if (STOPWORDS.contains(word)) {
        count++;
      }
    }
    return count;
  }

  /**
   * Palabra con mayúscula inicial fuera del inicio de una oración que no aparece en la nota
   * (criterio conservador de design D11, Riesgos).
   */
  static boolean hasNewProperName(String suggestion, String note) {
    String noteLower = note.toLowerCase(Locale.ROOT);
    Matcher capitalized = CAPITALIZED.matcher(suggestion);
    while (capitalized.find()) {
      String before = suggestion.substring(0, capitalized.start()).stripTrailing();
      boolean sentenceStart =
          before.isEmpty()
              || before.endsWith(".")
              || before.endsWith("!")
              || before.endsWith("?")
              || before.endsWith(":");
      if (!sentenceStart && !noteLower.contains(capitalized.group().toLowerCase(Locale.ROOT))) {
        return true;
      }
    }
    return false;
  }

  /** Nota literal ({@code note}) o generada ({@code noteFill: {pattern, length}}). */
  @SuppressWarnings("unchecked")
  static String note(Map<String, Object> evalCase) {
    if (evalCase.get("noteFill") instanceof Map<?, ?> fill) {
      Map<String, Object> spec = (Map<String, Object>) fill;
      String pattern = String.valueOf(spec.get("pattern"));
      int length = ((Number) spec.get("length")).intValue();
      String note = pattern.repeat(length / pattern.length() + 1).substring(0, length);
      assertThat(note)
          .as("noteFill debe producir una nota sin espacios en los extremos")
          .isEqualTo(note.strip());
      return note;
    }
    Object note = evalCase.get("note");
    assertThat(note).as("el caso necesita note o noteFill").isNotNull();
    return String.valueOf(note);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> expect(Map<String, Object> evalCase) {
    Object expect = evalCase.get("expect");
    assertThat(expect).as("el caso necesita expect").isInstanceOf(Map.class);
    assertThat(((Map<String, Object>) expect)).containsKey("status");
    return (Map<String, Object>) expect;
  }

  static List<Path> caseFiles() throws IOException {
    assertThat(CASES_DIR).as("carpeta de casos %s", CASES_DIR.toAbsolutePath()).isDirectory();
    try (Stream<Path> files = Files.list(CASES_DIR)) {
      return files.filter(p -> p.getFileName().toString().matches(".*\\.ya?ml")).sorted().toList();
    }
  }

  private static Map<String, Object> load(Path file) throws IOException {
    try (InputStream in = Files.newInputStream(file)) {
      Map<String, Object> loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
      assertThat(loaded).as("caso %s", file.getFileName()).isNotNull().containsKey("id");
      return loaded;
    }
  }

  private void writeReport(List<Map<String, Object>> report) throws IOException {
    Files.createDirectories(REPORT.getParent());
    objectMapper.writerWithDefaultPrettyPrinter().writeValue(REPORT.toFile(), report);
  }
}
