package com.flout.server.domain.user.dto.response;

import com.flout.server.domain.user.entity.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record LoginResponse(
    String accessToken,
    String tokenType,
    long expiresIn,
    Instant expiresAt,
    Long userId,
    String email,
    String name,
    UserRole role,
    @Schema(description = "토큰 재발급용 비밀값. 회전 시 교체 필요", example = "example-refresh-token")
        String refreshToken,
    @Schema(description = "Refresh Token 유효 기간(초)", example = "604800") long refreshExpiresIn,
    @Schema(
            description = "Refresh Token 만료 시각(UTC)",
            format = "date-time",
            example = "2026-10-28T13:30:00Z")
        Instant refreshExpiresAt,
    @Schema(
            description =
                "주민의 현재 버전 필수 정보 제공 동의 필요 여부. true면 동의 화면으로 이동하고 false면 동의 완료입니다. 운영자는 항상 false입니다.",
            example = "true")
        boolean requiresConsent) {
  @Override
  public String toString() {
    return "LoginResponse[REDACTED]";
  }
}
