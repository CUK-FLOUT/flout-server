package com.flout.server.domain.inspectionapplication.service;

import com.flout.server.domain.inspectionapplication.entity.InspectionApplication;
import com.flout.server.domain.inspectionapplication.repository.InspectionApplicationRepository;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationErrorCode;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class AdminApplicationAccess {
  private static final String BUCHEON = "부천시";
  private static final Pattern BUCHEON_ADDRESS =
      Pattern.compile("^(?:(?:경기도|경기)\\s+)?" + BUCHEON + "(?:\\s|$)");

  private final UserRepository users;
  private final InspectionApplicationRepository applications;

  void requireReadOperator(Long operatorId) {
    if (!hasBucheonJurisdiction(requireAdmin(operatorId))) {
      throw new BaseException(InspectionApplicationErrorCode.JURISDICTION_READ_ACCESS_DENIED);
    }
  }

  InspectionApplication read(Long operatorId, Long applicationId) {
    validateApplicationId(applicationId);
    requireReadOperator(operatorId);
    var application = findSubmittedApplication(applicationId);
    requireReadableAddress(application.getAddress());
    return application;
  }

  void requireWritableApplication(Long operatorId, Long applicationId) {
    validateApplicationId(applicationId);
    var operator = requireAdmin(operatorId);
    var application = findSubmittedApplication(applicationId);
    if (!hasBucheonJurisdiction(operator) || !isBucheonAddress(application.getAddress())) {
      throw new BaseException(InspectionApplicationErrorCode.JURISDICTION_ACCESS_DENIED);
    }
  }

  void requireReadableAddress(String address) {
    if (!isBucheonAddress(address)) {
      throw new BaseException(InspectionApplicationErrorCode.JURISDICTION_READ_ACCESS_DENIED);
    }
  }

  private User requireAdmin(Long operatorId) {
    var operator =
        users
            .findById(operatorId)
            .orElseThrow(() -> new BaseException(GeneralErrorCode.UNAUTHORIZED_ERROR));
    if (operator.getRole() != UserRole.ADMIN) {
      throw new BaseException(GeneralErrorCode.ACCESS_DENIED_REQUEST);
    }
    return operator;
  }

  private InspectionApplication findSubmittedApplication(Long applicationId) {
    return applications
        .findForAdminDetail(applicationId)
        .orElseThrow(() -> new BaseException(InspectionApplicationErrorCode.APPLICATION_NOT_FOUND));
  }

  private static boolean hasBucheonJurisdiction(User operator) {
    var jurisdiction = operator.getJurisdiction();
    return jurisdiction != null && BUCHEON.equals(jurisdiction.getName());
  }

  private static boolean isBucheonAddress(String address) {
    return BUCHEON_ADDRESS.matcher(address.strip()).find();
  }

  private static void validateApplicationId(Long applicationId) {
    if (applicationId == null || applicationId <= 0) {
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    }
  }
}
