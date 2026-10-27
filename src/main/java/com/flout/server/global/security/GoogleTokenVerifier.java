package com.flout.server.global.security;

import com.flout.server.domain.user.response.code.UserErrorCode;
import com.flout.server.global.exception.BaseException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

@Component
public class GoogleTokenVerifier {
  private final Set<String> clientIds;
  private final NimbusJwtDecoder decoder;

  @Autowired
  public GoogleTokenVerifier(@Value("${app.google.client-ids:}") String clientIds) {
    this(
        clientIds,
        NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs").build());
  }

  GoogleTokenVerifier(String clientIds, NimbusJwtDecoder decoder) {
    this.clientIds =
        Arrays.stream(clientIds.split(","))
            .map(String::strip)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
    this.decoder = decoder;
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(Duration.ZERO), this::validateClaims));
  }

  OAuth2TokenValidatorResult validateClaims(Jwt jwt) {
    boolean valid =
        jwt.getClaimAsString("iss") != null
            && Set.of("https://accounts.google.com", "accounts.google.com")
                .contains(jwt.getClaimAsString("iss"))
            && jwt.getAudience() != null
            && jwt.getAudience().stream().anyMatch(clientIds::contains)
            && (jwt.getAudience().size() == 1 || jwt.getClaimAsString("azp") != null)
            && (jwt.getClaimAsString("azp") == null
                || clientIds.contains(jwt.getClaimAsString("azp")))
            && jwt.getExpiresAt() != null
            && jwt.getIssuedAt() != null
            && !jwt.getIssuedAt().isAfter(java.time.Instant.now())
            && jwt.getSubject() != null
            && !jwt.getSubject().isBlank()
            && jwt.getSubject().length() <= 255
            && Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))
            && jwt.getClaimAsString("email") != null
            && !jwt.getClaimAsString("email").isBlank()
            && jwt.getClaimAsString("email").length() <= 100;
    return valid
        ? OAuth2TokenValidatorResult.success()
        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
  }

  public Jwt verify(String token) {
    if (clientIds.isEmpty()) throw new BaseException(UserErrorCode.GOOGLE_NOT_CONFIGURED);
    try {
      return decoder.decode(token);
    } catch (JwtException | IllegalArgumentException e) {
      throw new BaseException(UserErrorCode.GOOGLE_INVALID_TOKEN);
    }
  }
}
