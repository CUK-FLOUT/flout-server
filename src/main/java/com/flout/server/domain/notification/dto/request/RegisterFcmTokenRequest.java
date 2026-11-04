package com.flout.server.domain.notification.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterFcmTokenRequest(
    @NotBlank(message = "FCM 토큰은 필수입니다.") @Size(max = 2048, message = "FCM 토큰은 2048자 이하여야 합니다.") @Pattern(regexp = "[!-~]+", message = "FCM 토큰에는 공백이나 제어문자를 포함할 수 없습니다.") @Schema(
            description = "브라우저 또는 앱에서 발급받은 FCM 토큰. 공백 없는 ASCII 문자열, 최대 2048자",
            example = "example-fcm-token",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String token,
    @Size(min = 1, max = 2048, message = "이전 FCM 토큰은 1~2048자여야 합니다.") @Pattern(regexp = "[!-~]+", message = "이전 FCM 토큰에는 공백이나 제어문자를 포함할 수 없습니다.") @Schema(
            description = "기기 토큰 갱신 시 교체할 이전 토큰. 생략 또는 null이면 기존 다른 토큰 유지",
            example = "previous-example-fcm-token",
            nullable = true)
        String previousToken) {}
