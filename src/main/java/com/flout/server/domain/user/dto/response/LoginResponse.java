package com.flout.server.domain.user.dto.response;

import com.flout.server.domain.user.entity.UserRole;
import java.time.Instant;

public record LoginResponse(
    String accessToken,
    String tokenType,
    long expiresIn,
    Instant expiresAt,
    Long userId,
    String email,
    String name,
    UserRole role) {
  @Override
  public String toString() {
    return "LoginResponse[REDACTED]";
  }
}
