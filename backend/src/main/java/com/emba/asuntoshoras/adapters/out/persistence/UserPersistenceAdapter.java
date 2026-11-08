package com.emba.asuntoshoras.adapters.out.persistence;

import com.emba.asuntoshoras.application.port.out.UserRepository;
import com.emba.asuntoshoras.domain.user.Email;
import com.emba.asuntoshoras.domain.user.Role;
import com.emba.asuntoshoras.domain.user.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador de persistencia del puerto {@link UserRepository}. */
@Component
@Transactional(readOnly = true)
public class UserPersistenceAdapter implements UserRepository {

  private final UserJpaRepository repository;

  public UserPersistenceAdapter(UserJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public Optional<User> findByEmail(Email email) {
    return repository.findByEmail(email.value()).map(UserPersistenceAdapter::toDomain);
  }

  @Override
  public Optional<User> findById(UUID id) {
    return repository.findById(id).map(UserPersistenceAdapter::toDomain);
  }

  static User toDomain(UserJpaEntity entity) {
    return User.restore(
        entity.getId(),
        Email.of(entity.getEmail()),
        entity.getFullName(),
        entity.getPasswordHash(),
        Role.valueOf(entity.getRole().name()),
        entity.isActive());
  }
}
