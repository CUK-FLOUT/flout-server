package com.flout.server.domain.notification.service;

import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FcmDeliveryWorker {
  private final FcmDeliveryStore store;
  private final PushGateway gateway;

  // DB 트랜잭션 밖에서 기기별 독립 발송
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void processOnce() {
    for (int i = 0; i < 10; i++) {
      var deliveries = store.claim();
      if (deliveries.isEmpty()) return;
      var delivery = deliveries.getFirst();
      try {
        String token = store.currentToken(delivery);
        if (token == null) {
          store.finish(delivery, "SKIPPED", "TOKEN_CHANGED", 0);
          continue;
        }
        gateway.send(token, delivery.type(), delivery.applicationId(), delivery.eventId());
        store.finish(delivery, "SENT", null, 0);
      } catch (PushFailure failure) {
        try {
          if (failure.getKind() == PushFailure.Kind.UNREGISTERED) {
            store.removeUnregistered(delivery);
          }
          boolean retry = failure.getKind() == PushFailure.Kind.RETRYABLE && delivery.attempt() < 3;
          long delay =
              Math.max(
                  failure.getRetryAfterSeconds(),
                  60L * (1L << (delivery.attempt() - 1))
                      + ThreadLocalRandom.current().nextLong(15));
          store.finish(
              delivery, retry ? "PENDING" : "FAILED", failure.getCode(), retry ? delay : 0);
          log.warn(
              "FCM 발송 실패. deliveryId={}, code={}, retry={}",
              delivery.id(),
              failure.getCode(),
              retry);
        } catch (RuntimeException persistenceFailure) {
          log.error(
              "FCM 실패 처리 저장 실패. deliveryId={}, failureType={}",
              delivery.id(),
              persistenceFailure.getClass().getSimpleName());
        }
      } catch (RuntimeException failure) {
        // 원격 성공 후 DB 오류는 선점 만료 복구로 재시도
        log.error(
            "FCM 작업 처리 실패. deliveryId={}, failureType={}",
            delivery.id(),
            failure.getClass().getSimpleName());
      }
    }
  }
}
