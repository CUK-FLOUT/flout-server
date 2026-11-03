package com.flout.server.domain.notification.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.flout.server.domain.notification.dto.request.RegisterFcmTokenRequest;
import com.flout.server.domain.notification.entity.NotificationType;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:fcm-delivery;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false",
      "app.fcm.enabled=false"
    })
class FcmDeliveryTest {
  @Autowired NotificationService notifications;
  @Autowired FcmTokenService tokens;
  @Autowired FcmDeliveryWorker worker;
  @Autowired FcmDeliveryStore store;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager manager;
  @MockitoBean PushGateway gateway;

  @MockitoBean(name = "fcmClock")
  Clock clock;

  AtomicReference<Instant> now;
  Long userId;
  Long otherId;

  @BeforeEach
  void setup() {
    jdbc.update("DELETE FROM fcm_deliveries");
    jdbc.update("DELETE FROM fcm_tokens");
    users.deleteAll();
    userId = users.saveAndFlush(User.resident("fcm-delivery@example.com", "hash", "주민")).getId();
    otherId =
        users.saveAndFlush(User.resident("other-delivery@example.com", "hash", "다른주민")).getId();
    now = new AtomicReference<>(Instant.parse("2026-11-02T00:00:00Z"));
    when(clock.instant()).thenAnswer(call -> now.get());
  }

  private void register(Long id, String token) {
    tokens.register(id, new RegisterFcmTokenRequest(token, null));
  }

  private void request(String event) {
    notifications.send(userId, NotificationType.INSPECTION_RECEIVED, 1L, event);
  }

  private String status() {
    return jdbc.queryForObject("SELECT status FROM fcm_deliveries", String.class);
  }

