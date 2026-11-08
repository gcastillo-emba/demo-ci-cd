package com.emba.asuntoshoras.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Llave HS256 desde {@code app.jwt.secret} (variable {@code APP_JWT_SECRET}). El backend no arranca
 * sin un secreto de al menos 32 bytes (ADR-006).
 */
@Configuration(proxyBeanMethods = false)
public class JwtConfig {

  static final int MIN_SECRET_BYTES = 32;

  @Bean
  SecretKey jwtSecretKey(AppProperties properties) {
    return secretKey(properties.jwt().secret());
  }

  @Bean
  JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
  }

  @Bean
  JwtDecoder jwtDecoder(SecretKey jwtSecretKey, Clock clock) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(jwtValidator(clock));
    return decoder;
  }

  /**
   * Validadores por defecto de Spring Security (marcas de tiempo con el {@link Clock} inyectable y
   * huella X.509) más {@code exp} obligatorio: {@link JwtTimestampValidator} acepta un token sin
   * {@code exp}, lo que lo haría eterno.
   */
  static OAuth2TokenValidator<Jwt> jwtValidator(Clock clock) {
    JwtTimestampValidator timestamps = new JwtTimestampValidator();
    timestamps.setClock(clock);
    OAuth2TokenValidator<Jwt> requireExp =
        new JwtClaimValidator<Instant>(JwtClaimNames.EXP, Objects::nonNull);
    return JwtValidators.createDefaultWithValidators(List.of(timestamps, requireExp));
  }

  static SecretKey secretKey(String secret) {
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException(
          "Falta app.jwt.secret: defina la variable de entorno APP_JWT_SECRET (mínimo 32 bytes).");
    }
    byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
    if (bytes.length < MIN_SECRET_BYTES) {
      throw new IllegalStateException(
          "APP_JWT_SECRET es demasiado corto: se requieren al menos 32 bytes para HS256.");
    }
    return new SecretKeySpec(bytes, "HmacSHA256");
  }
}
