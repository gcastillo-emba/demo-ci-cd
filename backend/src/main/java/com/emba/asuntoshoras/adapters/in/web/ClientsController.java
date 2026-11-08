package com.emba.asuntoshoras.adapters.in.web;

import com.emba.asuntoshoras.adapters.in.web.api.ClientsApi;
import com.emba.asuntoshoras.adapters.in.web.api.model.Client;
import com.emba.asuntoshoras.adapters.in.web.api.model.ClientCreate;
import com.emba.asuntoshoras.adapters.in.web.api.model.ClientPage;
import com.emba.asuntoshoras.application.port.in.ClientUseCases;
import com.emba.asuntoshoras.application.port.in.ClientUseCases.ClientFilter;
import com.emba.asuntoshoras.application.port.in.ClientUseCases.NewClient;
import com.emba.asuntoshoras.application.port.in.PageQuery;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Implementa {@link ClientsApi} (generada desde el contrato). */
@RestController
public class ClientsController implements ClientsApi {

  private final ClientUseCases clients;

  public ClientsController(ClientUseCases clients) {
    this.clients = clients;
  }

  @Override
  public ResponseEntity<Client> createClient(ClientCreate clientCreate) {
    com.emba.asuntoshoras.domain.client.Client created =
        clients.createClient(
            CurrentUserId.requireUser(),
            new NewClient(clientCreate.getName(), clientCreate.getTaxId()));
    return ResponseEntity.created(URI.create("/api/clients/" + created.id()))
        .body(ApiMapper.toDto(created));
  }

  @Override
  public ResponseEntity<Client> getClient(UUID clientId) {
    return ResponseEntity.ok(ApiMapper.toDto(clients.getClient(clientId)));
  }

  @Override
  public ResponseEntity<ClientPage> listClients(String q, Integer page, Integer size) {
    return ResponseEntity.ok(
        ApiMapper.toClientPage(clients.listClients(new ClientFilter(q), PageQuery.of(page, size))));
  }
}
