package com.emba.asuntoshoras.adapters.out.assistant.anthropic;

import com.emba.asuntoshoras.application.port.out.DescriptionAssistant;
import com.emba.asuntoshoras.domain.assistant.AssistantPrompt;
import com.emba.asuntoshoras.domain.assistant.AssistantUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adaptador {@code anthropic} (design D4): Messages API con {@code java.net.http.HttpClient}, sin
 * SDK ni dependencias nuevas. Solo se construye con {@code app.assistant.provider=anthropic}.
 *
 * <ul>
 *   <li>Instrucciones fijas en {@code system}; la nota delimitada y escapada en el mensaje {@code
 *       user} ({@link AssistantPrompt#user()}, design D13).
 *   <li>Clave desde la variable de entorno {@code ANTHROPIC_API_KEY}: si falta, cada llamada lanza
 *       {@link AssistantUnavailableException} ({@code 503}) sin contactar a la red.
 *   <li>{@code connectTimeout} y {@code request.timeout} iguales al timeout del asistente.
 *   <li>Errores (HTTP no 2xx, rechazo del modelo, JSON inesperado, E/S) → {@link
 *       AssistantUnavailableException} sin el cuerpo ni el mensaje del proveedor (design D9).
 * </ul>
 */
public final class AnthropicDescriptionAssistant implements DescriptionAssistant {

  public static final String PROVIDER = "anthropic";
  static final String MESSAGES_PATH = "/v1/messages";
  static final String API_VERSION = "2026-10-21";

  private static final Logger log = LoggerFactory.getLogger(AnthropicDescriptionAssistant.class);

  private final HttpClient http;
  private final ObjectMapper json;
  private final Settings settings;
  private final String apiKey;

  /** Configuración del adaptador ({@code app.assistant.anthropic.*} y el timeout). */
  public record Settings(URI baseUrl, String model, int maxTokens, Duration timeout) {

    public Settings {
      Objects.requireNonNull(baseUrl, "baseUrl");
      Objects.requireNonNull(model, "model");
      Objects.requireNonNull(timeout, "timeout");
      if (maxTokens < 1) {
        throw new IllegalArgumentException("max-tokens debe ser positivo");
      }
    }
  }

  public AnthropicDescriptionAssistant(ObjectMapper json, Settings settings, String apiKey) {
    this(
        HttpClient.newBuilder()
            .connectTimeout(settings.timeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build(),
        json,
        settings,
        apiKey);
  }

  AnthropicDescriptionAssistant(
      HttpClient http, ObjectMapper json, Settings settings, String apiKey) {
    this.http = Objects.requireNonNull(http);
    this.json = Objects.requireNonNull(json);
    this.settings = Objects.requireNonNull(settings);
    this.apiKey = apiKey == null || apiKey.isBlank() ? null : apiKey.strip();
    if (this.apiKey == null) {
      log.warn(
          "ANTHROPIC_API_KEY no está definida: el asistente con el proveedor anthropic responderá"
              + " 503 assistant-unavailable");
    }
  }

  @Override
  public String suggest(AssistantPrompt prompt) {
    if (apiKey == null) {
      throw new AssistantUnavailableException();
    }
    HttpResponse<String> response;
    try {
      response = http.send(request(prompt), HttpResponse.BodyHandlers.ofString());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssistantUnavailableException();
    } catch (IOException | RuntimeException e) {
      log.warn("El proveedor anthropic no respondió (tipo={})", e.getClass().getSimpleName());
      throw new AssistantUnavailableException();
    }
    if (response.statusCode() / 100 != 2) {
      log.warn("El proveedor anthropic respondió con estado HTTP {}", response.statusCode());
      throw new AssistantUnavailableException();
    }
    return text(response.body());
  }

  @Override
  public String providerName() {
    return PROVIDER;
  }

  private HttpRequest request(AssistantPrompt prompt) throws IOException {
    ObjectNode body = json.createObjectNode();
    body.put("model", settings.model());
    body.put("max_tokens", settings.maxTokens());
    body.put("system", prompt.system());
    ObjectNode message = body.putArray("messages").addObject();
    message.put("role", "user");
    message.put("content", prompt.user());
    // Tarea breve de reformulación: esfuerzo bajo para que el pensamiento no consuma max_tokens.
    body.putObject("output_config").put("effort", "low");
    return HttpRequest.newBuilder(settings.baseUrl().resolve(MESSAGES_PATH))
        .timeout(settings.timeout())
        .header("content-type", "application/json")
        .header("x-api-key", apiKey)
        .header("anthropic-version", API_VERSION)
        .POST(
            HttpRequest.BodyPublishers.ofString(
                json.writeValueAsString(body), StandardCharsets.UTF_8))
        .build();
  }

  /** Concatena los bloques {@code text}; un rechazo o un cuerpo inesperado → no disponible. */
  private String text(String body) {
    JsonNode root;
    try {
      root = json.readTree(body);
    } catch (IOException e) {
      log.warn("El proveedor anthropic devolvió un cuerpo que no es JSON");
      throw new AssistantUnavailableException();
    }
    if (root == null || "refusal".equals(root.path("stop_reason").asText())) {
      log.warn("El proveedor anthropic no generó una sugerencia (stop_reason=refusal)");
      throw new AssistantUnavailableException();
    }
    JsonNode content = root.path("content");
    if (!content.isArray()) {
      log.warn("El proveedor anthropic devolvió una respuesta sin content");
      throw new AssistantUnavailableException();
    }
    StringBuilder text = new StringBuilder();
    for (JsonNode block : content) {
      if ("text".equals(block.path("type").asText())) {
        text.append(block.path("text").asText(""));
      }
    }
    return text.toString();
  }
}
