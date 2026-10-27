package com.flout.server.domain.user.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoogleLoginRequest(
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @NotBlank(message = "구글 인증 토큰을 입력해주세요.") @Size(max = 16384, message = "구글 인증 토큰은 16384자 이하여야 합니다.") @Schema(
            description =
                "Google Identity Services에서 받은 ID 토큰. 서비스 토큰이나 Google Access Token이 아닙니다.",
            example = "example-google-id-token",
            format = "password")
        String idToken,
    @Schema(description = "신규 주민 가입 시 필수 개인정보 제3자 제공 동의. 기존 회원·운영자는 생략 가능", example = "true")
        Boolean thirdPartyProvisionAgreed,
    @Schema(description = "신규 주민 가입 시 필수 진행상태 알림 수신 동의. 기존 회원·운영자는 생략 가능", example = "true")
        Boolean statusNotificationAgreed) {
  @Override
  public String toString() {
    return "GoogleLoginRequest[REDACTED]";
  }
}
