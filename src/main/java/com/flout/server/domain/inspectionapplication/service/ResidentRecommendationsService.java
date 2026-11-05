package com.flout.server.domain.inspectionapplication.service;

import com.flout.server.domain.inspection.entity.ResultStatus;
import com.flout.server.domain.inspection.repository.InspectionRepository;
import com.flout.server.domain.inspection.repository.InspectionResultRepository;
import com.flout.server.domain.inspection.repository.PreventionRecommendationRepository;
import com.flout.server.domain.inspectionapplication.dto.response.ResidentRecommendationsResponse;
import com.flout.server.domain.inspectionapplication.repository.InspectionApplicationRepository;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationErrorCode;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ResidentRecommendationsService {
  private final InspectionApplicationRepository applications;
  private final InspectionRepository inspections;
  private final InspectionResultRepository results;
  private final PreventionRecommendationRepository recommendations;

  @Transactional(readOnly = true)
  public ResidentRecommendationsResponse get(Long userId, Long applicationId) {
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
    var result =
        inspections
            .findByApplicationId(applicationId)
            .flatMap(
                inspection ->
                    results.findByInspectionIdAndResultStatus(
                        inspection.getId(), ResultStatus.PUBLISHED))
            .orElseThrow(
                () -> new BaseException(InspectionApplicationErrorCode.RESULT_NOT_PUBLISHED));
    var actions =
        recommendations.findByResultIdOrderBySortOrderAsc(result.getId()).stream()
            .map(
                item ->
                    new ResidentRecommendationsResponse.RecommendedAction(item.getDescription()))
            .toList();
    return new ResidentRecommendationsResponse(applicationId, actions);
  }
}
