package com.emba.asuntoshoras.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
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
    @Valid @NotNull @DefaultValue Features features,
    @Valid @NotNull @DefaultValue Assistant assistant) {

  /** Configuración del JWT HS256. */
  public record Jwt(String secret, @NotNull @DefaultValue("2h") Duration ttl) {

    @Override
    public String toString() {
      return "Jwt[secret=***, ttl=" + ttl + "]";
    }
  }

  /** Feature flags. */
  public record Features(@DefaultValue("false") boolean descriptionAssistant) {}

  /**
   * Asistente de descripción (add-description-assistant D4–D6). Un {@code provider} desconocido
   * hace fallar el arranque (enlace del enum).
   */
  public record Assistant(
      @NotNull @DefaultValue("fake") Provider provider,
      @NotNull @DefaultValue("8s") Duration timeout,
      @Valid @NotNull @DefaultValue Anthropic anthropic) {

    /** Adaptador del puerto {@code DescriptionAssistant}. */
    public enum Provider {
      FAKE,
      ANTHROPIC
    }
  }

  /**
   * Adaptador {@code anthropic}: modelo (vacío = por defecto), tope duro de 300 {@code max-tokens}
   * y URL base de la Messages API. La clave NO es una propiedad: se lee de {@code
   * ANTHROPIC_API_KEY}.
   */
  public record Anthropic(
      @DefaultValue(Anthropic.DEFAULT_MODEL) String model,
      @Min(1) @Max(300) @DefaultValue("300") int maxTokens,
      @NotNull @DefaultValue("https://api.anthropic.com") URI baseUrl) {

    public static final String DEFAULT_MODEL = "claude-opus-5-5";

    public Anthropic {
      model = model == null || model.isBlank() ? DEFAULT_MODEL : model.strip();
    }
  }
}
