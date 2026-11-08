package com.emba.asuntoshoras.application.fakes;

import com.emba.asuntoshoras.application.port.out.TokenIssuer;
import com.emba.asuntoshoras.domain.user.User;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Fake: emite {@code token-<userId>} con vigencia de 2 h y registra a quién se emitió. */
public final class FakeTokenIssuer implements TokenIssuer {

  private final List<User> issuedTo = new ArrayList<>();

  @Override
  public IssuedToken issue(User user) {
    issuedTo.add(user);
    return new IssuedToken("token-" + user.id(), Duration.ofHours(2));
  }

  public List<User> issuedTo() {
    return issuedTo;
  }
}
