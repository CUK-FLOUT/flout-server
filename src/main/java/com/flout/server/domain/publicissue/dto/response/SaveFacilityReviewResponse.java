package com.flout.server.domain.publicissue.dto.response;

import com.flout.server.domain.publicissue.entity.FacilityReview;
import com.flout.server.domain.publicissue.entity.FacilityReviewStatus;
import com.flout.server.domain.publicissue.entity.PublicIssueStatus;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record SaveFacilityReviewResponse(
    Long reviewId,
    Long publicIssueId,
    String reviewTarget,
    FacilityReviewStatus reviewStatus,
    PublicIssueStatus publicIssueStatus,
    String inspectionNotes,
    String reviewReason,
    Long reviewerUserId,
    OffsetDateTime reviewedAt) {
  public static SaveFacilityReviewResponse from(FacilityReview review) {
    return new SaveFacilityReviewResponse(
        review.getId(),
        review.getLocalProblem().getId(),
        review.getReviewTarget(),
        review.getReviewStatus(),
        review.getLocalProblem().getStatus(),
        review.getInspectionNote(),
        review.getReviewReason(),
        review.getReviewer().getId(),
        review
            .getReviewedAt()
            .atZone(ZoneId.systemDefault())
            .withZoneSameInstant(ZoneId.of("Asia/Seoul"))
            .toOffsetDateTime());
  }
}
