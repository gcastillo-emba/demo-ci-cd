package com.emba.asuntoshoras.domain.client;

import com.emba.asuntoshoras.domain.shared.InvalidValueException;
import com.emba.asuntoshoras.domain.shared.Text;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado Cliente. {@code name} (1–200, recortado) es obligatorio; {@code taxId} (1–50, recortado)
 * es opcional y único entre clientes (la unicidad la garantiza la persistencia).
 */
public final class Client {

  public static final int NAME_MAX_LENGTH = 200;
  public static final int TAX_ID_MAX_LENGTH = 50;

  private final UUID id;
  private final String name;
  private final String taxId;
  private final Instant createdAt;

  private Client(UUID id, String name, String taxId, Instant createdAt) {
    if (id == null) {
      throw new InvalidValueException("id", "El identificador del cliente es obligatorio.");
    }
    if (createdAt == null) {
      throw new InvalidValueException("createdAt", "La fecha de creación es obligatoria.");
    }
    this.id = id;
    this.name = Text.required("name", name, NAME_MAX_LENGTH);
    this.taxId = Text.optionalNonBlank("taxId", taxId, TAX_ID_MAX_LENGTH);
    this.createdAt = createdAt;
  }

  /** Crea un cliente nuevo. */
  public static Client create(UUID id, String name, String taxId, Instant createdAt) {
    return new Client(id, name, taxId, createdAt);
  }

  /** Reconstruye un cliente existente (por ejemplo, desde persistencia). */
  public static Client restore(UUID id, String name, String taxId, Instant createdAt) {
    return new Client(id, name, taxId, createdAt);
  }

  public UUID id() {
    return id;
  }

  public String name() {
    return name;
  }

  /** Identificación tributaria o {@code null} si no se informó. */
  public String taxId() {
    return taxId;
  }

  public Instant createdAt() {
    return createdAt;
  }

  @Override
  public boolean equals(Object o) {
    return this == o || (o instanceof Client other && id.equals(other.id));
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "Client[id=" + id + "]";
  }
}
