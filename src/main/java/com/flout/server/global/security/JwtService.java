package com.flout.server.global.security;

import com.flout.server.domain.user.dto.response.LoginResponse;
import com.flout.server.domain.user.entity.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private final JwtEncoder encoder;
  private final long expiresIn;
  private final String issuer;

  public JwtService(
      JwtEncoder encoder,
      @Value("${app.jwt.access-token-seconds:3600}") long expiresIn,
      @Value("${app.jwt.issuer:flout}") String issuer) {
    if (expiresIn <= 0 || expiresIn > 86400) {
      throw new IllegalArgumentException("액세스 토큰 유효 시간은 1~86400초여야 합니다.");
    }
    this.encoder = encoder;
    this.expiresIn = expiresIn;
    this.issuer = issuer;
  }

  public LoginResponse issue(User user) {
    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    Instant expiresAt = now.plusSeconds(expiresIn);
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(user.getId().toString())
            .issuedAt(now)
            .expiresAt(expiresAt)
            .claim("role", user.getRole().name())
            .build();
    String token =
        encoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    return new LoginResponse(
        token,
        "Bearer",
        expiresIn,
        expiresAt,
        user.getId(),
        user.getEmail(),
        user.getName(),
        user.getRole());
  }
}
