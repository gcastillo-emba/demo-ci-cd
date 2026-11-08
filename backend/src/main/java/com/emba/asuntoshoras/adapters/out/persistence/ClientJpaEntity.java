package com.emba.asuntoshoras.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Fila de la tabla {@code clients} (V2__clients_matters.sql). */
@Entity
@Table(name = "clients")
public class ClientJpaEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "name", nullable = false, length = 200)
  private String name;

  @Column(name = "tax_id", length = 50)
  private String taxId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected ClientJpaEntity() {
    // requerido por JPA
  }

  public ClientJpaEntity(UUID id, String name, String taxId, Instant createdAt) {
    this.id = id;
    this.name = name;
    this.taxId = taxId;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getTaxId() {
    return taxId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
