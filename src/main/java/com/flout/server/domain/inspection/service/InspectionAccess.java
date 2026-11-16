package com.flout.server.domain.inspection.service;

import com.flout.server.domain.inspection.entity.Inspection;
import com.flout.server.domain.inspection.repository.InspectionRepository;
import com.flout.server.domain.inspection.response.code.InspectionErrorCode;
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
class InspectionAccess {
  private static final String BUCHEON = "부천시";
  private static final Pattern BUCHEON_ADDRESS =
      Pattern.compile("^(?:(?:경기도|경기)\\s+)?" + BUCHEON + "(?:\\s|$)");

  private final UserRepository users;
  private final InspectionRepository inspections;

  User requireAdmin(Long operatorId) {
    var operator =
        users
            .findById(operatorId)
            .orElseThrow(() -> new BaseException(GeneralErrorCode.UNAUTHORIZED_ERROR));
    if (operator.getRole() != UserRole.ADMIN) {
      throw new BaseException(GeneralErrorCode.ACCESS_DENIED_REQUEST);
    }
    return operator;
  }

  void requireReadOperator(Long operatorId) {
    requireJurisdiction(
        requireAdmin(operatorId), InspectionErrorCode.JURISDICTION_READ_ACCESS_DENIED);
  }

  void requireWriteOperator(Long operatorId) {
    requireJurisdiction(requireAdmin(operatorId), InspectionErrorCode.JURISDICTION_ACCESS_DENIED);
  }

  Inspection read(Long operatorId, Long inspectionId) {
    validateInspectionId(inspectionId);
    requireReadOperator(operatorId);
    var inspection = find(inspectionId);
    requireAddress(inspection, InspectionErrorCode.JURISDICTION_READ_ACCESS_DENIED);
    return inspection;
  }

  Inspection find(Long inspectionId) {
    return inspections
        .findById(inspectionId)
        .orElseThrow(() -> new BaseException(InspectionErrorCode.INSPECTION_NOT_FOUND));
  }

  Inspection lock(Long inspectionId) {
    return inspections
        .findForStatusUpdate(inspectionId)
        .orElseThrow(() -> new BaseException(InspectionErrorCode.INSPECTION_NOT_FOUND));
  }

  void requireWritable(User operator, Inspection inspection) {
    requireJurisdiction(operator, InspectionErrorCode.JURISDICTION_ACCESS_DENIED);
    requireWritableAddress(inspection);
  }

  void requireWritableAddress(Inspection inspection) {
    requireAddress(inspection, InspectionErrorCode.JURISDICTION_ACCESS_DENIED);
  }

  static void validateInspectionId(Long inspectionId) {
    if (inspectionId == null || inspectionId <= 0) {
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    }
  }

  private static void requireJurisdiction(User operator, InspectionErrorCode error) {
    var jurisdiction = operator.getJurisdiction();
    if (jurisdiction == null || !BUCHEON.equals(jurisdiction.getName())) {
      throw new BaseException(error);
    }
  }

  private static void requireAddress(Inspection inspection, InspectionErrorCode error) {
    if (!BUCHEON_ADDRESS.matcher(inspection.getApplication().getAddress().strip()).find()) {
      throw new BaseException(error);
    }
  }
}
