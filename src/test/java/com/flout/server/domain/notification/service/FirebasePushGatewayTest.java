package com.flout.server.domain.notification.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.flout.server.domain.notification.entity.NotificationType;
import com.google.firebase.ErrorCode;
import com.google.firebase.IncomingHttpResponse;
import com.google.firebase.messaging.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class FirebasePushGatewayTest {
  // 알림 제목·내용 및 프론트 이동 데이터 매핑 검증
  @Test
  void mapsNotificationAndDataWithoutRealCredentials() throws Exception {
    var messaging = mock(FirebaseMessaging.class);
    var gateway = new FirebasePushGateway("");
    ReflectionTestUtils.setField(gateway, "messaging", messaging);
    gateway.send("device-token", NotificationType.INSPECTION_RESULT_PUBLISHED, 12L, "result:42");
    var message = ArgumentCaptor.forClass(Message.class);
    verify(messaging).send(message.capture());
    assertThat(ReflectionTestUtils.getField(message.getValue(), "token")).isEqualTo("device-token");
    assertThat(ReflectionTestUtils.getField(message.getValue(), "data"))
        .isEqualTo(
            Map.of(
                "notificationType",
                "INSPECTION_RESULT_PUBLISHED",
                "applicationId",
                "12",
                "target",
                "INSPECTION_RESULT",
                "eventId",
                "result:42"));
    var notification = ReflectionTestUtils.getField(message.getValue(), "notification");
    assertThat(ReflectionTestUtils.getField(notification, "title")).isEqualTo("현장점검 완료");
    assertThat(ReflectionTestUtils.getField(notification, "body")).isEqualTo("현장점검 결과가 등록됐어요.");
    gateway.close();
  }

  // SDK 오류 구분 및 Retry-After 헤더 반영 검증
  @Test
  void classifiesFailuresAndHonorsRetryAfter() throws Exception {
    var messaging = mock(FirebaseMessaging.class);
    var gateway = new FirebasePushGateway("");
    ReflectionTestUtils.setField(gateway, "messaging", messaging);
    for (var code :
        List.of(
            MessagingErrorCode.UNREGISTERED,
            MessagingErrorCode.INVALID_ARGUMENT,
            MessagingErrorCode.UNAVAILABLE,
            MessagingErrorCode.INTERNAL,
            MessagingErrorCode.QUOTA_EXCEEDED)) {
      var exception = mock(FirebaseMessagingException.class);
      var response = mock(IncomingHttpResponse.class);
      when(response.getHeaders()).thenReturn(Map.of("retry-after", List.of("180")));
      when(exception.getHttpResponse()).thenReturn(response);
      when(exception.getMessagingErrorCode()).thenReturn(code);
      when(exception.getErrorCode()).thenReturn(ErrorCode.UNKNOWN);
      doThrow(exception).when(messaging).send(any(Message.class));
      var expected =
          code == MessagingErrorCode.UNREGISTERED
              ? PushFailure.Kind.UNREGISTERED
              : code == MessagingErrorCode.INVALID_ARGUMENT
                  ? PushFailure.Kind.PERMANENT
                  : PushFailure.Kind.RETRYABLE;
      assertThatThrownBy(
              () -> gateway.send("device", NotificationType.INSPECTION_RECEIVED, 1L, "event"))
          .isInstanceOfSatisfying(
              PushFailure.class,
              failure -> {
                assertThat(failure.getKind()).isEqualTo(expected);
                assertThat(failure.getRetryAfterSeconds()).isEqualTo(180);
              });
    }
    gateway.close();
  }

  // 클라이언트 종료 후 재초기화 차단 검증
  @Test
  void refusesSendingAfterClose() {
    var gateway = new FirebasePushGateway("");
    gateway.close();
    assertThatThrownBy(
            () -> gateway.send("device", NotificationType.INSPECTION_RECEIVED, 1L, "event"))
        .isInstanceOfSatisfying(
            PushFailure.class,
            failure -> assertThat(failure.getKind()).isEqualTo(PushFailure.Kind.PERMANENT));
  }

  // 큐 저장 실패가 업무 호출자에게 전파되지 않음
  @Test
  void enqueueFailureDoesNotEscapeListener() {
    var store = mock(FcmDeliveryStore.class);
    doThrow(new IllegalStateException("database down")).when(store).enqueue(any());
    var listener = new FcmNotificationListener(store);
    assertThatCode(
            () ->
                listener.onRequested(
                    new PushNotificationRequested(
                        1L, NotificationType.INSPECTION_RECEIVED, 1L, "event")))
        .doesNotThrowAnyException();
  }
}
