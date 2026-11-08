package com.emba.asuntoshoras.application.port.out;

import com.emba.asuntoshoras.domain.user.User;
import java.time.Duration;

/** Puerto de salida: emisión de access tokens. */
public interface TokenIssuer {

  IssuedToken issue(User user);

  /** Token emitido y su vigencia. */
  record IssuedToken(String value, Duration expiresIn) {}
}
