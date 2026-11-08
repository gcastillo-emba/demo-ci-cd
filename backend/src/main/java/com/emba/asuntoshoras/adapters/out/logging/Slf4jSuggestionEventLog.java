package com.emba.asuntoshoras.adapters.out.logging;

import com.emba.asuntoshoras.application.port.out.SuggestionEventLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evento ECS por solicitud de sugerencia (design D9) con {@code assistant.note_length}, {@code
 * assistant.suggestion_length}, {@code assistant.provider}, {@code assistant.latency_ms} y {@code
 * assistant.outcome}. Solo metadatos: el puerto nunca recibe la nota ni la sugerencia.
 */
public class Slf4jSuggestionEventLog implements SuggestionEventLog {

  private static final Logger log = LoggerFactory.getLogger("assistant");

  @Override
  public void record(SuggestionEvent event) {
    log.atInfo()
        .addKeyValue("assistant.note_length", event.noteLength())
        .addKeyValue("assistant.suggestion_length", event.suggestionLength())
        .addKeyValue("assistant.provider", event.provider())
        .addKeyValue("assistant.latency_ms", event.latencyMs())
        .addKeyValue("assistant.outcome", event.outcome().value())
        .log(
            "Sugerencia de descripción outcome={} provider={} note_length={}"
                + " suggestion_length={} latency_ms={}",
            event.outcome().value(),
            event.provider(),
            event.noteLength(),
            event.suggestionLength(),
            event.latencyMs());
  }
}
