package com.emba.asuntoshoras.application.port.out;

import com.emba.asuntoshoras.domain.user.Email;
import com.emba.asuntoshoras.domain.user.User;
import java.util.Optional;
import java.util.UUID;

/** Puerto de salida: lectura de usuarios. */
public interface UserRepository {

  Optional<User> findByEmail(Email email);

  Optional<User> findById(UUID id);
}
