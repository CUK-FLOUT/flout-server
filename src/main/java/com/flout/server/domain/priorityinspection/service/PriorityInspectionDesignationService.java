package com.flout.server.domain.priorityinspection.service;

import com.flout.server.domain.inspectionapplication.repository.InspectionApplicationRepository;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationErrorCode;
import com.flout.server.domain.priorityinspection.dto.response.PriorityInspectionDesignationResponse;
import com.flout.server.domain.priorityinspection.entity.PriorityInspectionDesignation;
import com.flout.server.domain.priorityinspection.repository.PriorityInspectionDesignationRepository;
import com.flout.server.domain.priorityinspection.response.code.PriorityInspectionErrorCode;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PriorityInspectionDesignationService {
  private static final Pattern BUCHEON_ADDRESS =
      Pattern.compile("^(?:(?:경기도|경기)\\s+)?부천시(?:\\s|$)");
  private final UserRepository users;
  private final InspectionApplicationRepository applications;
  private final PriorityInspectionDesignationRepository designations;
  private final Clock inspectionClock;

  // 중복 지정 방지 및 잠금 대기 후 최신 지정 결과 조회
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public PriorityInspectionDesignationResponse designate(
      Long operatorId, Long applicationId, boolean designated) {
    if (applicationId == null || applicationId <= 0)
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    var operator =
        users
            .findById(operatorId)
            .orElseThrow(() -> new BaseException(GeneralErrorCode.UNAUTHORIZED_ERROR));
    if (operator.getRole() != UserRole.ADMIN)
      throw new BaseException(GeneralErrorCode.ACCESS_DENIED_REQUEST);
    var application =
        applications
            .findForPriorityDesignation(applicationId)
            .orElseThrow(
                () -> new BaseException(InspectionApplicationErrorCode.APPLICATION_NOT_FOUND));
    if (operator.getJurisdiction() == null
        || !"부천시".equals(operator.getJurisdiction().getName())
        || !BUCHEON_ADDRESS.matcher(application.getAddress().strip()).find())
      throw new BaseException(PriorityInspectionErrorCode.JURISDICTION_ACCESS_DENIED);
    var existing = designations.findById(applicationId).orElse(null);
    if (!designated) {
      if (existing != null) {
        designations.delete(existing);
        designations.flush();
      }
      return PriorityInspectionDesignationResponse.cancelled(applicationId);
    }
    if (existing != null) return PriorityInspectionDesignationResponse.from(existing);
    LocalDateTime now = LocalDateTime.now(inspectionClock).truncatedTo(ChronoUnit.SECONDS);
    var designation =
        designations.saveAndFlush(
            PriorityInspectionDesignation.designated(application, operator, now));
    return PriorityInspectionDesignationResponse.from(designation);
  }
}
