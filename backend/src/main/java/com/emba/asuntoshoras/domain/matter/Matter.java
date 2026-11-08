package com.emba.asuntoshoras.domain.matter;

import com.emba.asuntoshoras.domain.shared.InvalidValueException;
import com.emba.asuntoshoras.domain.shared.Text;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado Asunto. Nace {@code OPEN} con un {@link MatterCode} generado por el servidor; el cierre
 * es irreversible ({@code OPEN → CLOSED}). {@code version} sostiene el bloqueo optimista del
 * cierre.
 */
public final class Matter {

  public static final int TITLE_MAX_LENGTH = 200;
  public static final int DESCRIPTION_MAX_LENGTH = 2000;

  private final UUID id;
  private final MatterCode code;
  private final UUID clientId;
  private final String title;
  private final String description;
  private final Instant createdAt;
  private final long version;
  private MatterStatus status;
  private Instant closedAt;

  private Matter(
      UUID id,
      MatterCode code,
      UUID clientId,
      String title,
      String description,
      MatterStatus status,
      Instant createdAt,
      Instant closedAt,
      long version) {
    if (id == null) {
      throw new InvalidValueException("id", "El identificador del asunto es obligatorio.");
    }
    if (code == null) {
      throw new InvalidValueException("code", "El código del asunto es obligatorio.");
    }
    if (clientId == null) {
      throw new InvalidValueException("clientId", "El cliente del asunto es obligatorio.");
    }
    if (status == null) {
      throw new InvalidValueException("status", "El estado del asunto es obligatorio.");
    }
    if (createdAt == null) {
      throw new InvalidValueException("createdAt", "La fecha de creación es obligatoria.");
    }
    if ((status == MatterStatus.CLOSED) != (closedAt != null)) {
      throw new InvalidValueException(
          "closedAt", "Un asunto tiene fecha de cierre si y solo si está cerrado.");
    }
    this.id = id;
    this.code = code;
    this.clientId = clientId;
    this.title = Text.required("title", title, TITLE_MAX_LENGTH);
    this.description = Text.optional("description", description, DESCRIPTION_MAX_LENGTH);
    this.status = status;
    this.createdAt = createdAt;
    this.closedAt = closedAt;
    this.version = version;
  }

  /** Abre un asunto nuevo: estado {@code OPEN}, sin fecha de cierre. */
  public static Matter open(
      UUID id,
      MatterCode code,
      UUID clientId,
      String title,
      String description,
      Instant createdAt) {
    return new Matter(
        id, code, clientId, title, description, MatterStatus.OPEN, createdAt, null, 0);
  }

  /** Reconstruye un asunto existente (por ejemplo, desde persistencia). */
  public static Matter restore(
      UUID id,
      MatterCode code,
      UUID clientId,
      String title,
      String description,
      MatterStatus status,
      Instant createdAt,
      Instant closedAt,
      long version) {
    return new Matter(id, code, clientId, title, description, status, createdAt, closedAt, version);
  }

  /**
   * Cierra el asunto.
   *
   * @throws MatterAlreadyClosedException si ya estaba cerrado (no modifica {@code closedAt})
   */
  public void close(Instant now) {
    if (now == null) {
      throw new InvalidValueException("closedAt", "El instante de cierre es obligatorio.");
    }
    if (status == MatterStatus.CLOSED) {
      throw new MatterAlreadyClosedException();
    }
    this.status = MatterStatus.CLOSED;
    this.closedAt = now;
  }

  public boolean isClosed() {
    return status == MatterStatus.CLOSED;
  }

  public UUID id() {
    return id;
  }

  public MatterCode code() {
    return code;
  }

  public UUID clientId() {
    return clientId;
  }

  public String title() {
    return title;
  }

  /** Descripción o {@code null} si no se informó. */
  public String description() {
    return description;
  }

  public MatterStatus status() {
    return status;
  }

  public Instant createdAt() {
    return createdAt;
  }

  /** Instante de cierre o {@code null} si está abierto. */
  public Instant closedAt() {
    return closedAt;
  }

  /** Versión leída de persistencia (bloqueo optimista). */
  public long version() {
    return version;
  }

  @Override
  public boolean equals(Object o) {
    return this == o || (o instanceof Matter other && id.equals(other.id));
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "Matter[id=" + id + ", code=" + code + ", status=" + status + "]";
  }
}
