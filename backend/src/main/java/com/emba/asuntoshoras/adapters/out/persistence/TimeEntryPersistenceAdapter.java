package com.emba.asuntoshoras.adapters.out.persistence;

import com.emba.asuntoshoras.application.port.in.PageQuery;
import com.emba.asuntoshoras.application.port.in.PageResult;
import com.emba.asuntoshoras.application.port.in.TimeEntryUseCases.TimeEntryFilter;
import com.emba.asuntoshoras.application.port.in.TimeEntryUseCases.TimeEntryOrder;
import com.emba.asuntoshoras.application.port.out.TimeEntryRepository;
import com.emba.asuntoshoras.domain.timeentry.Hours;
import com.emba.asuntoshoras.domain.timeentry.TimeEntry;
import com.emba.asuntoshoras.domain.timeentry.TimeEntryStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador de persistencia del puerto {@link TimeEntryRepository} (design D1, D4, D9). */
@Component
@Transactional(readOnly = true)
public class TimeEntryPersistenceAdapter implements TimeEntryRepository {

  /** Ningún registro tiene este id: evita un parámetro nulo en la suma diaria. */
  private static final UUID NO_ENTRY = new UUID(0L, 0L);

  private static final List<TimeEntryJpaEntity.StatusColumn> COUNTED_STATUSES =
      List.of(TimeEntryJpaEntity.StatusColumn.PENDING, TimeEntryJpaEntity.StatusColumn.APPROVED);

  private static final Sort WORK_DATE_DESC =
      Sort.by(Sort.Order.desc("workDate"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
  private static final Sort CREATED_AT_ASC =
      Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));

  private final TimeEntryJpaRepository repository;
  private final EntityManager entityManager;

  public TimeEntryPersistenceAdapter(
      TimeEntryJpaRepository repository, EntityManager entityManager) {
    this.repository = repository;
    this.entityManager = entityManager;
  }

  @Override
  @Transactional
  public void insert(TimeEntry entry) {
    entityManager.persist(
        new TimeEntryJpaEntity(
            entry.id(),
            entityManager.getReference(MatterJpaEntity.class, entry.matterId()),
            entityManager.getReference(UserJpaEntity.class, entry.lawyerId()),
            entry.workDate(),
            (short) entry.hours().tenths(),
            entry.description(),
            TimeEntryJpaEntity.StatusColumn.valueOf(entry.status().name()),
            entry.rejectionReason(),
            entry.createdAt()));
    entityManager.flush();
  }

  @Override
  @Transactional
  public Optional<TimeEntry> findByIdForUpdate(UUID id) {
    return repository.findForUpdateById(id).map(TimeEntryPersistenceAdapter::toDomain);
  }

  @Override
  public Optional<TimeEntryListing> findListing(UUID id) {
    return repository.findWithMatterAndLawyerById(id).map(TimeEntryPersistenceAdapter::toListing);
  }

  @Override
  @Transactional
  public boolean update(TimeEntry entry) {
    TimeEntryJpaEntity entity = entityManager.find(TimeEntryJpaEntity.class, entry.id());
    if (entity == null || entity.getVersion() != entry.version()) {
      return false;
    }
    entity.apply(
        entityManager.getReference(MatterJpaEntity.class, entry.matterId()),
        entry.workDate(),
        (short) entry.hours().tenths(),
        entry.description(),
        TimeEntryJpaEntity.StatusColumn.valueOf(entry.status().name()),
        entry.rejectionReason());
    try {
      entityManager.flush();
    } catch (OptimisticLockException | OptimisticLockingFailureException e) {
      return false;
    }
    return true;
  }

  @Override
  @Transactional
  public Hours sumCountedHours(UUID lawyerId, LocalDate workDate, UUID excludedEntryId) {
    return Hours.sumOfTenths(
        repository.sumTenths(
            lawyerId,
            workDate,
            COUNTED_STATUSES,
            excludedEntryId == null ? NO_ENTRY : excludedEntryId));
  }

  @Override
  public PageResult<TimeEntryListing> search(
      TimeEntryFilter filter, TimeEntryOrder order, PageQuery page) {
    Sort sort = order == TimeEntryOrder.CREATED_AT_ASC ? CREATED_AT_ASC : WORK_DATE_DESC;
    Page<TimeEntryJpaEntity> result =
        repository.findAll(matching(filter), PageRequest.of(page.page(), page.size(), sort));
    return PageResult.of(
        result.getContent().stream().map(TimeEntryPersistenceAdapter::toListing).toList(),
        page,
        result.getTotalElements());
  }

  private static Specification<TimeEntryJpaEntity> matching(TimeEntryFilter filter) {
    return (root, query, cb) -> {
      Class<?> resultType = query == null ? null : query.getResultType();
      if (resultType != Long.class && resultType != long.class) {
        // Asunto y abogado en la misma consulta (no en la de conteo) para evitar N+1.
        root.fetch("matter", JoinType.INNER);
        root.fetch("lawyer", JoinType.INNER);
      }
      List<Predicate> predicates = new ArrayList<>();
      if (filter.matterId() != null) {
        predicates.add(cb.equal(root.get("matter").get("id"), filter.matterId()));
      }
      if (filter.lawyerId() != null) {
        predicates.add(cb.equal(root.get("lawyer").get("id"), filter.lawyerId()));
      }
      if (filter.status() != null) {
        predicates.add(
            cb.equal(
                root.get("status"),
                TimeEntryJpaEntity.StatusColumn.valueOf(filter.status().name())));
      }
      if (filter.from() != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("workDate"), filter.from()));
      }
      if (filter.to() != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("workDate"), filter.to()));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }

  private static TimeEntryListing toListing(TimeEntryJpaEntity entity) {
    return new TimeEntryListing(
        toDomain(entity), entity.getMatter().getCode(), entity.getLawyer().getFullName());
  }

  private static TimeEntry toDomain(TimeEntryJpaEntity entity) {
    return TimeEntry.restore(
        entity.getId(),
        entity.getMatter().getId(),
        entity.getLawyer().getId(),
        entity.getWorkDate(),
        Hours.ofTenths(entity.getHoursTenths()),
        entity.getDescription(),
        TimeEntryStatus.valueOf(entity.getStatus().name()),
        entity.getRejectionReason(),
        entity.getCreatedAt(),
        entity.getVersion());
  }
}
