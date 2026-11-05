package com.flout.server.domain.construction.service;

import com.flout.server.domain.construction.repository.ConstructionRepository;
import com.flout.server.domain.supportapplication.entity.SupportApplicationStatus;
import com.flout.server.domain.supportapplication.repository.SupportApplicationRepository;
import java.time.*;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class ConstructionStatusService {
  private final ConstructionRepository constructions;
  private final SupportApplicationRepository supports;
  private final Clock inspectionClock;

  /** 일정·사진·전체 완료와 동일한 지원 신청 → 시공 잠금 순서로 전환 직전 상태와 확정 시각을 재검증한다. */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public boolean startIfDue(Long id) {
    var supportId = constructions.findSupportApplicationId(id).orElse(null);
    if (supportId == null) return false;
    var support = supports.findForApproval(supportId).orElse(null);
    if (support == null || support.getStatus() != SupportApplicationStatus.APPROVED) return false;
    var construction = constructions.findForScheduleUpdate(id).orElse(null);
    if (construction == null) return false;
    return construction.startIfDue(
        LocalDateTime.now(inspectionClock).truncatedTo(ChronoUnit.SECONDS));
  }
}
