package com.flout.server.domain.inspection.service;

import com.flout.server.domain.inspection.dto.request.SaveInspectionAssignmentRequest;
import com.flout.server.domain.inspection.dto.response.InspectionAssignmentResponse;
import com.flout.server.domain.inspection.entity.InspectionStatus;
import com.flout.server.domain.inspection.repository.InspectionRepository;
import com.flout.server.domain.inspection.response.code.InspectionErrorCode;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.domain.user.repository.UserRepository;
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
public class InspectionAssignmentService {
  private static final Pattern BUCHEON_ADDRESS =
      Pattern.compile("^(?:(?:경기도|경기)\\s+)?부천시(?:\\s|$)");
  private final UserRepository users;
  private final InspectionRepository inspections;
  private final Clock inspectionClock;

  // 예정 점검에 본인 배정
  @Transactional
  public InspectionAssignmentResponse save(
      Long operatorId, Long inspectionId, SaveInspectionAssignmentRequest request) {
    if (inspectionId == null || inspectionId <= 0)
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    var operator =
        users
            .findById(operatorId)
            .orElseThrow(() -> new BaseException(GeneralErrorCode.UNAUTHORIZED_ERROR));
    if (operator.getRole() != UserRole.ADMIN)
      throw new BaseException(GeneralErrorCode.ACCESS_DENIED_REQUEST);
    // 자동 전환과 배정 저장 간 충돌 방지
    var inspection =
        inspections
            .findForStatusUpdate(inspectionId)
            .orElseThrow(() -> new BaseException(InspectionErrorCode.INSPECTION_NOT_FOUND));
    if (operator.getJurisdiction() == null
        || !"부천시".equals(operator.getJurisdiction().getName())
        || !BUCHEON_ADDRESS.matcher(inspection.getApplication().getAddress().strip()).find())
      throw new BaseException(InspectionErrorCode.JURISDICTION_ACCESS_DENIED);
    if (inspection.getInspector() != null)
      throw new BaseException(InspectionErrorCode.ALREADY_ASSIGNED);
    if (inspection.getStatus() != InspectionStatus.SCHEDULED)
      throw new BaseException(InspectionErrorCode.ASSIGNMENT_REQUIRES_SCHEDULED);
    LocalDateTime now = LocalDateTime.now(inspectionClock).truncatedTo(ChronoUnit.SECONDS);
    LocalDateTime scheduledAt = LocalDateTime.of(request.visitDate(), request.visitTime());
    if (scheduledAt.isBefore(now)) throw new BaseException(InspectionErrorCode.PAST_VISIT_TIME);
    String notice = request.preVisitNotice() == null ? null : request.preVisitNotice().strip();
    if (notice != null && notice.isEmpty()) notice = null;
    inspection.assign(operator, scheduledAt, notice, now);
    inspections.flush();
    return InspectionAssignmentResponse.from(inspection);
  }
}
