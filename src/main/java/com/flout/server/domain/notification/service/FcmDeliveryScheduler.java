package com.flout.server.domain.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.fcm.enabled", havingValue = "true", matchIfMissing = true)
public class FcmDeliveryScheduler {
  private final FcmDeliveryWorker worker;

  // 전용 스케줄러에서 대기·재시도 작업 처리
  @Scheduled(
      scheduler = "fcmTaskScheduler",
      initialDelayString = "${app.fcm.interval-millis:5000}",
      fixedDelayString = "${app.fcm.interval-millis:5000}")
  public void dispatch() {
    try {
      worker.processOnce();
    } catch (RuntimeException failure) {
      log.error("FCM 대기 작업 조회 실패. failureType={}", failure.getClass().getSimpleName());
    }
  }
}
