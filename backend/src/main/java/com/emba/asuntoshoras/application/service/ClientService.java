package com.emba.asuntoshoras.application.service;

import com.emba.asuntoshoras.application.port.in.ClientUseCases;
import com.emba.asuntoshoras.application.port.in.CurrentUser;
import com.emba.asuntoshoras.application.port.in.PageQuery;
import com.emba.asuntoshoras.application.port.in.PageResult;
import com.emba.asuntoshoras.application.port.out.ClientRepository;
import com.emba.asuntoshoras.domain.client.Client;
import com.emba.asuntoshoras.domain.client.ClientNotFoundException;
import com.emba.asuntoshoras.domain.client.ClientTaxIdTakenException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Alta, listado y consulta de clientes. Solo el Socio escribe. */
public class ClientService implements ClientUseCases {

  private final ClientRepository clients;
  private final Clock clock;

  public ClientService(ClientRepository clients, Clock clock) {
    this.clients = Objects.requireNonNull(clients);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  @Transactional
  public Client createClient(CurrentUser user, NewClient command) {
    Authorization.requirePartner(user);
    Client client =
        Client.create(
            UUID.randomUUID(),
            command.name(),
            command.taxId(),
            clock.instant().truncatedTo(ChronoUnit.MICROS));
    if (client.taxId() != null && clients.existsByTaxId(client.taxId())) {
      throw new ClientTaxIdTakenException();
    }
    clients.insert(client);
    return client;
  }

  @Override
  @Transactional(readOnly = true)
  public PageResult<Client> listClients(ClientFilter filter, PageQuery page) {
    return clients.search(filter == null ? new ClientFilter(null) : filter, page);
  }

  @Override
  @Transactional(readOnly = true)
  public Client getClient(UUID clientId) {
    return clients.findById(clientId).orElseThrow(() -> new ClientNotFoundException(clientId));
  }
}
