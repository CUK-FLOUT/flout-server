package com.flout.server.domain.publicissue.dto.response;

import com.flout.server.domain.publicissue.entity.FacilityReviewStatus;
import com.flout.server.domain.publicissue.entity.PublicIssueAction;
import com.flout.server.domain.publicissue.entity.PublicIssueActionStatus;
import com.flout.server.domain.publicissue.entity.PublicIssueStatus;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record CompletePublicIssueActionResponse(
    Long actionId,
    Long reviewId,
    Long publicIssueId,
    String actionDescription,
    PublicIssueActionStatus actionStatus,
    FacilityReviewStatus reviewStatus,
    PublicIssueStatus publicIssueStatus,
    OffsetDateTime completedAt) {
  public static CompletePublicIssueActionResponse from(PublicIssueAction action) {
    return new CompletePublicIssueActionResponse(
        action.getId(),
        action.getReview().getId(),
        action.getReview().getLocalProblem().getId(),
        action.getActionDescription(),
        action.getStatus(),
        action.getReview().getReviewStatus(),
        action.getReview().getLocalProblem().getStatus(),
        action
            .getCompletedAt()
            .atZone(ZoneId.systemDefault())
            .withZoneSameInstant(ZoneId.of("Asia/Seoul"))
            .toOffsetDateTime());
  }
}
