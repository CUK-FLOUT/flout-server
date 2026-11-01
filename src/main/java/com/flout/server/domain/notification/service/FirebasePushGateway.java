package com.flout.server.domain.notification.service;

import com.flout.server.domain.notification.entity.NotificationType;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FirebasePushGateway implements PushGateway {
  private final String projectId;
  private FirebaseApp app;
  private FirebaseMessaging messaging;
  private boolean closed;

  public FirebasePushGateway(@Value("${app.fcm.project-id:}") String projectId) {
    this.projectId = projectId;
  }

  // 최초 발송 시에만 서버 인증 및 Firebase 클라이언트 생성
  private synchronized FirebaseMessaging messaging() throws IOException {
    if (closed) throw new IllegalStateException("Firebase gateway is closed");
    if (messaging == null) {
      var options =
          FirebaseOptions.builder()
              .setCredentials(GoogleCredentials.getApplicationDefault())
              .setConnectTimeout(5000)
              .setReadTimeout(10000)
              .setWriteTimeout(10000);
      if (!projectId.isBlank()) options.setProjectId(projectId);
      app = FirebaseApp.initializeApp(options.build(), "flout-fcm-" + UUID.randomUUID());
      messaging = FirebaseMessaging.getInstance(app);
    }
    return messaging;
  }

  // 알림 문구와 프론트 이동 정보 발송
  @Override
  public void send(String token, NotificationType type, Long applicationId, String eventId) {
    try {
      messaging()
          .send(
              Message.builder()
                  .setToken(token)
                  .setNotification(
                      Notification.builder()
                          .setTitle(type.getTitle())
                          .setBody(type.getBody())
                          .build())
                  .putAllData(
                      Map.of(
                          "notificationType",
                          type.name(),
                          "applicationId",
                          applicationId.toString(),
                          "target",
                          type.getTarget(),
                          "eventId",
                          eventId))
                  .build());
    } catch (FirebaseMessagingException exception) {
      var code = exception.getMessagingErrorCode();
      String name = code == null ? exception.getErrorCode().name() : code.name();
      var kind =
          code == MessagingErrorCode.UNREGISTERED
              ? PushFailure.Kind.UNREGISTERED
              : code == MessagingErrorCode.UNAVAILABLE
                      || code == MessagingErrorCode.INTERNAL
                      || code == MessagingErrorCode.QUOTA_EXCEEDED
                      || exception.getErrorCode() == ErrorCode.UNAVAILABLE
                      || exception.getErrorCode() == ErrorCode.DEADLINE_EXCEEDED
                      || exception.getErrorCode() == ErrorCode.INTERNAL
                      || exception.getErrorCode() == ErrorCode.RESOURCE_EXHAUSTED
                  ? PushFailure.Kind.RETRYABLE
                  : PushFailure.Kind.PERMANENT;
      throw new PushFailure(kind, name, retryAfter(exception));
    } catch (IOException exception) {
      throw new PushFailure(PushFailure.Kind.PERMANENT, "FIREBASE_CREDENTIALS_UNAVAILABLE", 0);
    } catch (IllegalArgumentException | IllegalStateException exception) {
      throw new PushFailure(PushFailure.Kind.PERMANENT, "FIREBASE_CONFIGURATION_INVALID", 0);
    }
  }

  // 서버가 지정한 재시도 대기시간 우선 적용
  private long retryAfter(FirebaseMessagingException exception) {
    var response = exception.getHttpResponse();
    if (response == null) return 0;
    Object header =
        response.getHeaders().entrySet().stream()
            .filter(entry -> entry.getKey().equalsIgnoreCase("Retry-After"))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    if (header instanceof java.util.List<?> values)
      header = values.isEmpty() ? null : values.getFirst();
    String value = header == null ? null : header.toString();
    if (value == null) return 0;
    try {
      return Math.max(0, Long.parseLong(value));
    } catch (NumberFormatException ignored) {
      try {
        return Math.max(
            0,
            Duration.between(
                    Instant.now(),
                    ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant())
                .getSeconds());
      } catch (java.time.format.DateTimeParseException invalidDate) {
        return 0;
      }
    }
  }

  // 서버 종료 시 소유한 Firebase 자원 해제
  @PreDestroy
  synchronized void close() {
    closed = true;
    if (app != null) app.delete();
  }
}
