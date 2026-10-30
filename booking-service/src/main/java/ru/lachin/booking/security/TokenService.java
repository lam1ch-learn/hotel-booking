package ru.lachin.booking.security;

import java.time.*;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import ru.lachin.booking.api.UserDtos.Token;

@Service
public class TokenService {
  private final JwtEncoder encoder;
  private final String issuer, audience;
  private final Duration ttl;

  public TokenService(
      JwtEncoder encoder,
      @Value("${security.jwt.issuer}") String issuer,
      @Value("${security.jwt.audience}") String audience,
      @Value("${security.jwt.ttl}") Duration ttl) {
    this.encoder = encoder;
    this.issuer = issuer;
    this.audience = audience;
    this.ttl = ttl;
  }

  public Token issue(String subject, String role) {
    var now = Instant.now();
    var claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .audience(List.of(audience))
            .subject(subject)
            .issuedAt(now)
            .expiresAt(now.plus(ttl))
            .claim("roles", List.of(role))
            .build();
    return new Token(
        encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue(),
        "Bearer",
        ttl.toSeconds());
  }

  public String serviceToken() {
    return issue("booking-service", "SERVICE").accessToken();
  }
}
