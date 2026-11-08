package com.emba.asuntoshoras.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.Jwt;

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
  void validatorRejectsTokensWithoutExpAndAcceptsValidOnes() {
    Instant now = Instant.parse("2026-11-07T12:00:00Z");
    Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    Jwt withoutExp =
        Jwt.withTokenValue("t").header("alg", "HS256").subject("u").issuedAt(now).build();
    Jwt valid =
        Jwt.withTokenValue("t")
            .header("alg", "HS256")
            .subject("u")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(60))
            .build();
    Jwt expired =
        Jwt.withTokenValue("t")
            .header("alg", "HS256")
            .subject("u")
            .issuedAt(now.minusSeconds(7200))
            .expiresAt(now.minusSeconds(3600))
            .build();

    assertThat(JwtConfig.jwtValidator(clock).validate(withoutExp).hasErrors()).isTrue();
    assertThat(JwtConfig.jwtValidator(clock).validate(expired).hasErrors()).isTrue();
    assertThat(JwtConfig.jwtValidator(clock).validate(valid).hasErrors()).isFalse();
  }

  @Test
  void jwtPropertiesNeverPrintTheSecret() {
    AppProperties.Jwt jwt = new AppProperties.Jwt("secreto-falso", java.time.Duration.ofHours(2));
    assertThat(jwt.toString()).doesNotContain("secreto-falso");
  }
}
