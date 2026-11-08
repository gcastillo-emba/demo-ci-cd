package com.emba.asuntoshoras.domain.assistant;

import com.emba.asuntoshoras.domain.shared.DomainException;

/**
 * El indicador {@code description-assistant} está apagado: el endpoint se comporta como inexistente
 * ({@code 404 feature-disabled}, design D2).
 */
public class FeatureDisabledException extends DomainException {

  public static final String CODE = "feature-disabled";

  public FeatureDisabledException() {
    super(CODE, "El asistente de descripción no está habilitado.");
  }
}
