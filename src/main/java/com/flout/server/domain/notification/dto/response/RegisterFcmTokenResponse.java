package com.flout.server.domain.notification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record RegisterFcmTokenResponse(
    @Schema(description = "등록 또는 갱신된 토큰의 내부 ID. 토큰 원문은 반환하지 않음", example = "1") Long tokenId) {}
