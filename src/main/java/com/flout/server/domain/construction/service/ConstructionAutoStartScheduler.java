package com.flout.server.domain.construction.service;

import com.flout.server.domain.construction.repository.ConstructionRepository;
import java.time.*;
import java.time.temporal.ChronoUnit;
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
    name = "app.construction.auto-start.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ConstructionAutoStartScheduler {
  private final ConstructionRepository constructions;
  private final ConstructionStatusService service;
  private final Clock inspectionClock;

  /** 도래한 예정 시공을 100건씩 전환하고 개별 실패는 다음 주기에 재시도한다. */
  @Scheduled(
      fixedDelayString = "${app.construction.auto-start.interval-millis:5000}",
      initialDelayString = "${app.construction.auto-start.interval-millis:5000}")
  public void startDueConstructions() {
    var now = LocalDateTime.now(inspectionClock).truncatedTo(ChronoUnit.SECONDS);
    for (Long id : constructions.findDueConstructionIds(now, PageRequest.of(0, 100))) {
      try {
        service.startIfDue(id);
      } catch (RuntimeException e) {
        log.warn(
            "시공 자동 진행 전환 실패: constructionId={}, causeType={}", id, e.getClass().getSimpleName());
      }
    }
  }
}
