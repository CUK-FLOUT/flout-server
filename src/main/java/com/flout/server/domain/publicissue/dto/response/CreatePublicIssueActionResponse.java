package com.flout.server.domain.publicissue.dto.response;

import com.flout.server.domain.publicissue.entity.PublicIssueAction;
import com.flout.server.domain.publicissue.entity.PublicIssueActionStatus;
import com.flout.server.domain.publicissue.entity.PublicIssueStatus;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record CreatePublicIssueActionResponse(
    Long actionId,
    Long reviewId,
    Long publicIssueId,
    String actionDescription,
    PublicIssueActionStatus actionStatus,
    PublicIssueStatus publicIssueStatus,
    OffsetDateTime createdAt) {
  public static CreatePublicIssueActionResponse from(PublicIssueAction action) {
    return new CreatePublicIssueActionResponse(
        action.getId(),
        action.getReview().getId(),
        action.getReview().getLocalProblem().getId(),
        action.getActionDescription(),
        action.getStatus(),
        action.getReview().getLocalProblem().getStatus(),
        action
            .getCreatedAt()
            .atZone(ZoneId.systemDefault())
            .withZoneSameInstant(ZoneId.of("Asia/Seoul"))
            .toOffsetDateTime());
  }
}
