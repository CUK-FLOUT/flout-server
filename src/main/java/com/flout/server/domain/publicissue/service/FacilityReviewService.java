package com.flout.server.domain.publicissue.service;

import com.flout.server.domain.publicissue.dto.request.SaveFacilityReviewRequest;
import com.flout.server.domain.publicissue.dto.response.SaveFacilityReviewResponse;
import com.flout.server.domain.publicissue.entity.FacilityReview;
import com.flout.server.domain.publicissue.entity.FacilityReviewStatus;
import com.flout.server.domain.publicissue.entity.PublicIssueStatus;
import com.flout.server.domain.publicissue.repository.FacilityReviewRepository;
import com.flout.server.domain.publicissue.response.code.PublicIssueErrorCode;
import com.flout.server.global.exception.BaseException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FacilityReviewService {
  private static final EnumSet<FacilityReviewStatus> SAVABLE_STATUSES =
      EnumSet.of(
          FacilityReviewStatus.PENDING,
          FacilityReviewStatus.UNDER_REVIEW,
          FacilityReviewStatus.INSPECTION_REQUIRED);
  private final PublicIssueAccess access;
  private final FacilityReviewRepository reviews;

  @Transactional
  public SaveFacilityReviewResponse save(
      Long operatorId, Long publicIssueId, SaveFacilityReviewRequest request) {
    var authorized = access.lock(operatorId, publicIssueId);
    var issue = authorized.issue();
    if (issue.getStatus() == PublicIssueStatus.COMPLETED)
      throw new BaseException(PublicIssueErrorCode.REVIEW_NOT_ALLOWED);
    if (!SAVABLE_STATUSES.contains(request.reviewStatus()))
      throw new BaseException(PublicIssueErrorCode.INVALID_REVIEW_STATUS);
    var reviewedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    var review =
        reviews
            .findByLocalProblemId(publicIssueId)
            .map(
                current -> {
                  current.update(
                      authorized.operator(),
                      request.reviewTarget(),
                      request.inspectionNotes(),
                      request.reviewStatus(),
                      request.reviewReason(),
                      reviewedAt);
                  return current;
                })
            .orElseGet(
                () ->
                    FacilityReview.create(
                        authorized.operator(),
                        issue,
                        request.reviewTarget(),
                        request.inspectionNotes(),
                        request.reviewStatus(),
                        request.reviewReason(),
                        reviewedAt));
    issue.saveReview(request.reviewStatus());
    return SaveFacilityReviewResponse.from(reviews.saveAndFlush(review));
  }
}
