package com.emba.asuntoshoras.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.read.ListAppender;
import com.emba.asuntoshoras.AbstractAssistantIT;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Tarea 2.9 / requisito "Privacidad en logs" (design D9): con un appender en memoria sobre el
 * logger raíz se buscan la nota, el correo, el teléfono y la sugerencia en todo lo registrado
 * (mensaje, pares clave-valor, MDC y trazas de excepciones).
 */
class AssistantLogPrivacyIT extends AbstractAssistantIT {

  private static final String EMAIL = "ana.perez@example.com";
  private static final String PHONE = "+591 000 00000";

  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
  private Logger root;

  @BeforeEach
  void captureLogs() {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    appender.setContext(context);
    appender.start();
    root.addAppender(appender);
  }

  @AfterEach
  void stopCapturing() {
    root.detachAppender(appender);
    appender.stop();
  }

  // Scenario: Solicitud exitosa sin PII en logs
  @Test
  void successfulRequestLogsOnlyMetadata() throws Exception {
    String suggestion =
        read(suggest(lawyer(), note(CONTACT_NOTE)).andExpect(status().isOk()).andReturn())
            .path("suggestion")
            .asText();

    List<String> lines = lines();
    assertThat(lines)
        .noneMatch(line -> line.contains(CONTACT_NOTE))
        .noneMatch(line -> line.contains(EMAIL))
        .noneMatch(line -> line.contains("ana.perez"))
        .noneMatch(line -> line.contains(PHONE))
        .noneMatch(line -> line.contains(suggestion));
    assertThat(lines)
        .anyMatch(
            line ->
                line.contains("assistant.note_length=" + CONTACT_NOTE.length())
                    && line.contains("assistant.provider=fake")
                    && line.contains("assistant.latency_ms=")
                    && line.contains("assistant.suggestion_length=" + suggestion.length())
                    && line.contains("assistant.outcome=ok"));
  }

  // Scenario: Solicitud fallida sin PII en logs
  @Test
  void timedOutRequestDoesNotLogTheNote() throws Exception {
    provider.blocking("Respuesta tardía con " + EMAIL);

    suggest(lawyer(), note(CONTACT_NOTE)).andExpect(status().isServiceUnavailable());

    List<String> lines = lines();
    assertThat(lines)
        .noneMatch(line -> line.contains(CONTACT_NOTE))
        .noneMatch(line -> line.contains("ana.perez"))
        .noneMatch(line -> line.contains(PHONE));
    assertThat(lines).anyMatch(line -> line.contains("assistant.outcome=unavailable"));
  }

  @Test
  void failingProviderMessageIsNeverLogged() throws Exception {
    provider.failing();

    suggest(lawyer(), note(CONTACT_NOTE)).andExpect(status().isServiceUnavailable());

    assertThat(lines())
        .noneMatch(line -> line.contains("ana.perez"))
        .noneMatch(line -> line.contains("proveedor caído"))
        .anyMatch(line -> line.contains("assistant.outcome=unavailable"));
  }

  @Test
  void rejectedNoteIsNeverLogged() throws Exception {
    String longNote = CONTACT_NOTE + " " + "z".repeat(500);

    suggest(lawyer(), note(longNote)).andExpect(status().isBadRequest());

    assertThat(lines())
        .noneMatch(line -> line.contains("ana.perez"))
        .noneMatch(line -> line.contains("zzzzzzzzzz"))
        .anyMatch(line -> line.contains("assistant.outcome=validation"));
  }

  /** Todo lo registrado: mensaje, pares clave-valor, MDC y trazas completas. */
  private List<String> lines() {
    List<String> lines = new ArrayList<>();
    for (ILoggingEvent event : List.copyOf(appender.list)) {
      StringBuilder line = new StringBuilder(event.getFormattedMessage());
      if (event.getKeyValuePairs() != null) {
        event
            .getKeyValuePairs()
            .forEach(kv -> line.append(' ').append(kv.key).append('=').append(kv.value));
      }
      event.getMDCPropertyMap().forEach((k, v) -> line.append(' ').append(k).append('=').append(v));
      for (IThrowableProxy t = event.getThrowableProxy(); t != null; t = t.getCause()) {
        line.append(' ').append(t.getClassName()).append(": ").append(t.getMessage());
        for (StackTraceElementProxy frame : t.getStackTraceElementProxyArray()) {
          line.append(' ').append(frame.getSTEAsString());
        }
      }
      lines.add(line.toString());
    }
    return lines;
  }
}
