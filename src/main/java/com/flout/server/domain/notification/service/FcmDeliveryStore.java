package com.flout.server.domain.notification.service;

import com.flout.server.domain.notification.entity.NotificationType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class FcmDeliveryStore {
  private final JdbcTemplate jdbc;
  private final Clock clock;
  private final TransactionTemplate transactions;

  public FcmDeliveryStore(
      JdbcTemplate jdbc, PlatformTransactionManager manager, @Qualifier("fcmClock") Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.transactions = new TransactionTemplate(manager);
    transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  private LocalDateTime now() {
    return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }

  // 등록된 기기별 대기 작업 저장 및 이벤트 중복 방지
  public void enqueue(PushNotificationRequested event) {
    transactions.executeWithoutResult(
        status -> {
          var ids =
              jdbc.queryForList(
                  "SELECT token_id FROM fcm_tokens WHERE user_id = ? ORDER BY token_id",
                  Long.class,
                  event.userId());
          for (Long id : ids) {
            jdbc.update(
                """
            INSERT INTO fcm_deliveries
              (event_id,user_id,token_id,notification_type,application_id,next_attempt_at,created_at,updated_at)
            VALUES (?,?,?,?,?,?,?,?)
            ON DUPLICATE KEY UPDATE event_id = event_id
            """,
                event.eventId(),
                event.userId(),
                id,
                event.type().name(),
                event.applicationId(),
                now(),
                now(),
                now());
          }
        });
  }

  // 짧은 DB 잠금으로 발송 작업 선점 및 중단된 작업 복구
  public List<Delivery> claim() {
    return transactions.execute(
        status -> {
          var time = now();
          jdbc.update(
              """
          UPDATE fcm_deliveries SET status='FAILED',last_error='LEASE_EXHAUSTED',updated_at=?
          WHERE status='SENDING' AND next_attempt_at<=? AND attempts>=3
          """,
              time,
              time);
          var deliveries =
              jdbc.query(
                  """
          SELECT delivery_id,event_id,user_id,token_id,notification_type,application_id,attempts
          FROM fcm_deliveries WHERE status IN ('PENDING','SENDING') AND next_attempt_at<=? AND attempts<3
          ORDER BY next_attempt_at,delivery_id LIMIT 1 FOR UPDATE
          """,
                  (rs, row) ->
                      new Delivery(
                          rs.getLong("delivery_id"),
                          rs.getString("event_id"),
                          rs.getLong("user_id"),
                          rs.getLong("token_id"),
                          NotificationType.valueOf(rs.getString("notification_type")),
                          rs.getLong("application_id"),
                          rs.getInt("attempts") + 1),
                  time);
          for (var delivery : deliveries) {
            jdbc.update(
                "UPDATE fcm_deliveries SET status='SENDING',attempts=?,next_attempt_at=?,updated_at=? WHERE delivery_id=?",
                delivery.attempt(),
                time.plusMinutes(10),
                time,
                delivery.id());
          }
          return deliveries;
        });
  }

  // 계정 전환·토큰 교체 후 이전 사용자 발송 차단
  public String currentToken(Delivery delivery) {
    return transactions.execute(
        status -> {
          var rows =
              jdbc.queryForList(
                  "SELECT token FROM fcm_tokens WHERE token_id=? AND user_id=?",
                  String.class,
                  delivery.tokenId(),
                  delivery.userId());
          return rows.isEmpty() ? null : rows.getFirst();
        });
  }

  // 선점 회차가 같은 작업만 완료 처리
  public void finish(Delivery delivery, String state, String error, long retrySeconds) {
    transactions.executeWithoutResult(
        status ->
            jdbc.update(
                """
        UPDATE fcm_deliveries SET status=?,last_error=?,next_attempt_at=?,updated_at=?
        WHERE delivery_id=? AND status='SENDING' AND attempts=?
        """,
                state,
                error,
                now().plusSeconds(retrySeconds),
                now(),
                delivery.id(),
                delivery.attempt()));
  }

  // 같은 사용자에게 남아 있는 무효 토큰만 삭제
  public void removeUnregistered(Delivery delivery) {
    transactions.executeWithoutResult(
        status ->
            jdbc.update(
                "DELETE FROM fcm_tokens WHERE token_id=? AND user_id=?",
                delivery.tokenId(),
                delivery.userId()));
  }

  public record Delivery(
      Long id,
      String eventId,
      Long userId,
      Long tokenId,
      NotificationType type,
      Long applicationId,
      int attempt) {}
}
