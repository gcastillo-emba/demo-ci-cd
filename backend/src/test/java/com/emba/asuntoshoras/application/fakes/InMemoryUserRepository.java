package com.emba.asuntoshoras.application.fakes;

import com.emba.asuntoshoras.application.port.out.UserRepository;
import com.emba.asuntoshoras.domain.user.Email;
import com.emba.asuntoshoras.domain.user.User;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Fake en memoria del puerto {@link UserRepository} (sin Mockito, ADR-002). */
public final class InMemoryUserRepository implements UserRepository {

  private final Map<UUID, User> users = new LinkedHashMap<>();

  public InMemoryUserRepository with(User user) {
    users.put(user.id(), user);
    return this;
  }

  @Override
  public Optional<User> findByEmail(Email email) {
    return users.values().stream().filter(u -> u.email().equals(email)).findFirst();
  }

  @Override
  public Optional<User> findById(UUID id) {
    return Optional.ofNullable(users.get(id));
  }
}
