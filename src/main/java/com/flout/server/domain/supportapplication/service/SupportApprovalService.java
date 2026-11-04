package com.flout.server.domain.supportapplication.service;

import com.flout.server.domain.construction.entity.Construction;
import com.flout.server.domain.construction.repository.ConstructionRepository;
import com.flout.server.domain.inspectionapplication.entity.OccupancyType;
import com.flout.server.domain.supportapplication.dto.request.ApproveSupportApplicationRequest;
import com.flout.server.domain.supportapplication.dto.response.ApproveSupportApplicationResponse;
import com.flout.server.domain.supportapplication.entity.*;
import com.flout.server.domain.supportapplication.repository.*;
import com.flout.server.domain.supportapplication.response.code.SupportApprovalErrorCode;
import com.flout.server.global.exception.BaseException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupportApprovalService {
  private final SupportDecisionAccess access;
  private final SupportApplicationStatusHistoryRepository histories;
  private final SupportAttachmentRepository attachments;
  private final ConstructionRepository constructions;

  @Transactional
  public ApproveSupportApplicationResponse approve(
      Long operatorId, Long id, ApproveSupportApplicationRequest request) {
    var decision = access.lockSubmitted(operatorId, id);
    var operator = decision.operator();
    var application = decision.application();
    if (application.getOccupancyType() == null)
      throw new BaseException(SupportApprovalErrorCode.INVALID_OCCUPANCY);
    if (application.getOccupancyType() == OccupancyType.TENANT
        && !attachments.existsByApplicationIdAndAttachmentType(
            id, SupportAttachmentType.LANDLORD_CONSENT))
      throw new BaseException(SupportApprovalErrorCode.CONSENT_NOT_CONFIRMED);
    var previousStatus = application.getStatus();
    application.approve(
        request.supportScope(), operator, LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
    histories.saveAndFlush(SupportApplicationStatusHistory.approved(application, previousStatus));
    var construction =
        constructions.saveAndFlush(Construction.pending(application, application.getApprovedAt()));
    return ApproveSupportApplicationResponse.from(application, construction.getId());
  }
}
