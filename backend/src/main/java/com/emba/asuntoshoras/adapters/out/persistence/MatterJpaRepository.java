package com.emba.asuntoshoras.adapters.out.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio Spring Data de {@link MatterJpaEntity}. */
public interface MatterJpaRepository
    extends JpaRepository<MatterJpaEntity, UUID>, JpaSpecificationExecutor<MatterJpaEntity> {

  /** Asunto con su cliente en una sola consulta (sin N+1). */
  @Query("select m from MatterJpaEntity m join fetch m.client where m.id = :id")
  Optional<MatterJpaEntity> findWithClientById(@Param("id") UUID id);

  /**
   * Cierra el asunto solo si sigue abierto y con la versión leída.
   *
   * @return filas actualizadas (0 si otro proceso lo modificó)
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update MatterJpaEntity m set m.status = :closed, m.closedAt = :closedAt,"
          + " m.version = m.version + 1"
          + " where m.id = :id and m.version = :version and m.status = :open")
  int closeIfVersion(
      @Param("id") UUID id,
      @Param("version") long version,
      @Param("closedAt") Instant closedAt,
      @Param("open") MatterJpaEntity.StatusColumn open,
      @Param("closed") MatterJpaEntity.StatusColumn closed);
}
