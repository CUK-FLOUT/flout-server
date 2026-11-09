package com.flout.server.domain.publicissue.service;

import com.flout.server.domain.inspection.response.code.InspectionErrorCode;
import com.flout.server.domain.publicissue.entity.PublicIssue;
import com.flout.server.domain.publicissue.repository.PublicIssueRepository;
import com.flout.server.domain.publicissue.response.code.PublicIssueErrorCode;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PublicIssueAccess {

  private static final Pattern BUCHEON = Pattern.compile("^(?:(?:경기도|경기)\\s+)?부천시(?:\\s|$)");

  private final UserRepository users;
  private final PublicIssueRepository issues;

  // 조치/검토용: 비관적 락 사용
  @Transactional(propagation = Propagation.MANDATORY)
  public Access lock(Long operatorId, Long publicIssueId) {

    validatePublicIssueId(publicIssueId);

    User operator = authorize(operatorId);

    PublicIssue issue =
        issues
            .findForAction(publicIssueId)
            .orElseThrow(() -> new BaseException(PublicIssueErrorCode.PUBLIC_ISSUE_NOT_FOUND));

    validateJurisdiction(issue);

    return new Access(operator, issue);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public PublicIssue read(Long operatorId, Long publicIssueId) {

    validatePublicIssueId(publicIssueId);

    authorize(operatorId);

    PublicIssue issue =
        issues
            .findById(publicIssueId)
            .orElseThrow(() -> new BaseException(PublicIssueErrorCode.PUBLIC_ISSUE_NOT_FOUND));

    validateJurisdiction(issue);

    return issue;
  }

  private void validatePublicIssueId(Long publicIssueId) {
    if (publicIssueId == null || publicIssueId <= 0) {
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    }
  }

  private User authorize(Long operatorId) {
    if (operatorId == null || operatorId <= 0) {
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    }

    User operator =
        users
            .findById(operatorId)
            .orElseThrow(() -> new BaseException(GeneralErrorCode.UNAUTHORIZED_ERROR));

    if (operator.getRole() != UserRole.ADMIN) {
      throw new BaseException(GeneralErrorCode.ACCESS_DENIED_REQUEST);
    }

    if (operator.getJurisdiction() == null || !"부천시".equals(operator.getJurisdiction().getName())) {
      throw new BaseException(InspectionErrorCode.JURISDICTION_ACCESS_DENIED);
    }

    return operator;
  }

  private void validateJurisdiction(PublicIssue issue) {
    if (!BUCHEON.matcher(issue.getAddress().strip()).find()) {
      throw new BaseException(InspectionErrorCode.JURISDICTION_ACCESS_DENIED);
    }
  }

  public record Access(User operator, PublicIssue issue) {}
}
