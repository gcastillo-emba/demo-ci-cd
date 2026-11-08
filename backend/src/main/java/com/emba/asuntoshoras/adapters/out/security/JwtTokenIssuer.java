package com.emba.asuntoshoras.adapters.out.security;

import com.emba.asuntoshoras.application.port.out.TokenIssuer;
import com.emba.asuntoshoras.domain.user.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/** Emite JWT HS256 con claims {@code sub} (userId) y {@code role}; sin PII (ADR-006). */
public class JwtTokenIssuer implements TokenIssuer {

  public static final String CLAIM_ROLE = "role";

  private final JwtEncoder encoder;
  private final Clock clock;
  private final Duration ttl;

  public JwtTokenIssuer(JwtEncoder encoder, Clock clock, Duration ttl) {
    this.encoder = encoder;
    this.clock = clock;
    this.ttl = ttl;
  }

  @Override
  public IssuedToken issue(User user) {
    Instant issuedAt = clock.instant();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(user.id().toString())
            .claim(CLAIM_ROLE, user.role().name())
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plus(ttl))
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    return new IssuedToken(token, ttl);
  }
}
