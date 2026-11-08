package com.emba.asuntoshoras.application.fakes;

import com.emba.asuntoshoras.application.port.in.MatterUseCases.MatterFilter;
import com.emba.asuntoshoras.application.port.in.MatterView;
import com.emba.asuntoshoras.application.port.in.PageQuery;
import com.emba.asuntoshoras.application.port.in.PageResult;
import com.emba.asuntoshoras.application.port.out.ClientRepository;
import com.emba.asuntoshoras.application.port.out.MatterRepository;
import com.emba.asuntoshoras.domain.client.Client;
import com.emba.asuntoshoras.domain.matter.Matter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Fake en memoria del puerto {@link MatterRepository} (sin Mockito, ADR-002). Guarda copias para
 * imitar la persistencia: un cambio en el agregado leído no se ve hasta {@code saveClosed}.
 */
public final class InMemoryMatterRepository implements MatterRepository {

  private final Map<UUID, Matter> matters = new LinkedHashMap<>();
  private final ClientRepository clients;
  private Consumer<UUID> beforeSaveClosed = id -> {};

  public InMemoryMatterRepository(ClientRepository clients) {
    this.clients = clients;
  }

  public InMemoryMatterRepository with(Matter matter) {
    matters.put(matter.id(), copy(matter, 0));
    return this;
  }

  /** Acción que se ejecuta justo antes de persistir un cierre (simula otro Socio concurrente). */
  public InMemoryMatterRepository beforeSaveClosed(Consumer<UUID> action) {
    this.beforeSaveClosed = action;
    return this;
  }

  /** Simula que otro proceso cerró el asunto (incrementa la versión). */
  public void closeConcurrently(UUID id, java.time.Instant closedAt) {
    Matter current = matters.get(id);
    Matter closed = copy(current, 1);
    closed.close(closedAt);
    matters.put(id, closed);
  }

  /** Simula que otro proceso borró el asunto. */
  public void removeConcurrently(UUID id) {
    matters.remove(id);
  }

  public int count() {
    return matters.size();
  }

  @Override
  public void insert(Matter matter) {
    if (matters.values().stream().anyMatch(m -> m.code().equals(matter.code()))) {
      throw new IllegalStateException("código duplicado " + matter.code());
    }
    matters.put(matter.id(), copy(matter, 0));
  }

  @Override
  public Optional<MatterView> findById(UUID id) {
    return Optional.ofNullable(matters.get(id)).map(m -> view(copy(m, 0)));
  }

  @Override
  public boolean saveClosed(Matter matter) {
    beforeSaveClosed.accept(matter.id());
    Matter stored = matters.get(matter.id());
    if (stored == null || stored.version() != matter.version() || stored.isClosed()) {
      return false;
    }
    matters.put(
        matter.id(),
        Matter.restore(
            matter.id(),
            matter.code(),
            matter.clientId(),
            matter.title(),
            matter.description(),
            matter.status(),
            matter.createdAt(),
            matter.closedAt(),
            matter.version() + 1));
    return true;
  }

  @Override
  public PageResult<MatterView> search(MatterFilter filter, PageQuery page) {
    String q = filter.q() == null ? "" : filter.q().trim().toLowerCase(Locale.ROOT);
    List<MatterView> matching =
        matters.values().stream()
            .filter(m -> filter.clientId() == null || filter.clientId().equals(m.clientId()))
            .filter(m -> filter.status() == null || filter.status() == m.status())
            .filter(
                m ->
                    m.title().toLowerCase(Locale.ROOT).contains(q)
                        || m.code().value().toLowerCase(Locale.ROOT).contains(q))
            .sorted(Comparator.comparing((Matter m) -> m.code().value()).reversed())
            .map(this::view)
            .toList();
    return Pages.slice(matching, page);
  }

  private MatterView view(Matter matter) {
    String clientName = clients.findById(matter.clientId()).map(Client::name).orElse("?");
    return new MatterView(matter, clientName);
  }

  private static Matter copy(Matter m, long versionIncrement) {
    return Matter.restore(
        m.id(),
        m.code(),
        m.clientId(),
        m.title(),
        m.description(),
        m.status(),
        m.createdAt(),
        m.closedAt(),
        m.version() + versionIncrement);
  }
}
