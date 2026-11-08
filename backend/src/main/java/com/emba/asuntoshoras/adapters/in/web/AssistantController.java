package com.emba.asuntoshoras.adapters.in.web;

import com.emba.asuntoshoras.adapters.in.web.api.AssistantApi;
import com.emba.asuntoshoras.adapters.in.web.api.model.AssistantProvider;
import com.emba.asuntoshoras.adapters.in.web.api.model.DescriptionSuggestionRequest;
import com.emba.asuntoshoras.adapters.in.web.api.model.DescriptionSuggestionResponse;
import com.emba.asuntoshoras.application.port.in.SuggestDescriptionUseCase;
import com.emba.asuntoshoras.application.port.in.SuggestDescriptionUseCase.Suggestion;
import com.emba.asuntoshoras.application.port.in.SuggestDescriptionUseCase.SuggestionRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implementa {@link AssistantApi} (generada desde el contrato). La nota llega cruda: el recorte, la
 * validación con códigos propios y los guardrails están en la capa de aplicación (design D1).
 */
@RestController
public class AssistantController implements AssistantApi {

  private final SuggestDescriptionUseCase suggestDescription;

  public AssistantController(SuggestDescriptionUseCase suggestDescription) {
    this.suggestDescription = suggestDescription;
  }

  @Override
  public ResponseEntity<DescriptionSuggestionResponse> suggestDescription(
      DescriptionSuggestionRequest body) {
    Suggestion suggestion =
        suggestDescription.suggest(
            CurrentUserId.requireUser(), new SuggestionRequest(body.getNote(), body.getMatterId()));
    return ResponseEntity.ok(
        new DescriptionSuggestionResponse(
            suggestion.text(), AssistantProvider.fromValue(suggestion.provider())));
  }
}
