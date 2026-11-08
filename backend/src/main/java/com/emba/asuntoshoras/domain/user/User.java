package com.emba.asuntoshoras.domain.user;

import com.emba.asuntoshoras.domain.shared.InvalidValueException;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado Usuario. El hash de contraseña nunca aparece en {@link #toString()} ni sale del backend.
 */
public final class User {

  public static final int FULL_NAME_MAX_LENGTH = 120;

  private final UUID id;
  private final Email email;
  private final String fullName;
  private final String passwordHash;
  private final Role role;
  private final boolean active;

  private User(
      UUID id, Email email, String fullName, String passwordHash, Role role, boolean active) {
    if (id == null) {
      throw new InvalidValueException("id", "El identificador del usuario es obligatorio.");
    }
    if (email == null) {
      throw new InvalidValueException("email", "El email es obligatorio.");
    }
    if (fullName == null || fullName.isBlank()) {
      throw new InvalidValueException("fullName", "El nombre completo es obligatorio.");
    }
    if (fullName.trim().length() > FULL_NAME_MAX_LENGTH) {
      throw new InvalidValueException(
          "fullName", "El nombre completo no puede superar 120 caracteres.");
    }
    if (passwordHash == null || passwordHash.isBlank()) {
      throw new InvalidValueException("passwordHash", "El hash de contraseña es obligatorio.");
    }
    if (role == null) {
      throw new InvalidValueException("role", "El rol es obligatorio.");
    }
    this.id = id;
    this.email = email;
    this.fullName = fullName.trim();
    this.passwordHash = passwordHash;
    this.role = role;
    this.active = active;
  }

  /** Reconstruye un usuario existente (por ejemplo, desde persistencia). */
  public static User restore(
      UUID id, Email email, String fullName, String passwordHash, Role role, boolean active) {
    return new User(id, email, fullName, passwordHash, role, active);
  }

  public UUID id() {
    return id;
  }

  public Email email() {
    return email;
  }

  public String fullName() {
    return fullName;
  }

  public String passwordHash() {
    return passwordHash;
  }

  public Role role() {
    return role;
  }

  public boolean isActive() {
    return active;
  }

  public boolean isPartner() {
    return role.isPartner();
  }

  @Override
  public boolean equals(Object o) {
    return this == o || (o instanceof User other && id.equals(other.id));
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "User[id=" + id + ", role=" + role + ", active=" + active + "]";
  }
}
