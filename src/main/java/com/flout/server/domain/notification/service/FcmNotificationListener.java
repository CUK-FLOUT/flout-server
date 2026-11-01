package com.flout.server.domain.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class FcmNotificationListener {
  private final FcmDeliveryStore store;

  // 업무 커밋 후 대기 작업 저장 및 업무 응답과 실패 분리
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onRequested(PushNotificationRequested event) {
    try {
      store.enqueue(event);
    } catch (RuntimeException failure) {
      log.error(
          "FCM 대기 작업 저장 실패. eventId={}, failureType={}",
          event.eventId(),
          failure.getClass().getSimpleName());
    }
  }
}
