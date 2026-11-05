package com.flout.server.domain.publicissue.service;

import com.flout.server.domain.publicissue.dto.request.CompletePublicIssueActionRequest;
import com.flout.server.domain.publicissue.dto.response.CompletePublicIssueActionResponse;
import com.flout.server.domain.publicissue.entity.PublicIssueAction;
import com.flout.server.domain.publicissue.entity.PublicIssueStatus;
import com.flout.server.domain.publicissue.repository.FacilityReviewRepository;
import com.flout.server.domain.publicissue.repository.PublicIssueActionRepository;
import com.flout.server.domain.publicissue.response.code.PublicIssueErrorCode;
import com.flout.server.global.exception.BaseException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PublicIssueActionService {
  private final PublicIssueAccess access;
  private final FacilityReviewRepository reviews;
  private final PublicIssueActionRepository actions;

  @Transactional
  public CompletePublicIssueActionResponse complete(
      Long operatorId, Long publicIssueId, CompletePublicIssueActionRequest request) {
    var issue = access.lock(operatorId, publicIssueId).issue();
    if (issue.getStatus() == PublicIssueStatus.COMPLETED)
      throw new BaseException(PublicIssueErrorCode.ACTION_NOT_ALLOWED);
    var review =
        reviews
            .findByLocalProblemId(publicIssueId)
            .orElseThrow(() -> new BaseException(PublicIssueErrorCode.REVIEW_REQUIRED));
    var completedAt = LocalDateTime.now();
    issue.completeAction();
    review.completeAction();
    return CompletePublicIssueActionResponse.from(
        actions.saveAndFlush(
            PublicIssueAction.complete(review, request.actionDescription(), completedAt)));
  }
}
