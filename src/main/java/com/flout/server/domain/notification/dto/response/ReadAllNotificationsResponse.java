package com.flout.server.domain.notification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record ReadAllNotificationsResponse(
    @Schema(description = "이번 요청으로 읽음 처리된 알림 수", example = "4") int readCount,
    OffsetDateTime readAt) {}
