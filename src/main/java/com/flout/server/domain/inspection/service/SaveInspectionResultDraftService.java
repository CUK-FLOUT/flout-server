package com.flout.server.domain.inspection.service;

import com.flout.server.domain.inspection.dto.request.SaveInspectionResultDraftRequest;
import com.flout.server.domain.inspection.dto.response.SaveInspectionResultDraftResponse;
import com.flout.server.domain.inspection.entity.*;
import com.flout.server.domain.inspection.repository.*;
import com.flout.server.global.time.SeoulDateTimes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SaveInspectionResultDraftService {
  private final InspectionAccess access;
  private final InspectionResultRepository results;
  private final InspectionVulnerabilityRepository vulnerabilities;
  private final PreventionRecommendationRepository recommendations;

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public SaveInspectionResultDraftResponse save(
      Long operatorId, Long inspectionId, SaveInspectionResultDraftRequest request) {
    InspectionAccess.validateInspectionId(inspectionId);
    var operator = access.requireAdmin(operatorId);
    var inspection = access.lock(inspectionId);
    access.requireWritable(operator, inspection);

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
        recommendations.save(PreventionRecommendation.create(result, item.description(), i));
      }
    }
    results.flush();
    return new SaveInspectionResultDraftResponse(
        inspection.getId(),
        result.getResultStatus().name(),
        SeoulDateTimes.toOffset(result.getUpdatedAt()));
  }
}
