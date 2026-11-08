package com.emba.asuntoshoras.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.emba.asuntoshoras.adapters.out.assistant.anthropic.AnthropicDescriptionAssistant;
import com.emba.asuntoshoras.adapters.out.assistant.fake.FakeDescriptionAssistant;
import com.emba.asuntoshoras.application.fakes.InMemoryClientRepository;
import com.emba.asuntoshoras.application.fakes.InMemoryMatterRepository;
import com.emba.asuntoshoras.application.port.in.SuggestDescriptionUseCase;
import com.emba.asuntoshoras.application.port.out.DescriptionAssistant;
import com.emba.asuntoshoras.application.port.out.FeatureFlags;
import com.emba.asuntoshoras.application.port.out.MatterRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Tarea 2.7 / design D5: selección del adaptador por {@code app.assistant.provider}. */
class AssistantConfigTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(Collaborators.class, AssistantConfig.class)
          .withPropertyValues("app.jwt.ttl=2h");

  @Test
  void fakeIsTheDefaultAndTimeoutIsEightSeconds() {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context.getBean(DescriptionAssistant.class))
              .isInstanceOf(FakeDescriptionAssistant.class);
          assertThat(context).doesNotHaveBean(AnthropicDescriptionAssistant.class);
          AppProperties properties = context.getBean(AppProperties.class);
          assertThat(properties.assistant().timeout()).isEqualTo(Duration.ofSeconds(8));
          assertThat(properties.assistant().anthropic().maxTokens()).isEqualTo(300);
          assertThat(properties.assistant().anthropic().model())
              .isEqualTo(AppProperties.Anthropic.DEFAULT_MODEL);
          assertThat(properties.features().descriptionAssistant()).isFalse();
          assertThat(context.getBean(SuggestDescriptionUseCase.class)).isNotNull();
          assertThat(context.getBean("assistantExecutor", ExecutorService.class))
              .isInstanceOfSatisfying(
                  ThreadPoolExecutor.class,
                  pool -> assertThat(pool.getMaximumPoolSize()).isEqualTo(8));
        });
  }

  @Test
  void anthropicIsBuiltOnlyWhenRequested() {
    runner
        .withPropertyValues(
            "app.assistant.provider=anthropic",
            "app.assistant.anthropic.model=",
            "app.assistant.timeout=3s")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(DescriptionAssistant.class))
                  .isInstanceOf(AnthropicDescriptionAssistant.class);
              assertThat(context).doesNotHaveBean(FakeDescriptionAssistant.class);
              assertThat(context.getBean(AppProperties.class).assistant().anthropic().model())
                  .isEqualTo("claude-opus-5-5");
            });
  }

  @Test
  void unknownProviderFailsAtStartup() {
    runner.withPropertyValues("app.assistant.provider=otro").run(c -> assertThat(c).hasFailed());
  }

  @Test
  void maxTokensAboveThreeHundredFailsAtStartup() {
    runner
        .withPropertyValues("app.assistant.anthropic.max-tokens=301")
        .run(c -> assertThat(c).hasFailed());
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(AppProperties.class)
  static class Collaborators {

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    Clock clock() {
      return Clock.systemUTC();
    }

    @Bean
    FeatureFlags featureFlags() {
      return () -> true;
    }

    @Bean
    MatterRepository matterRepository() {
      return new InMemoryMatterRepository(new InMemoryClientRepository());
    }
  }
}
