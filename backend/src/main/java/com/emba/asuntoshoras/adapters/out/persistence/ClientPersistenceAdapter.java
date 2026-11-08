package com.emba.asuntoshoras.adapters.out.persistence;

import com.emba.asuntoshoras.application.port.in.ClientUseCases.ClientFilter;
import com.emba.asuntoshoras.application.port.in.PageQuery;
import com.emba.asuntoshoras.application.port.in.PageResult;
import com.emba.asuntoshoras.application.port.out.ClientRepository;
import com.emba.asuntoshoras.domain.client.Client;
import com.emba.asuntoshoras.domain.client.ClientTaxIdTakenException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador de persistencia del puerto {@link ClientRepository} (design D4, D8). */
@Component
@Transactional(readOnly = true)
public class ClientPersistenceAdapter implements ClientRepository {

  static final String TAX_ID_UNIQUE_INDEX = "ux_clients_tax_id";

  private static final Sort ORDER = Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"));

  private final ClientJpaRepository repository;
  private final EntityManager entityManager;

  public ClientPersistenceAdapter(ClientJpaRepository repository, EntityManager entityManager) {
    this.repository = repository;
    this.entityManager = entityManager;
  }

  @Override
  @Transactional
  public void insert(Client client) {
    try {
      entityManager.persist(
          new ClientJpaEntity(client.id(), client.name(), client.taxId(), client.createdAt()));
      entityManager.flush();
    } catch (PersistenceException e) {
      if (violates(e, TAX_ID_UNIQUE_INDEX)) {
        throw new ClientTaxIdTakenException();
      }
      throw e;
    }
  }

  @Override
  public Optional<Client> findById(UUID id) {
    return repository.findById(id).map(ClientPersistenceAdapter::toDomain);
  }

  @Override
  public boolean existsByTaxId(String taxId) {
    return repository.existsByTaxId(taxId);
  }

  @Override
  public PageResult<Client> search(ClientFilter filter, PageQuery page) {
    Page<ClientJpaEntity> result =
        repository.findAll(
            nameContains(filter.q()), PageRequest.of(page.page(), page.size(), ORDER));
    return PageResult.of(
        result.getContent().stream().map(ClientPersistenceAdapter::toDomain).toList(),
        page,
        result.getTotalElements());
  }

  private static Specification<ClientJpaEntity> nameContains(String q) {
    String pattern = LikePattern.contains(q);
    return (root, query, cb) ->
        pattern == null
            ? cb.conjunction()
            : cb.like(cb.lower(root.get("name")), pattern, LikePattern.ESCAPE);
  }

  static Client toDomain(ClientJpaEntity entity) {
    return Client.restore(
        entity.getId(), entity.getName(), entity.getTaxId(), entity.getCreatedAt());
  }

  /** Indica si la excepción (o alguna causa) es la violación de la restricción indicada. */
  static boolean violates(Throwable error, String constraint) {
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConstraintViolationException violation
          && violation.getConstraintName() != null
          && violation.getConstraintName().toLowerCase(Locale.ROOT).contains(constraint)) {
        return true;
      }
    }
    return false;
  }
}
