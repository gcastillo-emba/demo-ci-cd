package com.emba.asuntoshoras.adapters.out.persistence;

import com.emba.asuntoshoras.application.port.in.MatterUseCases.MatterFilter;
import com.emba.asuntoshoras.application.port.in.MatterView;
import com.emba.asuntoshoras.application.port.in.PageQuery;
import com.emba.asuntoshoras.application.port.in.PageResult;
import com.emba.asuntoshoras.application.port.out.MatterRepository;
import com.emba.asuntoshoras.domain.matter.Matter;
import com.emba.asuntoshoras.domain.matter.MatterCode;
import com.emba.asuntoshoras.domain.matter.MatterStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador de persistencia del puerto {@link MatterRepository} (design D5, D8). */
@Component
@Transactional(readOnly = true)
public class MatterPersistenceAdapter implements MatterRepository {

  private static final Sort ORDER = Sort.by(Sort.Order.desc("code"));

  private final MatterJpaRepository repository;
  private final EntityManager entityManager;

  public MatterPersistenceAdapter(MatterJpaRepository repository, EntityManager entityManager) {
    this.repository = repository;
    this.entityManager = entityManager;
  }

  @Override
  @Transactional
  public void insert(Matter matter) {
    entityManager.persist(
        new MatterJpaEntity(
            matter.id(),
            matter.code().value(),
            entityManager.getReference(ClientJpaEntity.class, matter.clientId()),
            matter.title(),
            matter.description(),
            MatterJpaEntity.StatusColumn.valueOf(matter.status().name()),
            matter.createdAt(),
            matter.closedAt(),
            matter.version()));
    entityManager.flush();
  }

  @Override
  public Optional<MatterView> findById(UUID id) {
    return repository.findWithClientById(id).map(MatterPersistenceAdapter::toView);
  }

  @Override
  @Transactional
  public boolean saveClosed(Matter matter) {
    return repository.closeIfVersion(
            matter.id(),
            matter.version(),
            matter.closedAt(),
            MatterJpaEntity.StatusColumn.OPEN,
            MatterJpaEntity.StatusColumn.CLOSED)
        == 1;
  }

  @Override
  public PageResult<MatterView> search(MatterFilter filter, PageQuery page) {
    Page<MatterJpaEntity> result =
        repository.findAll(matching(filter), PageRequest.of(page.page(), page.size(), ORDER));
    return PageResult.of(
        result.getContent().stream().map(MatterPersistenceAdapter::toView).toList(),
        page,
        result.getTotalElements());
  }

  private static Specification<MatterJpaEntity> matching(MatterFilter filter) {
    String pattern = LikePattern.contains(filter.q());
    return (root, query, cb) -> {
      Class<?> resultType = query == null ? null : query.getResultType();
      if (resultType != Long.class && resultType != long.class) {
        // El cliente se trae en la misma consulta (no en la de conteo) para evitar N+1.
        root.fetch("client", JoinType.INNER);
      }
      List<Predicate> predicates = new ArrayList<>();
      if (filter.clientId() != null) {
        predicates.add(cb.equal(root.get("client").get("id"), filter.clientId()));
      }
      if (filter.status() != null) {
        predicates.add(
            cb.equal(
                root.get("status"), MatterJpaEntity.StatusColumn.valueOf(filter.status().name())));
      }
      if (pattern != null) {
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("title")), pattern, LikePattern.ESCAPE),
                cb.like(cb.lower(root.get("code")), pattern, LikePattern.ESCAPE)));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }

  static MatterView toView(MatterJpaEntity entity) {
    Matter matter =
        Matter.restore(
            entity.getId(),
            new MatterCode(entity.getCode()),
            entity.getClient().getId(),
            entity.getTitle(),
            entity.getDescription(),
            MatterStatus.valueOf(entity.getStatus().name()),
            entity.getCreatedAt(),
            entity.getClosedAt(),
            entity.getVersion());
    return new MatterView(matter, entity.getClient().getName());
  }
}
