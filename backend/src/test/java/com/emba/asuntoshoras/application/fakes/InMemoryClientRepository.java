package com.emba.asuntoshoras.application.fakes;

import com.emba.asuntoshoras.application.port.in.ClientUseCases.ClientFilter;
import com.emba.asuntoshoras.application.port.in.PageQuery;
import com.emba.asuntoshoras.application.port.in.PageResult;
import com.emba.asuntoshoras.application.port.out.ClientRepository;
import com.emba.asuntoshoras.domain.client.Client;
import com.emba.asuntoshoras.domain.client.ClientTaxIdTakenException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Fake en memoria del puerto {@link ClientRepository} (sin Mockito, ADR-002). */
public final class InMemoryClientRepository implements ClientRepository {

  private final Map<UUID, Client> clients = new LinkedHashMap<>();

  /** Simula la carrera de dos altas: {@code existsByTaxId} no ve el duplicado, el índice sí. */
  private boolean hideTaxIdsFromExistsCheck;

  public InMemoryClientRepository with(Client client) {
    clients.put(client.id(), client);
    return this;
  }

  public InMemoryClientRepository hidingTaxIdsFromExistsCheck() {
    this.hideTaxIdsFromExistsCheck = true;
    return this;
  }

  public int count() {
    return clients.size();
  }

  @Override
  public void insert(Client client) {
    if (client.taxId() != null
        && clients.values().stream().anyMatch(c -> client.taxId().equals(c.taxId()))) {
      throw new ClientTaxIdTakenException();
    }
    clients.put(client.id(), client);
  }

  @Override
  public Optional<Client> findById(UUID id) {
    return Optional.ofNullable(clients.get(id));
  }

  @Override
  public boolean existsByTaxId(String taxId) {
    return !hideTaxIdsFromExistsCheck
        && clients.values().stream().anyMatch(c -> Objects.equals(taxId, c.taxId()));
  }

  @Override
  public PageResult<Client> search(ClientFilter filter, PageQuery page) {
    String q = filter.q() == null ? "" : filter.q().trim().toLowerCase(Locale.ROOT);
    List<Client> matching =
        clients.values().stream()
            .filter(c -> c.name().toLowerCase(Locale.ROOT).contains(q))
            .sorted(Comparator.comparing(Client::name).thenComparing(Client::id))
            .toList();
    return Pages.slice(matching, page);
  }
}
