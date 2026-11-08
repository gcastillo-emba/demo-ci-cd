package com.emba.asuntoshoras.adapters.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.emba.asuntoshoras.application.port.out.TokenIssuer.IssuedToken;
import com.emba.asuntoshoras.domain.user.Email;
import com.emba.asuntoshoras.domain.user.Role;
import com.emba.asuntoshoras.domain.user.User;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtTokenIssuerTest {

  private static final SecretKey KEY =
      new SecretKeySpec(
          "test-secreto-falso-de-al-menos-32-bytes!".getBytes(StandardCharsets.UTF_8),
          "HmacSHA256");
  private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");

  @Test
  void issuesHs256TokenWithExpectedClaims() {
    Clock clock = Clock.fixed(NOW, ZoneId.of("America/La_Paz"));
    JwtTokenIssuer issuer =
        new JwtTokenIssuer(
            new NimbusJwtEncoder(new ImmutableSecret<>(KEY)), clock, Duration.ofHours(2));
    User user =
        User.restore(
            UUID.fromString("00000000-0000-4000-8000-000000000002"),
            Email.of("abogado1@asuntos.test"),
            "Abogado Uno",
            "hash",
            Role.LAWYER,
            true);

    IssuedToken token = issuer.issue(user);

    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(
        jwt -> org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success());
    Jwt jwt = decoder.decode(token.value());

    assertThat(token.expiresIn()).isEqualTo(Duration.ofHours(2));
    assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
    assertThat(jwt.getSubject()).isEqualTo(user.id().toString());
    assertThat(jwt.getClaimAsString("role")).isEqualTo("LAWYER");
    assertThat(jwt.getClaims()).doesNotContainKey("email"); // ADR-006: sin PII en el token
    assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
    assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(2)));
  }
}
