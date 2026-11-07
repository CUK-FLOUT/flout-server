package com.flout.server.domain.notification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record ReadAllNotificationsResponse(
    @Schema(description = "읽음 처리를 요청할 때 기준으로 사용한 알림 ID", example = "3") Long notificationId,
    @Schema(description = "이번 요청으로 읽음 처리된 알림 수", example = "4") int readCount,
    OffsetDateTime readAt) {}
