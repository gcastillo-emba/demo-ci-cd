package com.emba.asuntoshoras.application.service;

import com.emba.asuntoshoras.application.port.in.GetFeaturesQuery;
import com.emba.asuntoshoras.application.port.out.FeatureFlags;
import java.util.Objects;

/** Expone los feature flags visibles para la interfaz. */
public class FeaturesService implements GetFeaturesQuery {

  private final FeatureFlags featureFlags;

  public FeaturesService(FeatureFlags featureFlags) {
    this.featureFlags = Objects.requireNonNull(featureFlags);
  }

  @Override
  public FeatureSet getFeatures() {
    return new FeatureSet(featureFlags.descriptionAssistantEnabled());
  }
}
