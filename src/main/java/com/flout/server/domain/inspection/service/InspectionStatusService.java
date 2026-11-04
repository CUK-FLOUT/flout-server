package com.flout.server.domain.inspection.service;

import com.flout.server.domain.inspection.entity.*;
import com.flout.server.domain.inspection.repository.*;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InspectionStatusService {
  private final InspectionRepository inspections;
  private final InspectionStatusHistoryRepository histories;
  private final Clock inspectionClock;

  /** 방문 시간이 된 예정 점검만 진행으로 변경하고 자동 전환 이력을 저장합니다. */
  @Transactional
  public boolean startIfDue(Long inspectionId) {
    var inspection = inspections.findForStatusUpdate(inspectionId).orElse(null);
    LocalDateTime now = LocalDateTime.now(inspectionClock).truncatedTo(ChronoUnit.SECONDS);
    if (inspection == null
        || inspection.getStatus() != InspectionStatus.SCHEDULED
        || inspection.getScheduledAt() == null
        || inspection.getScheduledAt().isAfter(now)) return false;
    inspection.changeStatus(InspectionStatus.IN_PROGRESS, now);
    histories.saveAndFlush(InspectionStatusHistory.autoStarted(inspection, now));
    return true;
  }
}
