package com.flout.server.domain.publicissue.service;

import com.flout.server.domain.inspection.response.code.InspectionErrorCode;
import com.flout.server.domain.publicissue.entity.PublicIssue;
import com.flout.server.domain.publicissue.repository.PublicIssueRepository;
import com.flout.server.domain.publicissue.response.code.PublicIssueErrorCode;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.BaseResponseCode;
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

  /** 조치·검토 변경을 직렬화할 수 있도록 문제 행을 비관적 잠금으로 조회한다. */
  @Transactional(propagation = Propagation.MANDATORY)
  public Access lock(Long operatorId, Long publicIssueId) {

    validatePublicIssueId(publicIssueId);

    User operator = requireWriter(operatorId);

    PublicIssue issue =
        issues
            .findForAction(publicIssueId)
            .orElseThrow(() -> new BaseException(PublicIssueErrorCode.PUBLIC_ISSUE_NOT_FOUND));

    requireWritableAddress(issue.getAddress());

    return new Access(operator, issue);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public PublicIssue read(Long operatorId, Long publicIssueId) {

    validatePublicIssueId(publicIssueId);

    requireWriter(operatorId);

    PublicIssue issue =
        issues
            .findById(publicIssueId)
            .orElseThrow(() -> new BaseException(PublicIssueErrorCode.PUBLIC_ISSUE_NOT_FOUND));

    requireWritableAddress(issue.getAddress());

    return issue;
  }

  private void validatePublicIssueId(Long publicIssueId) {
    if (publicIssueId == null || publicIssueId <= 0) {
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    }
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public User requireWriter(Long operatorId) {
    return requireOperator(operatorId, InspectionErrorCode.JURISDICTION_ACCESS_DENIED);
  }

  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public User requireReader(Long operatorId) {
    return requireOperator(operatorId, InspectionErrorCode.JURISDICTION_READ_ACCESS_DENIED);
  }

  public void requireWritableAddress(String address) {
    requireAddress(address, InspectionErrorCode.JURISDICTION_ACCESS_DENIED);
  }

  public void requireReadableAddress(String address) {
    requireAddress(address, InspectionErrorCode.JURISDICTION_READ_ACCESS_DENIED);
  }

  private User requireOperator(Long operatorId, BaseResponseCode jurisdictionError) {
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
      throw new BaseException(jurisdictionError);
    }

    return operator;
  }

  private void requireAddress(String address, BaseResponseCode jurisdictionError) {
    if (address == null || !BUCHEON.matcher(address.strip()).find()) {
      throw new BaseException(jurisdictionError);
    }
  }

  public record Access(User operator, PublicIssue issue) {}
}
