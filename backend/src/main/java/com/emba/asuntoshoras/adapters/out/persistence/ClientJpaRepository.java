package com.emba.asuntoshoras.adapters.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repositorio Spring Data de {@link ClientJpaEntity}. */
public interface ClientJpaRepository
    extends JpaRepository<ClientJpaEntity, UUID>, JpaSpecificationExecutor<ClientJpaEntity> {

  boolean existsByTaxId(String taxId);
}
