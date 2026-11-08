package com.emba.asuntoshoras.adapters.out.persistence;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio Spring Data de {@link TimeEntryJpaEntity}. */
public interface TimeEntryJpaRepository
    extends JpaRepository<TimeEntryJpaEntity, UUID>, JpaSpecificationExecutor<TimeEntryJpaEntity> {

  /** Lee el registro con {@code SELECT … FOR UPDATE}. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from TimeEntryJpaEntity e where e.id = :id")
  Optional<TimeEntryJpaEntity> findForUpdateById(@Param("id") UUID id);

  /** Registro con su asunto y su abogado en una sola consulta (sin N+1). */
  @Query(
      "select e from TimeEntryJpaEntity e join fetch e.matter join fetch e.lawyer"
          + " where e.id = :id")
  Optional<TimeEntryJpaEntity> findWithMatterAndLawyerById(@Param("id") UUID id);

  /** Suma de décimas del abogado en un día para los estados dados, excluido un registro. */
  @Query(
      "select coalesce(sum(e.hoursTenths), 0) from TimeEntryJpaEntity e"
          + " where e.lawyer.id = :lawyerId and e.workDate = :workDate"
          + " and e.status in :statuses and e.id <> :excludedId")
  long sumTenths(
      @Param("lawyerId") UUID lawyerId,
      @Param("workDate") LocalDate workDate,
      @Param("statuses") Collection<TimeEntryJpaEntity.StatusColumn> statuses,
      @Param("excludedId") UUID excludedId);
}
