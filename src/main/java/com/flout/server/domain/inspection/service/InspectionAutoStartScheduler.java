package com.flout.server.domain.inspection.service;

import com.flout.server.domain.inspection.repository.InspectionRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
    name = "app.inspection.auto-start.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class InspectionAutoStartScheduler {
  private final InspectionRepository inspections;
  private final InspectionStatusService service;
  private final Clock inspectionClock;

  // 방문 시각이 지난 예정 점검 전환 및 실패 건 재시도
  @Scheduled(
      fixedDelayString = "${app.inspection.auto-start.interval-millis:5000}",
      initialDelayString = "${app.inspection.auto-start.interval-millis:5000}")
  public void startDueInspections() {
    for (Long id :
        inspections.findDueInspectionIds(
            LocalDateTime.now(inspectionClock), PageRequest.of(0, 100))) {
      try {
        service.startIfDue(id);
      } catch (RuntimeException e) {
        log.warn("점검 자동 진행 전환 실패: inspectionId={}, causeType={}", id, e.getClass().getSimpleName());
      }
    }
  }
}
