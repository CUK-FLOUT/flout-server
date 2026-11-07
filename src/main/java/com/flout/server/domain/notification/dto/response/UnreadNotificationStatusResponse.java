package com.flout.server.domain.notification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record UnreadNotificationStatusResponse(
    @Schema(description = "미확인 알림 존재 여부", example = "true") boolean hasUnread) {}
