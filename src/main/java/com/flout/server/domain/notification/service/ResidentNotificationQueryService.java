package com.flout.server.domain.notification.service;

import com.flout.server.domain.notification.dto.response.ReadAllNotificationsResponse;
import com.flout.server.domain.notification.dto.response.ResidentNotificationResponse;
import com.flout.server.domain.notification.dto.response.UnreadNotificationStatusResponse;
import com.flout.server.domain.notification.repository.ResidentNotificationRepository;
import com.flout.server.domain.user.service.ResidentAccountAccess;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResidentNotificationQueryService {
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private final ResidentAccountAccess access;
  private final ResidentNotificationRepository notifications;

  public List<ResidentNotificationResponse> getAll(Long userId) {
    access.requireResident(userId);
    // 생성 시각이 같은 알림은 ID 내림차순으로 정렬한다.
    return notifications.findAllByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
        .map(ResidentNotificationResponse::from)
        .toList();
  }

  public UnreadNotificationStatusResponse getUnreadStatus(Long userId) {
    access.requireResident(userId);
    return new UnreadNotificationStatusResponse(
        notifications.existsByUserIdAndReadAtIsNull(userId));
  }

  @Transactional
  public ReadAllNotificationsResponse readAll(Long userId) {
    access.requireResident(userId);
    // 이번 요청으로 읽는 알림에 같은 시각 적용
    var readAt = OffsetDateTime.now(SEOUL);
    int readCount = notifications.markAllUnreadAsRead(userId, readAt.toLocalDateTime());
    return new ReadAllNotificationsResponse(readCount, readAt);
  }
}
