package com.flout.server.domain.publicissue.service;

import com.flout.server.domain.publicissue.dto.request.ChangePublicIssueActionStatusRequest;
import com.flout.server.domain.publicissue.dto.request.CreatePublicIssueActionRequest;
import com.flout.server.domain.publicissue.dto.response.PublicIssueActionResponse;
import com.flout.server.domain.publicissue.entity.PublicIssueAction;
import com.flout.server.domain.publicissue.entity.PublicIssueActionStatus;
import com.flout.server.domain.publicissue.entity.PublicIssueStatus;
import com.flout.server.domain.publicissue.repository.FacilityActionImageRepository;
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
  private final FacilityActionImageRepository images;

  @Transactional
  public PublicIssueActionResponse register(
      Long operatorId, Long publicIssueId, CreatePublicIssueActionRequest request) {
    var authorized = access.lock(operatorId, publicIssueId);
    var issue = authorized.issue();
    if (issue.getStatus() == PublicIssueStatus.COMPLETED)
      throw new BaseException(PublicIssueErrorCode.ACTION_NOT_ALLOWED);
    var review =
        reviews
            .findByLocalProblemId(publicIssueId)
            .orElseThrow(() -> new BaseException(PublicIssueErrorCode.REVIEW_REQUIRED));
    issue.startAction();
    review.startAction();
    return PublicIssueActionResponse.from(
        actions.saveAndFlush(
            PublicIssueAction.start(review, authorized.operator(), request.actionDescription())));
  }

  @Transactional
  public PublicIssueActionResponse changeStatus(
      Long operatorId,
      Long publicIssueId,
      Long actionId,
      ChangePublicIssueActionStatusRequest request) {
    var issue = access.lock(operatorId, publicIssueId).issue();
    var action =
        actions
            .findForStatusChange(actionId)
            .orElseThrow(() -> new BaseException(PublicIssueErrorCode.ACTION_NOT_FOUND));
    if (!action.getReview().getLocalProblem().getId().equals(issue.getId()))
      throw new BaseException(PublicIssueErrorCode.ACTION_NOT_FOUND);
    if (request.status() != PublicIssueActionStatus.COMPLETED
        || action.getStatus() != PublicIssueActionStatus.IN_PROGRESS)
      throw new BaseException(PublicIssueErrorCode.ACTION_STATUS_TRANSITION_NOT_ALLOWED);
    if (action.getActionDescription().isBlank() || !images.existsByActionId(actionId))
      throw new BaseException(PublicIssueErrorCode.ACTION_COMPLETION_REQUIREMENTS_NOT_MET);
    action.complete(LocalDateTime.now());
    issue.completeAction();
    action.getReview().completeAction();
    actions.flush();
    return PublicIssueActionResponse.from(action);
  }
}
