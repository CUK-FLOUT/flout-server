package com.flout.server.domain.user.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record RefreshTokenRequest(
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @NotBlank(message = "리프레시 토큰을 입력해주세요.") @Size(max = 512) @Schema(
            description = "로그인 또는 직전 재발급 응답의 refreshToken",
            example = "example-refresh-token",
            format = "password")
        String refreshToken) {
  @Override
  public String toString() {
    return "RefreshTokenRequest[REDACTED]";
  }
}
