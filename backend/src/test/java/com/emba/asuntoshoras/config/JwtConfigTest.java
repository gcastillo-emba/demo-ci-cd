package com.emba.asuntoshoras.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class JwtConfigTest {

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void failsFastWithoutSecret(String secret) {
    assertThatIllegalStateException()
        .isThrownBy(() -> JwtConfig.secretKey(secret))
        .withMessageContaining("APP_JWT_SECRET");
  }

  @Test
  void rejectsSecretsShorterThan32Bytes() {
    assertThatIllegalStateException()
        .isThrownBy(() -> JwtConfig.secretKey("x".repeat(31)))
        .withMessageContaining("32 bytes");
  }

  @Test
  void acceptsSecretOf32Bytes() {
    assertThat(JwtConfig.secretKey("x".repeat(32)).getAlgorithm()).isEqualTo("HmacSHA256");
  }

  @Test
  void jwtPropertiesNeverPrintTheSecret() {
    AppProperties.Jwt jwt = new AppProperties.Jwt("secreto-falso", java.time.Duration.ofHours(2));
    assertThat(jwt.toString()).doesNotContain("secreto-falso");
  }
}
