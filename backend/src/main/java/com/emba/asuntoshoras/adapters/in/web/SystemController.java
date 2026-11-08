package com.emba.asuntoshoras.adapters.in.web;

import com.emba.asuntoshoras.adapters.in.web.api.SystemApi;
import com.emba.asuntoshoras.adapters.in.web.api.model.Features;
import com.emba.asuntoshoras.application.port.in.GetFeaturesQuery;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Implementa {@link SystemApi} (generada desde el contrato). */
@RestController
public class SystemController implements SystemApi {

  private final GetFeaturesQuery features;

  public SystemController(GetFeaturesQuery features) {
    this.features = features;
  }

  @Override
  public ResponseEntity<Features> getFeatures() {
    GetFeaturesQuery.FeatureSet set = features.getFeatures();
    return ResponseEntity.ok(new Features().descriptionAssistant(set.descriptionAssistant()));
  }
}
