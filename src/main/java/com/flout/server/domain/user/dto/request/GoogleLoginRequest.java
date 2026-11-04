package com.flout.server.domain.user.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoogleLoginRequest(
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @NotBlank(message = "Google ID 토큰을 입력해주세요.") @Size(max = 16384, message = "Google ID 토큰은 16384자 이하여야 합니다.") @Schema(
            description =
                "Google Identity Services에서 받은 ID 토큰. 서비스 토큰이나 Google Access Token이 아닙니다.",
            example = "example-google-id-token",
            format = "password")
        String idToken) {
  @Override
  public String toString() {
    return "GoogleLoginRequest[REDACTED]";
  }
}
