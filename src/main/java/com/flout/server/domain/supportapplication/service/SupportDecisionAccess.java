package com.flout.server.domain.supportapplication.service;

import com.flout.server.domain.supportapplication.entity.*;
import com.flout.server.domain.supportapplication.repository.SupportApplicationRepository;
import com.flout.server.domain.supportapplication.response.code.SupportApprovalErrorCode;
import com.flout.server.domain.user.entity.*;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@RequiredArgsConstructor
public class SupportDecisionAccess {
  static final Pattern BUCHEON_ADDRESS = Pattern.compile("^(?:(?:경기도|경기)\\s+)?부천시(?:\\s|$)");
  private final UserRepository users;
  private final SupportApplicationRepository applications;

  @Transactional(propagation = Propagation.MANDATORY)
  public Decision lockSubmitted(Long operatorId, Long id) {
    if (id == null || id <= 0)
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    var operator = requireOperator(operatorId, true);
    var application =
        applications
            .findForApproval(id)
            .orElseThrow(() -> new BaseException(SupportApprovalErrorCode.APPLICATION_NOT_FOUND));
    if (!BUCHEON_ADDRESS.matcher(application.getAddress().strip()).find())
      throw new BaseException(SupportApprovalErrorCode.JURISDICTION_ACCESS_DENIED);
    if (!application.getStatus().isReviewable())
      throw new BaseException(SupportApprovalErrorCode.ALREADY_DECIDED);
    return new Decision(operator, application);
  }

  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public User requireOperator(Long operatorId) {
    return requireOperator(operatorId, false);
  }

  private User requireOperator(Long operatorId, boolean decision) {
    var operator =
        users
            .findById(operatorId)
            .orElseThrow(() -> new BaseException(GeneralErrorCode.UNAUTHORIZED_ERROR));
    if (operator.getRole() != UserRole.ADMIN)
      throw new BaseException(GeneralErrorCode.ACCESS_DENIED_REQUEST);
    if (operator.getJurisdiction() == null || !"부천시".equals(operator.getJurisdiction().getName()))
      throw new BaseException(
          decision
              ? SupportApprovalErrorCode.JURISDICTION_ACCESS_DENIED
              : GeneralErrorCode.ACCESS_DENIED_REQUEST);
    return operator;
  }

  public record Decision(User operator, SupportApplication application) {}
}
