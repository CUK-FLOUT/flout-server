package com.flout.server.domain.priorityinspection.service;

import com.flout.server.domain.inspectionapplication.repository.InspectionApplicationRepository;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationErrorCode;
import com.flout.server.domain.priorityinspection.dto.response.PriorityInspectionDesignationResponse;
import com.flout.server.domain.priorityinspection.entity.PriorityInspectionDesignation;
import com.flout.server.domain.priorityinspection.repository.PriorityInspectionDesignationRepository;
import com.flout.server.domain.priorityinspection.response.code.PriorityInspectionErrorCode;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PriorityInspectionDesignationService {
  private final PriorityInspectionAccess access;
  private final InspectionApplicationRepository applications;
  private final PriorityInspectionDesignationRepository designations;
  private final Clock inspectionClock;

  // 중복 지정 방지 및 잠금 대기 후 최신 지정 결과 조회
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public PriorityInspectionDesignationResponse designate(
      Long operatorId, Long applicationId, boolean designated) {
    if (applicationId == null || applicationId <= 0)
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    var operator = access.lockJurisdiction(operatorId);
    var application =
        applications
            .findForPriorityDesignation(applicationId)
            .orElseThrow(
                () -> new BaseException(InspectionApplicationErrorCode.APPLICATION_NOT_FOUND));
    if (!access.includes(application.getAddress()))
      throw new BaseException(PriorityInspectionErrorCode.JURISDICTION_ACCESS_DENIED);
    var existing = designations.findById(applicationId).orElse(null);
    if (!designated) {
      if (existing != null) {
        designations.delete(existing);
        designations.flush();
        var remaining =
            designations.findAllByOrderBySelectionOrderAscApplicationIdAsc().stream()
                .filter(item -> access.includes(item.getApplication().getAddress()))
                .toList();
        var now = LocalDateTime.now(inspectionClock).truncatedTo(ChronoUnit.SECONDS);
        for (int index = 0; index < remaining.size(); index++) {
          remaining.get(index).changeOrder(index + 1, operator, now);
        }
        designations.flush();
      }
      return PriorityInspectionDesignationResponse.cancelled(applicationId);
    }
    if (existing != null) return PriorityInspectionDesignationResponse.from(existing);
    LocalDateTime now = LocalDateTime.now(inspectionClock).truncatedTo(ChronoUnit.SECONDS);
    int lastOrder =
        designations.findAllByOrderBySelectionOrderAscApplicationIdAsc().stream()
            .filter(item -> access.includes(item.getApplication().getAddress()))
            .mapToInt(PriorityInspectionDesignation::getSelectionOrder)
            .max()
            .orElse(0);
    var designation = PriorityInspectionDesignation.designated(application, operator, now);
    designation.changeOrder(lastOrder + 1, operator, now);
    designations.saveAndFlush(designation);
    return PriorityInspectionDesignationResponse.from(designation);
  }
}
