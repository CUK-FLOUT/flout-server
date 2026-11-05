package com.flout.server.domain.construction.service;

import com.flout.server.domain.construction.dto.request.SaveConstructionScheduleRequest;
import com.flout.server.domain.construction.dto.response.ConstructionScheduleResponse;
import com.flout.server.domain.construction.entity.ConstructionStatus;
import com.flout.server.domain.construction.entity.ConstructionStatusHistory;
import com.flout.server.domain.construction.repository.ConstructionRepository;
import com.flout.server.domain.construction.repository.ConstructionStatusHistoryRepository;
import com.flout.server.domain.construction.response.code.ConstructionErrorCode;
import com.flout.server.domain.supportapplication.entity.SupportApplicationStatus;
import com.flout.server.domain.supportapplication.repository.SupportApplicationRepository;
import com.flout.server.domain.supportapplication.service.SupportDecisionAccess;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConstructionScheduleService {
  private static final Pattern BUCHEON_ADDRESS =
      Pattern.compile("^(?:(?:경기도|경기)\\s+)?부천시(?:\\s|$)");
  private final SupportDecisionAccess access;
  private final SupportApplicationRepository supports;
  private final ConstructionRepository constructions;
  private final ConstructionStatusHistoryRepository histories;
  private final Clock inspectionClock;

  /** 승인 신청을 먼저 잠그고 시공 건을 잠근 뒤 상태와 확정 일정을 검증해 저장한다. */
  @Transactional
  public ConstructionScheduleResponse save(
      Long operatorId, Long id, SaveConstructionScheduleRequest request) {
    if (id == null || id <= 0)
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    var operator = access.requireOperator(operatorId);
    var supportId =
        constructions
            .findSupportApplicationId(id)
            .orElseThrow(() -> new BaseException(ConstructionErrorCode.NOT_FOUND));
    var support =
        supports
            .findForApproval(supportId)
            .orElseThrow(() -> new BaseException(ConstructionErrorCode.NOT_FOUND));
    if (!BUCHEON_ADDRESS.matcher(support.getAddress().strip()).find())
      throw new BaseException(ConstructionErrorCode.JURISDICTION_ACCESS_DENIED);
    if (support.getStatus() != SupportApplicationStatus.APPROVED)
      throw new BaseException(ConstructionErrorCode.SUPPORT_NOT_APPROVED);
    var construction =
        constructions
            .findForScheduleUpdate(id)
            .orElseThrow(() -> new BaseException(ConstructionErrorCode.NOT_FOUND));
    if (construction.getManager() != null
        || construction.getScheduledAt() != null
        || construction.getStatus() != ConstructionStatus.PENDING)
      throw new BaseException(ConstructionErrorCode.SCHEDULE_NOT_EDITABLE);
    var now = LocalDateTime.now(inspectionClock).truncatedTo(ChronoUnit.SECONDS);
    var scheduledAt = LocalDateTime.of(request.constructionDate(), request.constructionTime());
    if (scheduledAt.isBefore(now)) throw new BaseException(ConstructionErrorCode.PAST_SCHEDULE);
    construction.schedule(operator, scheduledAt, now);
    histories.saveAndFlush(ConstructionStatusHistory.changed(construction, operator, now));
    constructions.flush();
    return ConstructionScheduleResponse.from(construction);
  }
}
