package com.flout.server.domain.inspection.service;

import com.flout.server.domain.inspection.dto.request.SaveInspectionAssignmentRequest;
import com.flout.server.domain.inspection.dto.response.InspectionAssignmentResponse;
import com.flout.server.domain.inspection.entity.InspectionStatus;
import com.flout.server.domain.inspection.entity.InspectionStatusHistory;
import com.flout.server.domain.inspection.repository.InspectionRepository;
import com.flout.server.domain.inspection.repository.InspectionStatusHistoryRepository;
import com.flout.server.domain.inspection.response.code.InspectionErrorCode;
import com.flout.server.domain.notification.entity.NotificationType;
import com.flout.server.domain.notification.service.NotificationService;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InspectionAssignmentService {
  private final InspectionAccess access;
  private final InspectionRepository inspections;
  private final Clock inspectionClock;
  private final InspectionStatusHistoryRepository histories;
  private final NotificationService notifications;

  /** 미배정 접수·예정 점검에 본인을 배정하고 예정 상태 및 전환 이력을 함께 저장한다. */
  @Transactional
  public InspectionAssignmentResponse save(
      Long operatorId, Long inspectionId, SaveInspectionAssignmentRequest request) {
    InspectionAccess.validateInspectionId(inspectionId);
    if (request.preVisitNotice() == null || request.preVisitNotice().isBlank())
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY);
    var operator = access.requireAdmin(operatorId);
    // 자동 전환과 배정 저장 간 충돌 방지
    var inspection = access.lock(inspectionId);
    access.requireWritable(operator, inspection);
    if (inspection.getInspector() != null)
      throw new BaseException(InspectionErrorCode.ALREADY_ASSIGNED);
    if (inspection.getStatus() != InspectionStatus.RECEIVED
        && inspection.getStatus() != InspectionStatus.SCHEDULED)
      throw new BaseException(InspectionErrorCode.INVALID_STATUS_TRANSITION);
    LocalDateTime now = LocalDateTime.now(inspectionClock).truncatedTo(ChronoUnit.SECONDS);
    LocalDateTime scheduledAt = LocalDateTime.of(request.visitDate(), request.visitTime());
    if (scheduledAt.isBefore(now)) throw new BaseException(InspectionErrorCode.PAST_VISIT_TIME);
    String notice = request.preVisitNotice().strip();
    var previous = inspection.getStatus();
    inspection.assign(operator, scheduledAt, notice, now);
    if (previous != inspection.getStatus())
      histories.saveAndFlush(InspectionStatusHistory.changed(inspection, previous, operator, now));
    inspections.flush();
    var application = inspection.getApplication();
    notifications.send(
        application.getApplicant().getId(),
        NotificationType.INSPECTION_SCHEDULED,
        application.getId(),
        "inspection-scheduled:" + inspectionId);
    return InspectionAssignmentResponse.from(inspection);
  }
}
