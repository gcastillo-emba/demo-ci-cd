package com.emba.asuntoshoras;

import com.emba.asuntoshoras.adapters.out.persistence.ClientPersistenceAdapter;
import com.emba.asuntoshoras.application.port.in.ClientUseCases.ClientFilter;
import com.emba.asuntoshoras.application.port.in.PageQuery;
import com.emba.asuntoshoras.application.port.in.PageResult;
import com.emba.asuntoshoras.application.port.out.ClientRepository;
import com.emba.asuntoshoras.domain.client.Client;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * Beans controlables para los IT que necesitan fijar "ahora" o simular una falla de la base: un
 * {@link MutableClock} y un {@link ClientRepository} que puede fallar en el próximo alta.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ControlledBeansConfiguration {

  public static final ZoneId LA_PAZ = ZoneId.of("America/La_Paz");

  @Bean
  @Primary
  MutableClock mutableClock() {
    return new MutableClock(Instant.parse("2026-11-07T14:00:00Z"), LA_PAZ);
  }

  @Bean
  @Primary
  FailingClientRepository failingClientRepository(ClientPersistenceAdapter delegate) {
    return new FailingClientRepository(delegate);
  }

  /** Reloj que los tests pueden mover (sin {@code Thread.sleep}). */
  public static final class MutableClock extends Clock {

    private final AtomicReference<Instant> instant;
    private final ZoneId zone;

    MutableClock(Instant initial, ZoneId zone) {
      this.instant = new AtomicReference<>(initial);
      this.zone = zone;
    }

    public void set(Instant value) {
      instant.set(value);
    }

    @Override
    public ZoneId getZone() {
      return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
      return new MutableClock(instant.get(), newZone);
    }

    @Override
    public Instant instant() {
      return instant.get();
    }
  }

  /** Delegado del adaptador real que puede simular una falla inesperada de la base. */
  public static final class FailingClientRepository implements ClientRepository {

    /** Mensaje con SQL, clase de excepción y texto del driver: nada de esto debe filtrarse. */
    public static final String LEAKY_MESSAGE =
        "could not execute statement [insert into clients (id,name,tax_id) values (?,?,?)];"
            + " SQL [insert into clients]; nested exception is org.postgresql.util.PSQLException";

    private final ClientRepository delegate;
    private final AtomicBoolean failNextInsert = new AtomicBoolean();

    FailingClientRepository(ClientRepository delegate) {
      this.delegate = delegate;
    }

    public void failNextInsert() {
      failNextInsert.set(true);
    }

    @Override
    public void insert(Client client) {
      if (failNextInsert.getAndSet(false)) {
        throw new DataAccessResourceFailureException(
            LEAKY_MESSAGE, new SQLException("FATAL: terminating connection SQLState 57P01"));
      }
      delegate.insert(client);
    }

    @Override
    public Optional<Client> findById(UUID id) {
      return delegate.findById(id);
    }

    @Override
    public boolean existsByTaxId(String taxId) {
      return delegate.existsByTaxId(taxId);
    }

    @Override
    public PageResult<Client> search(ClientFilter filter, PageQuery page) {
      return delegate.search(filter, page);
    }
  }
}
