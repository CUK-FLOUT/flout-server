package com.flout.server.domain.notification.service;

import com.flout.server.domain.notification.dto.response.ReadAllNotificationsResponse;
import com.flout.server.domain.notification.dto.response.ResidentNotificationResponse;
import com.flout.server.domain.notification.dto.response.UnreadNotificationStatusResponse;
import com.flout.server.domain.notification.repository.ResidentNotificationRepository;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ResidentNotificationQueryService {
  private final UserRepository users;
  private final ResidentNotificationRepository notifications;

  @Transactional(readOnly = true)
  public List<ResidentNotificationResponse> getAll(Long userId) {
    requireResident(userId);
    return notifications.findAllByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
        .map(ResidentNotificationResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public UnreadNotificationStatusResponse getUnreadStatus(Long userId) {
    requireResident(userId);
    return new UnreadNotificationStatusResponse(
        notifications.existsByUserIdAndReadAtIsNull(userId));
  }

  @Transactional
  public ReadAllNotificationsResponse readAll(Long userId) {
    requireResident(userId);
    var readAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    int readCount = notifications.markAllUnreadAsRead(userId, readAt);
    return new ReadAllNotificationsResponse(
        readCount, readAt.atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime());
  }

  private void requireResident(Long userId) {
    var user =
        users
            .findById(userId)
            .orElseThrow(() -> new BaseException(GeneralErrorCode.UNAUTHORIZED_ERROR));
    if (user.getRole() != UserRole.RESIDENT) {
      throw new BaseException(GeneralErrorCode.ACCESS_DENIED_REQUEST);
    }
  }
}
