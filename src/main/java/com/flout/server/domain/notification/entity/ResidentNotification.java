package com.flout.server.domain.notification.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResidentNotification {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "notification_id")
  private Long id;

  @Column(name = "event_id", nullable = false, unique = true, length = 128)
  private String eventId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "notification_type", nullable = false, length = 40)
  private NotificationType type;

  @Column(name = "application_id", nullable = false)
  private Long applicationId;

  @Column(name = "read_at")
  private LocalDateTime readAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  public static ResidentNotification create(
      Long userId, NotificationType type, Long applicationId, String eventId) {
    var notification = new ResidentNotification();
    notification.userId = userId;
    notification.type = type;
    notification.applicationId = applicationId;
    notification.eventId = eventId;
    return notification;
  }

  @PrePersist
  void onCreate() {
    createdAt = LocalDateTime.now();
  }
}
