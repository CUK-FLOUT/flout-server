package com.flout.server.domain.inspectionapplication.service;

import com.flout.server.domain.inspection.entity.ResultStatus;
import com.flout.server.domain.inspection.repository.InspectionRepository;
import com.flout.server.domain.inspection.repository.InspectionResultRepository;
import com.flout.server.domain.inspectionapplication.dto.response.ResidentApplicationDetailResponse;
import com.flout.server.domain.inspectionapplication.repository.*;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationErrorCode;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ResidentApplicationDetailService {
  private final InspectionApplicationRepository applications;
  private final ApplicationInflowLocationRepository locations;
  private final ApplicationAttachmentRepository attachments;
  private final InspectionRepository inspections;
  private final InspectionResultRepository results;

  @Transactional(readOnly = true)
  public ResidentApplicationDetailResponse get(Long userId, Long applicationId) {
    if (applicationId == null || applicationId <= 0) {
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    }
    var application =
        applications
            .findForResidentDetail(applicationId)
            .orElseThrow(
                () -> new BaseException(InspectionApplicationErrorCode.APPLICATION_NOT_FOUND));
    if (application.getApplicant().getRole() != UserRole.RESIDENT
        || !application.getApplicant().getId().equals(userId)) {
      throw new BaseException(InspectionApplicationErrorCode.APPLICATION_DETAIL_ACCESS_DENIED);
    }
    var inspection = inspections.findByApplicationId(applicationId);
    boolean published =
        inspection.isPresent()
            && results
                .findByInspectionIdAndResultStatus(inspection.get().getId(), ResultStatus.PUBLISHED)
                .isPresent();
    return ResidentApplicationDetailResponse.from(
        application,
        locations.findLocationsByApplicationId(applicationId),
        attachments.countByApplicationId(applicationId),
        inspection.map(i -> i.getStatus()).orElse(null),
        published);
  }
}
