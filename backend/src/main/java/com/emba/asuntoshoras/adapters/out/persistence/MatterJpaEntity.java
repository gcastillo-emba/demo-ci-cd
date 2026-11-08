package com.emba.asuntoshoras.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Fila de la tabla {@code matters} (V2__clients_matters.sql). {@code version} se incrementa en el
 * cierre condicional de {@link MatterJpaRepository#closeIfVersion} (bloqueo optimista).
 */
@Entity
@Table(name = "matters")
public class MatterJpaEntity {

  /** Valores de estado persistidos (CHECK en la migración). */
  public enum StatusColumn {
    OPEN,
    CLOSED
  }

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "code", nullable = false, unique = true, length = 12)
  private String code;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "client_id", nullable = false)
  private ClientJpaEntity client;

  @Column(name = "title", nullable = false, length = 200)
  private String title;

  @Column(name = "description", length = 2000)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 6)
  private StatusColumn status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "closed_at")
  private Instant closedAt;

  @Column(name = "version", nullable = false)
  private long version;

  protected MatterJpaEntity() {
    // requerido por JPA
  }

  public MatterJpaEntity(
      UUID id,
      String code,
      ClientJpaEntity client,
      String title,
      String description,
      StatusColumn status,
      Instant createdAt,
      Instant closedAt,
      long version) {
    this.id = id;
    this.code = code;
    this.client = client;
    this.title = title;
    this.description = description;
    this.status = status;
    this.createdAt = createdAt;
    this.closedAt = closedAt;
    this.version = version;
  }

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public ClientJpaEntity getClient() {
    return client;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public StatusColumn getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getClosedAt() {
    return closedAt;
  }

  public long getVersion() {
    return version;
  }
}
