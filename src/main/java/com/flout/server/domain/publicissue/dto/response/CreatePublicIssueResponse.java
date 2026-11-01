package com.flout.server.domain.publicissue.dto.response;

import com.flout.server.domain.publicissue.entity.*;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record CreatePublicIssueResponse(
    Long publicIssueId,
    Long sourceInspectionId,
    String address,
    PublicIssueType issueType,
    String description,
    String fieldFindings,
    PublicIssueStatus status,
    OffsetDateTime createdAt) {
  public static CreatePublicIssueResponse from(PublicIssue issue) {
    return new CreatePublicIssueResponse(
        issue.getId(),
        issue.getSourceInspection().getId(),
        issue.getAddress(),
        issue.getIssueType(),
        issue.getDescription(),
        issue.getFieldFindings(),
        issue.getStatus(),
        issue
            .getCreatedAt()
            .atZone(ZoneId.systemDefault())
            .withZoneSameInstant(ZoneId.of("Asia/Seoul"))
            .toOffsetDateTime());
  }
}
