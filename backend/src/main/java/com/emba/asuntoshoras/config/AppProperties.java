package com.emba.asuntoshoras.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Propiedades {@code app.*}. El secreto JWT no tiene valor por defecto fuera del perfil demo. */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
    @Valid @NotNull Jwt jwt,
    @NotBlank @DefaultValue("America/La_Paz") String timeZone,
    @Valid @NotNull @DefaultValue Features features) {

  /** Configuración del JWT HS256. */
  public record Jwt(String secret, @NotNull @DefaultValue("2h") Duration ttl) {

    @Override
    public String toString() {
      return "Jwt[secret=***, ttl=" + ttl + "]";
    }
  }

  /** Feature flags. */
  public record Features(@DefaultValue("false") boolean descriptionAssistant) {}
}
