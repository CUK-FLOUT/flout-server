package com.flout.server.domain.notification.dto.response;

import com.flout.server.domain.notification.entity.NotificationType;
import com.flout.server.domain.notification.entity.ResidentNotification;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Locale;

public record ResidentNotificationResponse(
    Long notificationId,
    NotificationType type,
    String title,
    String body,
    Long applicationId,
    @Schema(
            description = "화면 표시용 현장점검 신청 번호. 알림 종류와 관계없이 원본 applicationId를 최소 6자리로 표시",
            example = "000008")
        String receiptNumber,
    @Schema(description = "상세 화면 이동 대상", example = "CONSTRUCTION_RESULT") String target,
    boolean read,
    OffsetDateTime createdAt) {
  public static ResidentNotificationResponse from(ResidentNotification notification) {
    var type = notification.getType();
    return new ResidentNotificationResponse(
        notification.getId(),
        type,
        type.getTitle(),
        type.getBody(),
        notification.getApplicationId(),
        String.format(Locale.ROOT, "%06d", notification.getApplicationId()),
        type.getTarget(),
        notification.getReadAt() != null,
        notification.getCreatedAt().atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime());
  }
}
