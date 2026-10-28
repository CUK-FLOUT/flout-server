package com.flout.server.domain.inspection.service;

import com.flout.server.domain.inspection.dto.response.InspectionStatusResponse;
import com.flout.server.domain.inspection.entity.*;
import com.flout.server.domain.inspection.repository.*;
import com.flout.server.domain.inspection.response.code.InspectionErrorCode;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InspectionStatusService {
  private static final Pattern BUCHEON_ADDRESS =
      Pattern.compile("^(?:(?:경기도|경기)\\s+)?부천시(?:\\s|$)");
  private final UserRepository users;
  private final InspectionRepository inspections;
  private final InspectionStatusHistoryRepository histories;
  private final Clock inspectionClock;

  /** 점검 행을 잠가 상태 변경과 이력을 함께 저장하고 같은 상태 요청은 중복 기록하지 않는다. */
  @Transactional
  public InspectionStatusResponse change(
      Long operatorId, Long inspectionId, InspectionStatus target) {
    if (inspectionId == null || inspectionId <= 0)
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    if (target == null || target == InspectionStatus.RECEIVED)
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY);
    var operator =
        users
            .findById(operatorId)
            .orElseThrow(() -> new BaseException(GeneralErrorCode.UNAUTHORIZED_ERROR));
    if (operator.getRole() != UserRole.ADMIN)
      throw new BaseException(GeneralErrorCode.ACCESS_DENIED_REQUEST);
    var inspection =
        inspections
            .findForStatusUpdate(inspectionId)
            .orElseThrow(() -> new BaseException(InspectionErrorCode.INSPECTION_NOT_FOUND));
    if (operator.getJurisdiction() == null
        || !"부천시".equals(operator.getJurisdiction().getName())
        || !BUCHEON_ADDRESS.matcher(inspection.getApplication().getAddress().strip()).find()) {
      throw new BaseException(InspectionErrorCode.JURISDICTION_ACCESS_DENIED);
    }
    InspectionStatus previous = inspection.getStatus();
    if (previous == target) return InspectionStatusResponse.from(inspection);
    if (!previous.canTransitionTo(target))
      throw new BaseException(InspectionErrorCode.INVALID_STATUS_TRANSITION);
    LocalDateTime changedAt = LocalDateTime.now(inspectionClock).truncatedTo(ChronoUnit.SECONDS);
    if (target == InspectionStatus.IN_PROGRESS
        && (inspection.getScheduledAt() == null
            || inspection.getScheduledAt().isAfter(changedAt))) {
      throw new BaseException(InspectionErrorCode.VISIT_TIME_NOT_REACHED);
    }
    inspection.changeStatus(target, changedAt);
    histories.saveAndFlush(
        InspectionStatusHistory.changed(inspection, previous, operator, changedAt));
    return InspectionStatusResponse.from(inspection);
  }

  /** 각 점검을 별도 트랜잭션으로 재확인하여 자동·수동 요청 경합에서도 이력을 한 번만 저장한다. */
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
