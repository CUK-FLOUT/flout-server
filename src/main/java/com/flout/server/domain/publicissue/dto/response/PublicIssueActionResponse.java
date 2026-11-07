package com.flout.server.domain.publicissue.dto.response;

import com.flout.server.domain.publicissue.entity.FacilityReviewStatus;
import com.flout.server.domain.publicissue.entity.PublicIssueAction;
import com.flout.server.domain.publicissue.entity.PublicIssueActionStatus;
import com.flout.server.domain.publicissue.entity.PublicIssueStatus;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record PublicIssueActionResponse(
    Long actionId,
    Long reviewId,
    Long publicIssueId,
    Long operatorUserId,
    String actionDescription,
    PublicIssueActionStatus actionStatus,
    FacilityReviewStatus reviewStatus,
    PublicIssueStatus publicIssueStatus,
    OffsetDateTime completedAt) {
  public static PublicIssueActionResponse from(PublicIssueAction action) {
    OffsetDateTime completedAt =
        action.getCompletedAt() == null
            ? null
            : action
                .getCompletedAt()
                .atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneId.of("Asia/Seoul"))
                .toOffsetDateTime();
    return new PublicIssueActionResponse(
        action.getId(),
        action.getReview().getId(),
        action.getReview().getLocalProblem().getId(),
        action.getOperator().getId(),
        action.getActionDescription(),
        action.getStatus(),
        action.getReview().getReviewStatus(),
        action.getReview().getLocalProblem().getStatus(),
        completedAt);
  }
}
