package com.flout.server.domain.inspectionapplication.service;

import com.flout.server.domain.inspection.repository.InspectionRepository;
import com.flout.server.domain.inspectionapplication.dto.response.ResidentApplicationDetailResponse;
import com.flout.server.domain.inspectionapplication.dto.response.ResidentApplicationSummaryResponse;
import com.flout.server.domain.inspectionapplication.repository.*;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationErrorCode;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.util.List;
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
  private final ResidentApplicationProgressReader progressReader;
  private final UserRepository users;

  @Transactional(readOnly = true)
  public List<ResidentApplicationSummaryResponse> list(Long userId) {
    var user =
        users
            .findById(userId)
            .orElseThrow(() -> new BaseException(GeneralErrorCode.UNAUTHORIZED_ERROR));
    if (user.getRole() != UserRole.RESIDENT)
      throw new BaseException(GeneralErrorCode.ACCESS_DENIED_REQUEST);
    var rows = applications.findResidentApplications(userId);
    var progress = progressReader.read(rows);
    return rows.stream()
        .map(row -> ResidentApplicationSummaryResponse.from(row, progress.get(row.getId())))
        .toList();
  }

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
    var progress = progressReader.read(List.of(application)).get(applicationId);
    return ResidentApplicationDetailResponse.from(
        application,
        locations.findLocationsByApplicationId(applicationId),
        attachments.countByApplicationId(applicationId),
        inspection.map(i -> i.getStatus()).orElse(null),
        progress.resultPublished(),
        progress);
  }
}
