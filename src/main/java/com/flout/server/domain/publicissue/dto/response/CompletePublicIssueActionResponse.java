package com.flout.server.domain.publicissue.dto.response;

import com.flout.server.domain.publicissue.entity.FacilityReviewStatus;
import com.flout.server.domain.publicissue.entity.PublicIssueAction;
import com.flout.server.domain.publicissue.entity.PublicIssueActionStatus;
import com.flout.server.domain.publicissue.entity.PublicIssueStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

public record CompletePublicIssueActionResponse(
    Long actionId,
    Long reviewId,
    Long publicIssueId,
    Long operatorUserId,
    String actionDescription,
    PublicIssueActionStatus actionStatus,
    FacilityReviewStatus reviewStatus,
    PublicIssueStatus publicIssueStatus,
    OffsetDateTime completedAt,
    @Schema(description = "이번 완료 요청에서 저장한 조치 증빙 사진") List<Image> images) {

  public static CompletePublicIssueActionResponse from(
      PublicIssueAction action, List<Image> images) {

    OffsetDateTime completedAt =
        action.getCompletedAt() == null
            ? null
            : action
                .getCompletedAt()
                .atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneId.of("Asia/Seoul"))
                .toOffsetDateTime();

    return new CompletePublicIssueActionResponse(
        action.getId(),
        action.getReview().getId(),
        action.getReview().getLocalProblem().getId(),
        action.getOperator().getId(),
        action.getActionDescription(),
        action.getStatus(),
        action.getReview().getReviewStatus(),
        action.getReview().getLocalProblem().getStatus(),
        completedAt,
        images);
  }

  public record Image(
      Long imageId,
      @Schema(description = "15분 유효한 비공개 사진 조회 URL") String imageUrl,
      OffsetDateTime uploadedAt) {}
}
