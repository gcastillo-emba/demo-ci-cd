package com.emba.asuntoshoras.application.service;

import com.emba.asuntoshoras.application.policy.PiiRedactor;
import com.emba.asuntoshoras.application.policy.SuggestionPolicy;
import com.emba.asuntoshoras.application.port.in.CurrentUser;
import com.emba.asuntoshoras.application.port.in.MatterView;
import com.emba.asuntoshoras.application.port.in.SuggestDescriptionUseCase;
import com.emba.asuntoshoras.application.port.out.DescriptionAssistant;
import com.emba.asuntoshoras.application.port.out.FeatureFlags;
import com.emba.asuntoshoras.application.port.out.MatterRepository;
import com.emba.asuntoshoras.application.port.out.SuggestionEventLog;
import com.emba.asuntoshoras.application.port.out.SuggestionEventLog.Outcome;
import com.emba.asuntoshoras.application.port.out.SuggestionEventLog.SuggestionEvent;
import com.emba.asuntoshoras.domain.assistant.AssistantNote;
import com.emba.asuntoshoras.domain.assistant.AssistantPrompt;
import com.emba.asuntoshoras.domain.assistant.AssistantUnavailableException;
import com.emba.asuntoshoras.domain.assistant.FeatureDisabledException;
import com.emba.asuntoshoras.domain.matter.MatterCode;
import com.emba.asuntoshoras.domain.matter.MatterNotFoundException;
import com.emba.asuntoshoras.domain.shared.DomainValidationException;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Sugerencia de descripción (add-description-assistant, design D1, D2, D6, D7, D9, D12 y D13).
 *
 * <p>Orden: indicador ({@code 404 feature-disabled}) → rol {@code LAWYER} ({@code 403}) → nota
 * ({@code 400}) → asunto ({@code 404 not-found}) → redacción de la nota → prompt delimitado →
 * proveedor con timeout ({@code 503}) → redacción y normalización de la salida → un evento de log
 * con metadatos. El indicador y el rol también los resuelve la cadena de seguridad por URL; aquí se
 * conservan como defensa en profundidad. Nada se persiste.
 */
public class DescriptionSuggestionService implements SuggestDescriptionUseCase {

  private final FeatureFlags featureFlags;
  private final MatterRepository matters;
  private final DescriptionAssistant assistant;
  private final SuggestionEventLog eventLog;
  private final ExecutorService executor;
  private final Duration timeout;
  private final Clock clock;
  private final PiiRedactor redactor = new PiiRedactor();
  private final SuggestionPolicy policy = new SuggestionPolicy();

  public DescriptionSuggestionService(
      FeatureFlags featureFlags,
      MatterRepository matters,
      DescriptionAssistant assistant,
      SuggestionEventLog eventLog,
      ExecutorService executor,
      Duration timeout,
      Clock clock) {
    this.featureFlags = Objects.requireNonNull(featureFlags);
    this.matters = Objects.requireNonNull(matters);
    this.assistant = Objects.requireNonNull(assistant);
    this.eventLog = Objects.requireNonNull(eventLog);
    this.executor = Objects.requireNonNull(executor);
    this.timeout = Objects.requireNonNull(timeout);
    if (timeout.isNegative() || timeout.isZero()) {
      throw new IllegalArgumentException("El timeout del asistente debe ser positivo");
    }
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public Suggestion suggest(CurrentUser user, SuggestionRequest request) {
    long start = clock.millis();
    String rawNote = request == null ? null : request.note();
    int noteLength = rawNote == null ? 0 : AssistantNote.codePoints(rawNote.strip());
    String provider = assistant.providerName();
    Outcome outcome = Outcome.UNAVAILABLE;
    int suggestionLength = 0;
    try {
      if (!featureFlags.descriptionAssistantEnabled()) {
        outcome = Outcome.DISABLED;
        throw new FeatureDisabledException();
      }
      outcome = Outcome.FORBIDDEN;
      Authorization.requireLawyer(user);
      outcome = Outcome.UNAVAILABLE;
      AssistantNote note = AssistantNote.of(rawNote);
      Optional<MatterCode> matterCode = matterCode(request.matterId());
      AssistantPrompt prompt =
          new AssistantPrompt(
              redactor.redact(SuggestionPolicy.normalizeWhitespace(note.value())), matterCode);
      String generated = invokeWithTimeout(prompt);
      if (generated == null) {
        throw new AssistantUnavailableException();
      }
      // Normalizar ANTES de redactar: un teléfono con espacios dobles, \n o \t no debe escapar.
      String suggestion =
          policy.apply(redactor.redact(SuggestionPolicy.normalizeWhitespace(generated)));
      suggestionLength = AssistantNote.codePoints(suggestion);
      outcome = Outcome.OK;
      return new Suggestion(suggestion, provider);
    } catch (DomainValidationException e) {
      outcome = Outcome.VALIDATION;
      throw e;
    } catch (MatterNotFoundException e) {
      outcome = Outcome.NOT_FOUND;
      throw e;
    } finally {
      eventLog.record(
          new SuggestionEvent(
              noteLength, suggestionLength, provider, clock.millis() - start, outcome));
    }
  }

  private Optional<MatterCode> matterCode(UUID matterId) {
    if (matterId == null) {
      return Optional.empty();
    }
    MatterView view =
        matters.findById(matterId).orElseThrow(() -> new MatterNotFoundException(matterId));
    return Optional.of(view.matter().code());
  }

  /**
   * Invoca al proveedor en el executor acotado y espera como máximo {@code timeout}. Timeout,
   * excepción del proveedor o rechazo del executor → {@link AssistantUnavailableException}, sin
   * conservar el mensaje ni la causa del proveedor (podrían contener la nota, design D9).
   */
  private String invokeWithTimeout(AssistantPrompt prompt) {
    Future<String> future;
    try {
      future = executor.submit(() -> assistant.suggest(prompt));
    } catch (RejectedExecutionException e) {
      throw new AssistantUnavailableException();
    }
    try {
      return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      future.cancel(true);
      Thread.currentThread().interrupt();
      throw new AssistantUnavailableException();
    } catch (TimeoutException | ExecutionException e) {
      future.cancel(true);
      throw new AssistantUnavailableException();
    }
  }
}
