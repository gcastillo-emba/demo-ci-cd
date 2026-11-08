package com.emba.asuntoshoras.application.port.in;

/** Consulta: feature flags visibles para la interfaz. */
public interface GetFeaturesQuery {

  FeatureSet getFeatures();

  /** Estado de los feature flags. */
  record FeatureSet(boolean descriptionAssistant) {}
}
