package com.flout.server.domain.priorityinspection.service;

import com.flout.server.domain.inspectionapplication.repository.InspectionApplicationRepository;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationErrorCode;
import com.flout.server.domain.priorityinspection.dto.response.PriorityInspectionOrderListResponse;
import com.flout.server.domain.priorityinspection.dto.response.PriorityInspectionOrderResponse;
import com.flout.server.domain.priorityinspection.repository.PriorityInspectionCandidateRepository;
import com.flout.server.domain.priorityinspection.repository.PriorityInspectionDesignationRepository;
import com.flout.server.domain.priorityinspection.response.code.PriorityInspectionErrorCode;
import com.flout.server.global.exception.BaseException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PriorityInspectionOrderService {
  private final PriorityInspectionAccess access;
  private final InspectionApplicationRepository applications;
  private final PriorityInspectionDesignationRepository designations;
  private final Clock inspectionClock;
  private final PriorityInspectionCandidateRepository candidates;

  @Transactional(readOnly = true)
  public PriorityInspectionOrderListResponse get(Long operatorId) {
    access.requireJurisdiction(operatorId);
    var ordered =
        designations.findActiveOrder().stream()
            .filter(designation -> access.includes(designation.getApplication().getAddress()))
            .toList();
    var counts =
        candidates.nearbyCounts(
            ordered.stream().map(designation -> designation.getApplicationId()).toList());
    return new PriorityInspectionOrderListResponse(
        ordered.stream()
            .map(
                designation ->
                    PriorityInspectionOrderListResponse.Item.from(
                        designation, counts.getOrDefault(designation.getApplicationId(), 0L)))
            .toList());
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public PriorityInspectionOrderResponse save(Long operatorId, List<Long> applicationIds) {
    var operator = access.lockJurisdiction(operatorId);
    var requested = new HashSet<>(applicationIds);
    if (requested.size() != applicationIds.size())
      throw new BaseException(PriorityInspectionErrorCode.DUPLICATE_APPLICATION);
    var found = applications.findAllById(applicationIds);
    if (found.size() != applicationIds.size())
      throw new BaseException(InspectionApplicationErrorCode.APPLICATION_NOT_FOUND);
    if (found.stream().anyMatch(application -> !access.includes(application.getAddress())))
      throw new BaseException(PriorityInspectionErrorCode.JURISDICTION_ACCESS_DENIED);
    var designated =
        designations.findAllByOrderBySelectionOrderAscApplicationIdAsc().stream()
            .filter(designation -> access.includes(designation.getApplication().getAddress()))
            .collect(
                Collectors.toMap(
                    designation -> designation.getApplicationId(), Function.identity()));
    if (!designated.keySet().containsAll(requested))
      throw new BaseException(PriorityInspectionErrorCode.NOT_DESIGNATED);
    var current =
        designations.findAllWithoutCompletedInspections().stream()
            .filter(designation -> access.includes(designation.getApplication().getAddress()))
            .collect(
                Collectors.toMap(
                    designation -> designation.getApplicationId(), Function.identity()));
    if (!requested.equals(current.keySet()))
      throw new BaseException(PriorityInspectionErrorCode.ORDER_LIST_CHANGED);
    var now = LocalDateTime.now(inspectionClock).truncatedTo(ChronoUnit.SECONDS);
    for (int index = 0; index < applicationIds.size(); index++) {
      current.get(applicationIds.get(index)).changeOrder(index + 1, operator, now);
    }
    designations.flush();
    return new PriorityInspectionOrderResponse(
        applicationIds.stream()
            .map(id -> PriorityInspectionOrderResponse.Item.from(current.get(id)))
            .toList());
  }
}
