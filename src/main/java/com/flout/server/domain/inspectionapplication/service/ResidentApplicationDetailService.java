package com.flout.server.domain.inspectionapplication.service;

import com.flout.server.domain.inspectionapplication.dto.response.ResidentApplicationDetailResponse;
import com.flout.server.domain.inspectionapplication.dto.response.ResidentApplicationSummaryResponse;
import com.flout.server.domain.inspectionapplication.repository.*;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationErrorCode;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.domain.user.service.ResidentAccountAccess;
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
  private final ResidentApplicationProgressReader progressReader;
  private final ResidentAccountAccess access;

  @Transactional(readOnly = true)
  public List<ResidentApplicationSummaryResponse> list(Long userId) {
    access.requireResident(userId);
    // 내 신청 목록에서는 임시 저장 건을 제외한다.
    var rows = applications.findResidentApplications(userId);
    var progress = progressReader.read(rows);
    return rows.stream()
        .map(
            row ->
                ResidentApplicationSummaryResponse.from(row, progress.get(row.getId()).progress()))
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
    var snapshot = progressReader.read(List.of(application)).get(applicationId);
    return ResidentApplicationDetailResponse.from(
        application,
        locations.findLocationsByApplicationId(applicationId),
        attachments.countByApplicationId(applicationId),
        snapshot.inspectionStatus(),
        snapshot.progress());
  }
}
