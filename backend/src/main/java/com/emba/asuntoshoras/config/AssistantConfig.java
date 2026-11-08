package com.emba.asuntoshoras.config;

import com.emba.asuntoshoras.adapters.out.assistant.anthropic.AnthropicDescriptionAssistant;
import com.emba.asuntoshoras.adapters.out.assistant.fake.FakeDescriptionAssistant;
import com.emba.asuntoshoras.adapters.out.logging.Slf4jSuggestionEventLog;
import com.emba.asuntoshoras.application.port.in.SuggestDescriptionUseCase;
import com.emba.asuntoshoras.application.port.out.DescriptionAssistant;
import com.emba.asuntoshoras.application.port.out.FeatureFlags;
import com.emba.asuntoshoras.application.port.out.MatterRepository;
import com.emba.asuntoshoras.application.port.out.SuggestionEventLog;
import com.emba.asuntoshoras.application.service.DescriptionSuggestionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Wiring del asistente de descripción (add-description-assistant D4–D6, D9). El adaptador se elige
 * con {@code app.assistant.provider} ({@code fake} por defecto | {@code anthropic}); el adaptador
 * real solo se construye cuando se lo pide.
 */
@Configuration(proxyBeanMethods = false)
public class AssistantConfig {

  static final String PROVIDER_PROPERTY = "app.assistant.provider";
  static final String API_KEY_VARIABLE = "ANTHROPIC_API_KEY";

  /** Hilos que esperan al proveedor: acotados, con cola corta; si se llena → 503. */
  static final int MAX_THREADS = 8;

  static final int QUEUE_CAPACITY = 32;

  @Bean
  @ConditionalOnProperty(name = PROVIDER_PROPERTY, havingValue = "fake", matchIfMissing = true)
  DescriptionAssistant fakeDescriptionAssistant() {
    return new FakeDescriptionAssistant();
  }

  @Bean
  @ConditionalOnProperty(name = PROVIDER_PROPERTY, havingValue = "anthropic")
  DescriptionAssistant anthropicDescriptionAssistant(
      ObjectMapper objectMapper, AppProperties properties, Environment environment) {
    AppProperties.Anthropic anthropic = properties.assistant().anthropic();
    return new AnthropicDescriptionAssistant(
        objectMapper,
        new AnthropicDescriptionAssistant.Settings(
            anthropic.baseUrl(),
            anthropic.model(),
            anthropic.maxTokens(),
            properties.assistant().timeout()),
        environment.getProperty(API_KEY_VARIABLE));
  }

  @Bean
  SuggestionEventLog suggestionEventLog() {
    return new Slf4jSuggestionEventLog();
  }

  @Bean(destroyMethod = "shutdownNow")
  ExecutorService assistantExecutor() {
    AtomicInteger counter = new AtomicInteger();
    ThreadFactory threads =
        runnable -> {
          Thread thread = new Thread(runnable, "assistant-" + counter.incrementAndGet());
          thread.setDaemon(true);
          return thread;
        };
    ThreadPoolExecutor executor =
        new ThreadPoolExecutor(
            MAX_THREADS,
            MAX_THREADS,
            60,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY),
            threads,
            new ThreadPoolExecutor.AbortPolicy());
    executor.allowCoreThreadTimeOut(true);
    return executor;
  }

  @Bean
  SuggestDescriptionUseCase suggestDescriptionUseCase(
      FeatureFlags featureFlags,
      MatterRepository matters,
      DescriptionAssistant assistant,
      SuggestionEventLog eventLog,
      @Qualifier("assistantExecutor") ExecutorService assistantExecutor,
      AppProperties properties,
      Clock clock) {
    return new DescriptionSuggestionService(
        featureFlags,
        matters,
        assistant,
        eventLog,
        assistantExecutor,
        properties.assistant().timeout(),
        clock);
  }
}
