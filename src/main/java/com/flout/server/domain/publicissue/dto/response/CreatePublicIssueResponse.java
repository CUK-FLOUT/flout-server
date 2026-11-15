package com.flout.server.domain.publicissue.dto.response;

import com.flout.server.domain.publicissue.entity.*;
import com.flout.server.global.time.SeoulDateTimes;
import java.time.OffsetDateTime;

public record CreatePublicIssueResponse(
    Long publicIssueId,
    Long inspectionId,
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
        SeoulDateTimes.toOffset(issue.getCreatedAt()));
  }
}
