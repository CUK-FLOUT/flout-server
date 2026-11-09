package com.flout.server.domain.publicissue.service;

import com.flout.server.domain.inspectionapplication.repository.InspectionApplicationRepository;
import com.flout.server.domain.publicissue.dto.response.RelatedApplicationsResponse;
import com.flout.server.domain.publicissue.dto.response.RelatedApplicationsResponse.ApplicationItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PublicIssueRelatedApplicationService {

  private final PublicIssueAccess access;
  private final InspectionApplicationRepository applications;

  @Transactional(readOnly = true)
  public RelatedApplicationsResponse getRelated(Long operatorId, Long publicIssueId) {
    var issue = access.read(operatorId, publicIssueId);

    var items =
        applications.findRelatedByAddress(issue.getAddress()).stream()
            .map(
                app ->
                    new ApplicationItem(
                        app.getId(),
                        app.getReceiptNo(),
                        app.getAddress(),
                        app.getAssessment().getRiskLevel(),
                        app.getRequestReason()))
            .toList();

    return new RelatedApplicationsResponse(publicIssueId, items.size(), items);
  }
}
