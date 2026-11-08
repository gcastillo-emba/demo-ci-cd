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
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Fila de la tabla {@code time_entries} (V3__time_entries.sql). Las horas se guardan en décimas
 * ({@code hours_tenths}, design D1) y {@code version} es el bloqueo optimista de JPA (design D4).
 */
@Entity
@Table(name = "time_entries")
public class TimeEntryJpaEntity {

  /** Valores de estado persistidos (CHECK en la migración). */
  public enum StatusColumn {
    PENDING,
    APPROVED,
    REJECTED
  }

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "matter_id", nullable = false)
  private MatterJpaEntity matter;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "lawyer_id", nullable = false, updatable = false)
  private UserJpaEntity lawyer;

  @Column(name = "work_date", nullable = false)
  private LocalDate workDate;

  @Column(name = "hours_tenths", nullable = false)
  private short hoursTenths;

  @Column(name = "description", nullable = false, length = 2000)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 8)
  private StatusColumn status;

  @Column(name = "rejection_reason", length = 500)
  private String rejectionReason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  protected TimeEntryJpaEntity() {
    // requerido por JPA
  }

  public TimeEntryJpaEntity(
      UUID id,
      MatterJpaEntity matter,
      UserJpaEntity lawyer,
      LocalDate workDate,
      short hoursTenths,
      String description,
      StatusColumn status,
      String rejectionReason,
      Instant createdAt) {
    this.id = id;
    this.matter = matter;
    this.lawyer = lawyer;
    this.workDate = workDate;
    this.hoursTenths = hoursTenths;
    this.description = description;
    this.status = status;
    this.rejectionReason = rejectionReason;
    this.createdAt = createdAt;
  }

  /** Aplica los campos mutables (la versión la incrementa Hibernate al hacer flush). */
  public void apply(
      MatterJpaEntity matter,
      LocalDate workDate,
      short hoursTenths,
      String description,
      StatusColumn status,
      String rejectionReason) {
    this.matter = matter;
    this.workDate = workDate;
    this.hoursTenths = hoursTenths;
    this.description = description;
    this.status = status;
    this.rejectionReason = rejectionReason;
  }

  public UUID getId() {
    return id;
  }

  public MatterJpaEntity getMatter() {
    return matter;
  }

  public UserJpaEntity getLawyer() {
    return lawyer;
  }

  public LocalDate getWorkDate() {
    return workDate;
  }

  public short getHoursTenths() {
    return hoursTenths;
  }

  public String getDescription() {
    return description;
  }

  public StatusColumn getStatus() {
    return status;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public long getVersion() {
    return version;
  }
}
