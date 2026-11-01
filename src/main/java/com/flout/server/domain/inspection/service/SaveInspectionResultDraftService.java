package com.flout.server.domain.inspection.service;

import com.flout.server.domain.inspection.dto.request.SaveInspectionResultDraftRequest;
import com.flout.server.domain.inspection.dto.response.SaveInspectionResultDraftResponse;
import com.flout.server.domain.inspection.entity.*;
import com.flout.server.domain.inspection.repository.*;
import com.flout.server.domain.inspection.response.code.InspectionErrorCode;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.time.ZoneId;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SaveInspectionResultDraftService {
  private static final Pattern BUCHEON_ADDRESS =
      Pattern.compile("^(?:(?:경기도|경기)\\s+)?부천시(?:\\s|$)");

  private final UserRepository users;
  private final InspectionRepository inspections;
  private final InspectionResultRepository results;
  private final InspectionVulnerabilityRepository vulnerabilities;
  private final PreventionRecommendationRepository recommendations;

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public SaveInspectionResultDraftResponse save(
      Long operatorId, Long inspectionId, SaveInspectionResultDraftRequest request) {
    if (inspectionId == null || inspectionId <= 0) {
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    }
    var operator =
        users
            .findById(operatorId)
            .orElseThrow(() -> new BaseException(GeneralErrorCode.UNAUTHORIZED_ERROR));
    if (operator.getRole() != UserRole.ADMIN) {
      throw new BaseException(GeneralErrorCode.ACCESS_DENIED_REQUEST);
    }
    var inspection =
        inspections
            .findForStatusUpdate(inspectionId)
            .orElseThrow(() -> new BaseException(InspectionErrorCode.INSPECTION_NOT_FOUND));
    if (operator.getJurisdiction() == null
        || !"부천시".equals(operator.getJurisdiction().getName())
        || !BUCHEON_ADDRESS.matcher(inspection.getApplication().getAddress().strip()).find()) {
      throw new BaseException(InspectionErrorCode.JURISDICTION_ACCESS_DENIED);
    }

    var result =
        results
            .findByInspectionIdAndResultStatus(inspectionId, ResultStatus.DRAFT)
            .orElseGet(() -> InspectionResult.draft(inspection));
    result.updateDraftContent(
        request.summary(),
        request.followUpType() == null ? null : request.followUpType().name(),
        request.internalMemo());
    result = results.saveAndFlush(result);
    vulnerabilities.deleteByResultId(result.getId());
    recommendations.deleteByResultId(result.getId());

    if (request.vulnerabilities() != null) {
      for (int i = 0; i < request.vulnerabilities().size(); i++) {
        var item = request.vulnerabilities().get(i);
        vulnerabilities.save(
            InspectionVulnerability.create(
                result,
                item.type() == null ? null : item.type().name(),
                item.title(),
                item.expertOpinion(),
                item.fieldFindings(),
                i));
      }
    }
    if (request.recommendedActions() != null) {
      for (int i = 0; i < request.recommendedActions().size(); i++) {
        var item = request.recommendedActions().get(i);
        recommendations.save(
            PreventionRecommendation.create(
                result,
                item.title(),
                item.relatedTarget() == null ? null : item.relatedTarget().name(),
                item.description(),
                i));
      }
    }
    results.flush();
    return new SaveInspectionResultDraftResponse(
        inspection.getId(),
        result.getResultStatus().name(),
        result
            .getUpdatedAt()
            .atZone(ZoneId.systemDefault())
            .withZoneSameInstant(ZoneId.of("Asia/Seoul"))
            .toOffsetDateTime());
  }
}
