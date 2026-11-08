package com.emba.asuntoshoras.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FeaturesServiceTest {

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void exposesDescriptionAssistantFlag(boolean enabled) {
    FeaturesService service = new FeaturesService(() -> enabled);

    assertThat(service.getFeatures().descriptionAssistant()).isEqualTo(enabled);
  }
}
