package com.flout.server.domain.publicissue.service;

import com.flout.server.domain.inspection.response.code.InspectionErrorCode;
import com.flout.server.domain.publicissue.dto.request.CreatePublicIssueActionRequest;
import com.flout.server.domain.publicissue.dto.response.CreatePublicIssueActionResponse;
import com.flout.server.domain.publicissue.entity.PublicIssueAction;
import com.flout.server.domain.publicissue.entity.PublicIssueStatus;
import com.flout.server.domain.publicissue.repository.FacilityReviewRepository;
import com.flout.server.domain.publicissue.repository.PublicIssueActionRepository;
import com.flout.server.domain.publicissue.repository.PublicIssueRepository;
import com.flout.server.domain.publicissue.response.code.PublicIssueErrorCode;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PublicIssueActionService {
  private static final Pattern BUCHEON = Pattern.compile("^(?:(?:경기도|경기)\\s+)?부천시(?:\\s|$)");
  private final UserRepository users;
  private final PublicIssueRepository issues;
  private final FacilityReviewRepository reviews;
  private final PublicIssueActionRepository actions;

  @Transactional
  public CreatePublicIssueActionResponse register(
      Long operatorId, Long publicIssueId, CreatePublicIssueActionRequest request) {
    if (publicIssueId == null || publicIssueId <= 0)
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    var operator =
        users
            .findById(operatorId)
            .orElseThrow(() -> new BaseException(GeneralErrorCode.UNAUTHORIZED_ERROR));
    if (operator.getRole() != UserRole.ADMIN)
      throw new BaseException(GeneralErrorCode.ACCESS_DENIED_REQUEST);
    if (operator.getJurisdiction() == null || !"부천시".equals(operator.getJurisdiction().getName()))
      throw new BaseException(InspectionErrorCode.JURISDICTION_ACCESS_DENIED);
    var issue =
        issues
            .findForAction(publicIssueId)
            .orElseThrow(() -> new BaseException(PublicIssueErrorCode.PUBLIC_ISSUE_NOT_FOUND));
    if (!BUCHEON.matcher(issue.getAddress().strip()).find())
      throw new BaseException(InspectionErrorCode.JURISDICTION_ACCESS_DENIED);
    if (issue.getStatus() == PublicIssueStatus.COMPLETED)
      throw new BaseException(PublicIssueErrorCode.ACTION_NOT_ALLOWED);
    var review =
        reviews
            .findByLocalProblemId(publicIssueId)
            .orElseThrow(() -> new BaseException(PublicIssueErrorCode.REVIEW_REQUIRED));
    issue.startAction();
    review.startAction();
    return CreatePublicIssueActionResponse.from(
        actions.saveAndFlush(PublicIssueAction.create(review, request.actionDescription())));
  }
}
