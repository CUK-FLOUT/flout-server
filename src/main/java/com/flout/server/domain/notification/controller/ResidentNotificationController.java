package com.flout.server.domain.notification.controller;

import com.flout.server.domain.notification.dto.response.ReadAllNotificationsResponse;
import com.flout.server.domain.notification.dto.response.ResidentNotificationResponse;
import com.flout.server.domain.notification.dto.response.UnreadNotificationStatusResponse;
import com.flout.server.domain.notification.response.code.NotificationSuccessCode;
import com.flout.server.domain.notification.service.ResidentNotificationQueryService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/resident/notifications")
@Tag(name = "주민-알림", description = "주민 알림함 조회 및 읽음 처리")
public class ResidentNotificationController {
  private final ResidentNotificationQueryService service;

  @GetMapping
  @Operation(
      summary = "알림 목록 조회",
      description =
          "로그인한 주민의 알림을 생성 시각과 알림 ID 내림차순으로 모두 반환합니다. receiptNumber는 모든 단계에서 원본 현장점검 applicationId를 최소 6자리로 표시하며 상세 이동에는 applicationId와 target을 사용합니다.")
  public SuccessResponse<List<ResidentNotificationResponse>> getAll(
      @AuthenticationPrincipal Jwt jwt) {
    return SuccessResponse.of(
        service.getAll(Long.valueOf(jwt.getSubject())),
        NotificationSuccessCode.NOTIFICATION_LIST_FOUND);
  }

  @GetMapping("/unread-status")
  @Operation(
      summary = "미확인 알림 여부 조회",
      description = "로그인한 주민에게 읽지 않은 알림이 하나 이상 있는지 조회합니다. 알림이 없거나 모두 읽은 경우 false입니다.")
  public SuccessResponse<UnreadNotificationStatusResponse> getUnreadStatus(
      @AuthenticationPrincipal Jwt jwt) {
    return SuccessResponse.of(
        service.getUnreadStatus(Long.valueOf(jwt.getSubject())),
        NotificationSuccessCode.UNREAD_STATUS_FOUND);
  }

  @PatchMapping("/{notificationId}/read-all")
  @Operation(
      summary = "전체 알림 읽음 처리",
      description =
          "경로의 알림이 로그인한 주민의 것인지 확인한 후 해당 주민의 미확인 알림 전체를 읽음 처리합니다. 이미 모두 읽은 경우 readCount는 0입니다.")
  public SuccessResponse<ReadAllNotificationsResponse> readAll(
      @AuthenticationPrincipal Jwt jwt, @PathVariable Long notificationId) {
    return SuccessResponse.of(
        service.readAll(Long.valueOf(jwt.getSubject()), notificationId),
        NotificationSuccessCode.NOTIFICATIONS_READ_ALL);
  }
}
