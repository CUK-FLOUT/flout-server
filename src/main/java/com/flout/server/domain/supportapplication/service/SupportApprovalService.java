package com.flout.server.domain.supportapplication.service;

import com.flout.server.domain.inspectionapplication.entity.OccupancyType;
import com.flout.server.domain.supportapplication.dto.request.ApproveSupportApplicationRequest;
import com.flout.server.domain.supportapplication.dto.response.ApproveSupportApplicationResponse;
import com.flout.server.domain.supportapplication.entity.*;
import com.flout.server.domain.supportapplication.repository.*;
import com.flout.server.domain.supportapplication.response.code.SupportApprovalErrorCode;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupportApprovalService {
  private static final Pattern BUCHEON_ADDRESS =
      Pattern.compile("^(?:(?:경기도|경기)\\s+)?부천시(?:\\s|$)");
  private final UserRepository users;
  private final SupportApplicationRepository applications;
  private final SupportApplicationStatusHistoryRepository histories;

  @Transactional
  public ApproveSupportApplicationResponse approve(
      Long operatorId, Long id, ApproveSupportApplicationRequest request) {
    if (id == null || id <= 0)
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    var operator =
        users
            .findById(operatorId)
            .orElseThrow(() -> new BaseException(GeneralErrorCode.UNAUTHORIZED_ERROR));
    if (operator.getRole() != UserRole.ADMIN)
      throw new BaseException(GeneralErrorCode.ACCESS_DENIED_REQUEST);
    if (operator.getJurisdiction() == null || !"부천시".equals(operator.getJurisdiction().getName()))
      throw new BaseException(SupportApprovalErrorCode.JURISDICTION_ACCESS_DENIED);
    var application =
        applications
            .findForApproval(id)
            .orElseThrow(() -> new BaseException(SupportApprovalErrorCode.APPLICATION_NOT_FOUND));
    if (!BUCHEON_ADDRESS.matcher(application.getAddress().strip()).find())
      throw new BaseException(SupportApprovalErrorCode.JURISDICTION_ACCESS_DENIED);
    if (application.getStatus() != SupportApplicationStatus.SUBMITTED)
      throw new BaseException(SupportApprovalErrorCode.ALREADY_DECIDED);
    if (application.getOccupancyType() == OccupancyType.ETC
        || application.getOccupancyType() == null)
      throw new BaseException(SupportApprovalErrorCode.INVALID_OCCUPANCY);
    if (application.getOccupancyType() == OccupancyType.TENANT
        && application.getLandlordConsentStatus() != LandlordConsentStatus.CONFIRMED)
      throw new BaseException(SupportApprovalErrorCode.CONSENT_NOT_CONFIRMED);
    application.approve(
        request.supportScope(), operator, LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
    histories.saveAndFlush(SupportApplicationStatusHistory.approved(application));
    return ApproveSupportApplicationResponse.from(application);
  }
}
