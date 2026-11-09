package com.flout.server.domain.publicissue.dto.response;

import com.flout.server.domain.riskassessment.entity.RiskLevel;
import java.util.List;

public record RelatedApplicationsResponse(
    Long publicIssueId, int totalCount, List<ApplicationItem> applications) {
  public record ApplicationItem(
      Long applicationId,
      String receiptNo,
      String address,
      RiskLevel vulnerabilityGrade,
      String fieldFindings) {}
}