  // 업무 커밋 전 발송 금지 및 다중 기기 독립 발송 검증
  @Test
  void sendsAfterCommitOutsideTransactionOnlyToRecipientsDevices() {
    register(userId, "device-one");
    register(userId, "device-two");
    register(otherId, "other-device");
    doAnswer(
            call -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              assertThat(
                      jdbc.queryForObject(
                          "SELECT COUNT(*) FROM fcm_deliveries WHERE status='SENDING'",
                          Integer.class))
                  .isEqualTo(1);
              return null;
            })
        .when(gateway)
        .send(anyString(), any(), anyLong(), anyString());
    new TransactionTemplate(manager)
        .executeWithoutResult(
            tx -> {
              request("committed");
              assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fcm_deliveries", Integer.class))
                  .isZero();
              verifyNoInteractions(gateway);
            });
    worker.processOnce();
    assertThat(jdbc.queryForList("SELECT status FROM fcm_deliveries", String.class))
        .containsExactly("SENT", "SENT");
    verify(gateway).send("device-one", NotificationType.INSPECTION_RECEIVED, 1L, "committed");
    verify(gateway).send("device-two", NotificationType.INSPECTION_RECEIVED, 1L, "committed");
    verifyNoMoreInteractions(gateway);
  }

  // 업무 롤백 시 발송 대기 작업 생성 금지
  @Test
  void rollbackDoesNotEnqueue() {
    register(userId, "device");
    new TransactionTemplate(manager)
        .executeWithoutResult(
            tx -> {
              request("rolled-back");
              tx.setRollbackOnly();
            });
    worker.processOnce();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fcm_deliveries", Integer.class)).isZero();
    verifyNoInteractions(gateway);
  }

  // 토큰 미등록 시 발송 생략
  @Test
  void skipsUserWithoutDevices() {
    request("no-devices");
    worker.processOnce();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fcm_deliveries", Integer.class)).isZero();
    verifyNoInteractions(gateway);
  }

  // 같은 이벤트와 기기는 중복 발송 금지
  @Test
  void deduplicatesStableEventId() {
    register(userId, "device");
    request("same-event");
    request("same-event");
    worker.processOnce();
    worker.processOnce();
    assertThat(status()).isEqualTo("SENT");
    verify(gateway, times(1))
        .send("device", NotificationType.INSPECTION_RECEIVED, 1L, "same-event");
  }

  // 계정 전환 후 이전 사용자 알림 발송 차단
  @Test
  void skipsReassignedToken() {
    register(userId, "device");
    request("old-owner");
    register(otherId, "device");
    worker.processOnce();
    assertThat(status()).isEqualTo("SKIPPED");
    verifyNoInteractions(gateway);
  }

  // 등록 해제된 토큰만 삭제하고 다른 기기 발송 지속
  @Test
  void removesUnregisteredTokenAndContinuesOtherDevices() {
    register(userId, "invalid-device");
    register(userId, "valid-device");
    register(otherId, "other-device");
    doThrow(new PushFailure(PushFailure.Kind.UNREGISTERED, "UNREGISTERED", 0))
        .when(gateway)
        .send(eq("invalid-device"), any(), anyLong(), anyString());
    request("invalid-one");
    worker.processOnce();
    assertThat(jdbc.queryForList("SELECT token FROM fcm_tokens ORDER BY token", String.class))
        .containsExactly("other-device", "valid-device");
    assertThat(
            jdbc.queryForList(
                "SELECT status FROM fcm_deliveries ORDER BY delivery_id", String.class))
        .containsExactly("FAILED", "SENT");
  }

  // 메시지 오류를 토큰 오류로 오인해 삭제하지 않음
  @Test
  void preservesTokenOnInvalidPayload() {
    register(userId, "device");
    doThrow(new PushFailure(PushFailure.Kind.PERMANENT, "INVALID_ARGUMENT", 0))
        .when(gateway)
        .send(anyString(), any(), anyLong(), anyString());
    request("bad-payload");
    worker.processOnce();
    assertThat(status()).isEqualTo("FAILED");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fcm_tokens", Integer.class)).isEqualTo(1);
  }

  // Retry-After 이전 재발송 금지 및 재시도 성공 검증
  @Test
  void retriesTransientFailureAfterRequestedDelay() {
    register(userId, "device");
    doThrow(new PushFailure(PushFailure.Kind.RETRYABLE, "UNAVAILABLE", 180))
        .doNothing()
        .when(gateway)
        .send(anyString(), any(), anyLong(), anyString());
    request("retry");
    worker.processOnce();
    assertThat(status()).isEqualTo("PENDING");
    worker.processOnce();
    verify(gateway, times(1)).send(anyString(), any(), anyLong(), anyString());
    now.updateAndGet(time -> time.plusSeconds(181));
    worker.processOnce();
    assertThat(status()).isEqualTo("SENT");
    verify(gateway, times(2)).send(anyString(), any(), anyLong(), anyString());
  }

  // 세 차례 실패 후 무한 재시도 금지
  @Test
  void stopsAfterThreeAttempts() {
    register(userId, "device");
    doThrow(new PushFailure(PushFailure.Kind.RETRYABLE, "INTERNAL", 0))
        .when(gateway)
        .send(anyString(), any(), anyLong(), anyString());
    request("limit");
    for (int i = 0; i < 4; i++) {
      worker.processOnce();
      now.updateAndGet(time -> time.plusSeconds(600));
    }
    assertThat(status()).isEqualTo("FAILED");
    verify(gateway, times(3)).send(anyString(), any(), anyLong(), anyString());
  }

  // 중단된 발송 복구 및 오래된 작업자의 완료 덮어쓰기 차단
  @Test
  void recoversExpiredLeaseAndFencesStaleWorker() {
    register(userId, "device");
    request("lease");
    var claimed = store.claim().getFirst();
    assertThat(store.claim()).isEmpty();
    now.updateAndGet(time -> time.plusSeconds(601));
    worker.processOnce();
    store.finish(claimed, "FAILED", "STALE_WORKER", 0);
    assertThat(status()).isEqualTo("SENT");
    assertThat(jdbc.queryForObject("SELECT attempts FROM fcm_deliveries", Integer.class))
        .isEqualTo(2);
  }
}
