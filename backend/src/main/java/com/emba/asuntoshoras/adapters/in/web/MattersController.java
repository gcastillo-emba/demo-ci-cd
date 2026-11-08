package com.emba.asuntoshoras.adapters.in.web;

import com.emba.asuntoshoras.adapters.in.web.api.MattersApi;
import com.emba.asuntoshoras.adapters.in.web.api.model.Matter;
import com.emba.asuntoshoras.adapters.in.web.api.model.MatterCreate;
import com.emba.asuntoshoras.adapters.in.web.api.model.MatterPage;
import com.emba.asuntoshoras.adapters.in.web.api.model.MatterStatus;
import com.emba.asuntoshoras.application.port.in.MatterUseCases;
import com.emba.asuntoshoras.application.port.in.MatterUseCases.MatterFilter;
import com.emba.asuntoshoras.application.port.in.MatterUseCases.NewMatter;
import com.emba.asuntoshoras.application.port.in.MatterView;
import com.emba.asuntoshoras.application.port.in.PageQuery;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Implementa {@link MattersApi} (generada desde el contrato). */
@RestController
public class MattersController implements MattersApi {

  private final MatterUseCases matters;

  public MattersController(MatterUseCases matters) {
    this.matters = matters;
  }

  @Override
  public ResponseEntity<Matter> createMatter(MatterCreate matterCreate) {
    MatterView created =
        matters.createMatter(
            CurrentUserId.requireUser(),
            new NewMatter(
                matterCreate.getClientId(),
                matterCreate.getTitle(),
                matterCreate.getDescription()));
    return ResponseEntity.created(URI.create("/api/matters/" + created.matter().id()))
        .body(ApiMapper.toDto(created));
  }

  @Override
  public ResponseEntity<Matter> getMatter(UUID matterId) {
    return ResponseEntity.ok(ApiMapper.toDto(matters.getMatter(matterId)));
  }

  @Override
  public ResponseEntity<Matter> closeMatter(UUID matterId) {
    return ResponseEntity.ok(
        ApiMapper.toDto(matters.closeMatter(CurrentUserId.requireUser(), matterId)));
  }

  @Override
  public ResponseEntity<MatterPage> listMatters(
      String q, UUID clientId, MatterStatus status, Integer page, Integer size) {
    MatterFilter filter = new MatterFilter(q, clientId, ApiMapper.toDomain(status));
    return ResponseEntity.ok(
        ApiMapper.toMatterPage(matters.listMatters(filter, PageQuery.of(page, size))));
  }
}
