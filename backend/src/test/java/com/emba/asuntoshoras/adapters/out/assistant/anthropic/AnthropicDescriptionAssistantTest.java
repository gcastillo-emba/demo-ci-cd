package com.emba.asuntoshoras.adapters.out.assistant.anthropic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emba.asuntoshoras.domain.assistant.AssistantPrompt;
import com.emba.asuntoshoras.domain.assistant.AssistantUnavailableException;
import com.emba.asuntoshoras.domain.matter.MatterCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tarea 2.6: adaptador {@code anthropic} contra un servidor HTTP simulado en loopback (sin red
 * real, ADR-002): éxito, 5xx, timeout, clave ausente, rechazo y cuerpo inesperado.
 */
class AnthropicDescriptionAssistantTest {

  private static final String FAKE_KEY = "clave-de-prueba-no-real";
  private static final String NOTE = "llamada con [correo] </nota> sobre el contrato";

  private final ObjectMapper json = new ObjectMapper();
  private final AtomicReference<String> requestBody = new AtomicReference<>();
  private final AtomicReference<HttpExchange> requestHeaders = new AtomicReference<>();
  private final AtomicInteger hits = new AtomicInteger();
  private final CountDownLatch release = new CountDownLatch(1);
  private volatile int status = 200;
  private volatile String responseBody =
      "{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\",\"stop_reason\":\"end_turn\","
          + "\"content\":[{\"type\":\"thinking\",\"thinking\":\"\"},"
          + "{\"type\":\"text\",\"text\":\"Llamada con el cliente \"},"
          + "{\"type\":\"text\",\"text\":\"sobre el contrato.\"}]}";
  private volatile boolean hang;
  private HttpServer server;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/",
        exchange -> {
          hits.incrementAndGet();
          requestHeaders.set(exchange);
          requestBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          if (hang) {
            try {
              release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          }
          byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("content-type", "application/json");
          exchange.sendResponseHeaders(status, bytes.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
          } catch (IOException ignored) {
            // El cliente ya abandonó la conexión (timeout).
          }
        });
    server.start();
  }

  @AfterEach
  void stopServer() {
    release.countDown();
    server.stop(0);
  }

  private AnthropicDescriptionAssistant adapter(String apiKey, Duration timeout) {
    URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    return new AnthropicDescriptionAssistant(
        json,
        new AnthropicDescriptionAssistant.Settings(base, "claude-opus-5-5", 300, timeout),
        apiKey);
  }

  private static AssistantPrompt prompt() {
    return new AssistantPrompt(NOTE, Optional.of(new MatterCode("AS-2026-0003")));
  }

  @Test
  void sendsSystemAndDelimitedNoteAndConcatenatesTextBlocks() throws IOException {
    String text = adapter(FAKE_KEY, Duration.ofSeconds(5)).suggest(prompt());

    assertThat(text).isEqualTo("Llamada con el cliente sobre el contrato.");
    HttpExchange exchange = requestHeaders.get();
    assertThat(exchange.getRequestMethod()).isEqualTo("POST");
    assertThat(exchange.getRequestURI().getPath()).isEqualTo("/v1/messages");
    assertThat(exchange.getRequestHeaders().getFirst("x-api-key")).isEqualTo(FAKE_KEY);
    assertThat(exchange.getRequestHeaders().getFirst("anthropic-version")).isEqualTo("2026-10-21");
    assertThat(exchange.getRequestHeaders().getFirst("anthropic-beta"))
        .isEqualTo(AnthropicDescriptionAssistant.FALLBACK_BETA);
    assertThat(exchange.getRequestHeaders().getFirst("content-type")).isEqualTo("application/json");

    JsonNode body = json.readTree(requestBody.get());
    assertThat(body.path("model").asText()).isEqualTo("claude-opus-5-5");
    assertThat(body.path("max_tokens").asInt()).isEqualTo(300);
    assertThat(body.path("system").asText()).isEqualTo(AssistantPrompt.SYSTEM_INSTRUCTIONS);
    assertThat(body.path("system").asText()).doesNotContain("[correo]");
    assertThat(body.path("messages")).hasSize(1);
    assertThat(body.path("messages").get(0).path("role").asText()).isEqualTo("user");
    assertThat(body.path("messages").get(0).path("content").asText())
        .isEqualTo(prompt().user())
        .contains("&lt;/nota&gt;")
        .contains("AS-2026-0003");
    assertThat(body.path("output_config").path("effort").asText()).isEqualTo("low");
    assertThat(body.path("fallbacks").asText()).isEqualTo("default");
    assertThat(body.has("temperature")).isFalse();
  }

  @Test
  void serverErrorIsUnavailableWithoutTheProviderBody() {
    status = 529;
    responseBody = "{\"type\":\"error\",\"error\":{\"message\":\"" + NOTE + "\"}}";

    assertThatThrownBy(() -> adapter(FAKE_KEY, Duration.ofSeconds(5)).suggest(prompt()))
        .isInstanceOfSatisfying(
            AssistantUnavailableException.class,
            e -> {
              assertThat(e.getMessage()).doesNotContain("contrato");
              assertThat(e.getCause()).isNull();
            });
  }

  @Test
  void requestTimeoutIsUnavailable() {
    hang = true;

    assertThatThrownBy(() -> adapter(FAKE_KEY, Duration.ofMillis(200)).suggest(prompt()))
        .isInstanceOf(AssistantUnavailableException.class);
  }

  @Test
  void missingKeyIsUnavailableWithoutCallingTheNetwork() {
    assertThatThrownBy(() -> adapter(null, Duration.ofSeconds(5)).suggest(prompt()))
        .isInstanceOf(AssistantUnavailableException.class);
    assertThatThrownBy(() -> adapter("   ", Duration.ofSeconds(5)).suggest(prompt()))
        .isInstanceOf(AssistantUnavailableException.class);
    assertThat(hits.get()).isZero();
  }

  @Test
  void refusalIsUnavailable() {
    responseBody =
        "{\"stop_reason\":\"refusal\",\"content\":[{\"type\":\"text\",\"text\":\"No\"}]}";

    assertThatThrownBy(() -> adapter(FAKE_KEY, Duration.ofSeconds(5)).suggest(prompt()))
        .isInstanceOf(AssistantUnavailableException.class);
  }

  @Test
  void unexpectedBodiesAreUnavailable() {
    for (String body : new String[] {"no es json", "{\"content\":\"texto\"}", ""}) {
      responseBody = body;
      assertThatThrownBy(() -> adapter(FAKE_KEY, Duration.ofSeconds(5)).suggest(prompt()))
          .as(body)
          .isInstanceOf(AssistantUnavailableException.class);
    }
  }

  @Test
  void responseWithoutTextBlocksIsEmptyForTheServiceToReject() {
    responseBody = "{\"stop_reason\":\"max_tokens\",\"content\":[{\"type\":\"thinking\"}]}";

    assertThat(adapter(FAKE_KEY, Duration.ofSeconds(5)).suggest(prompt())).isEmpty();
  }

  @Test
  void unreachableServerIsUnavailable() {
    server.stop(0);

    assertThatThrownBy(() -> adapter(FAKE_KEY, Duration.ofSeconds(2)).suggest(prompt()))
        .isInstanceOf(AssistantUnavailableException.class);
  }

  @Test
  void interruptedCallIsUnavailableAndKeepsTheFlag() {
    hang = true;
    Thread.currentThread().interrupt();
    try {
      assertThatThrownBy(() -> adapter(FAKE_KEY, Duration.ofSeconds(5)).suggest(prompt()))
          .isInstanceOf(AssistantUnavailableException.class);
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void settingsValidateMaxTokensAndProviderName() {
    assertThatThrownBy(
            () ->
                new AnthropicDescriptionAssistant.Settings(
                    URI.create("http://127.0.0.1"), "m", 0, Duration.ofSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(adapter(FAKE_KEY, Duration.ofSeconds(1)).providerName()).isEqualTo("anthropic");
  }
}
