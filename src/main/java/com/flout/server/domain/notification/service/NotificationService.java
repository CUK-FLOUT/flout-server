package com.flout.server.domain.notification.service;

import com.flout.server.domain.notification.entity.NotificationType;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {
  private final ApplicationEventPublisher events;
  private final JdbcTemplate jdbc;

  // 매 호출을 새로운 알림으로 요청
  @Transactional
  public void send(Long userId, NotificationType type, Long applicationId) {
    send(userId, type, applicationId, UUID.randomUUID().toString());
  }

  // 같은 업무 이벤트 재호출 시 동일한 이벤트 ID 사용
  @Transactional
  public void send(Long userId, NotificationType type, Long applicationId, String eventId) {
    if (userId == null
        || userId <= 0
        || applicationId == null
        || applicationId <= 0
        || type == null
        || eventId == null
        || !eventId.matches("[A-Za-z0-9_.:-]{1,128}")) {
      throw new IllegalArgumentException("알림 대상·종류·신청 ID·이벤트 ID를 확인해주세요.");
    }
    jdbc.update(
        """
        INSERT INTO notifications (event_id,user_id,notification_type,application_id)
        VALUES (?,?,?,?)
        ON DUPLICATE KEY UPDATE event_id = event_id
        """,
        eventId,
        userId,
        type.name(),
        applicationId);
    events.publishEvent(new PushNotificationRequested(userId, type, applicationId, eventId));
  }
}
