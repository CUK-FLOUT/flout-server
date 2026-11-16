package com.flout.server.domain.priorityinspection.service;

import com.flout.server.domain.inspection.repository.InspectionRepository;
import com.flout.server.domain.inspectionapplication.entity.InspectionApplication;
import com.flout.server.domain.inspectionapplication.repository.InspectionApplicationRepository;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationErrorCode;
import com.flout.server.domain.priorityinspection.dto.request.UpdatePriorityInspectionDesignationsRequest;
import com.flout.server.domain.priorityinspection.dto.response.PriorityInspectionDesignationResponse;
import com.flout.server.domain.priorityinspection.dto.response.PriorityInspectionDesignationsResponse;
import com.flout.server.domain.priorityinspection.entity.PriorityInspectionDesignation;
import com.flout.server.domain.priorityinspection.repository.PriorityInspectionDesignationRepository;
import com.flout.server.domain.priorityinspection.response.code.PriorityInspectionErrorCode;
import com.flout.server.domain.user.entity.User;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
  private final PriorityInspectionDesignationReader reader;
  private final Clock inspectionClock;
  private final InspectionRepository inspections;

  // 전체 검증을 마친 뒤 일괄 변경하고, 실패하면 모두 롤백한다.
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public PriorityInspectionDesignationsResponse designateAll(
      Long operatorId, List<Long> applicationIds, boolean designated) {
    validateApplicationIds(applicationIds);
    var operator = access.lockJurisdiction(operatorId);
    var found = lockApplications(applicationIds);
    found.values().forEach(application -> access.requireIncludedAddress(application.getAddress()));
    var current = reader.findAllByApplicationId();
    var now = LocalDateTime.now(inspectionClock).truncatedTo(ChronoUnit.SECONDS);
    var results =
        designated
            ? designate(applicationIds, found, current, operator, now)
            : cancel(applicationIds, current, operator, now);
    return new PriorityInspectionDesignationsResponse(results);
  }

  private static void validateApplicationIds(List<Long> applicationIds) {
    if (applicationIds == null
        || applicationIds.isEmpty()
        || applicationIds.size() > UpdatePriorityInspectionDesignationsRequest.MAX_APPLICATIONS
        || applicationIds.stream().anyMatch(id -> id == null || id <= 0))
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY);
    if (new HashSet<>(applicationIds).size() != applicationIds.size())
      throw new BaseException(PriorityInspectionErrorCode.DUPLICATE_APPLICATION);
  }

  private Map<Long, InspectionApplication> lockApplications(List<Long> applicationIds) {
    var found = new LinkedHashMap<Long, InspectionApplication>();
    // ID 오름차순 잠금으로 교착 방지
    for (Long id : applicationIds.stream().sorted().toList()) {
      found.put(
          id,
          applications
              .findForPriorityDesignation(id)
              .orElseThrow(
                  () -> new BaseException(InspectionApplicationErrorCode.APPLICATION_NOT_FOUND)));
    }
    return found;
  }

  private List<PriorityInspectionDesignationResponse> designate(
      List<Long> applicationIds,
      Map<Long, InspectionApplication> found,
      Map<Long, PriorityInspectionDesignation> current,
      User operator,
      LocalDateTime now) {
    var completed = new HashSet<>(inspections.findCompletedApplicationIds(applicationIds));
    int lastOrder =
        reader.findWithoutCompletedInspections().stream()
            .mapToInt(PriorityInspectionDesignation::getSelectionOrder)
            .max()
            .orElse(0);
    // 기존 지정은 유지하고 신규 신청만 요청 순서대로 추가
    for (Long id : applicationIds) {
      if (current.containsKey(id)) continue;
      var item = PriorityInspectionDesignation.designated(found.get(id), operator, now);
      // 완료된 신청을 새로 지정해도 미완료 대상의 순위에는 포함하지 않는다.
      item.changeOrder(completed.contains(id) ? lastOrder + 1 : ++lastOrder, operator, now);
      current.put(id, designations.saveAndFlush(item));
    }
    return applicationIds.stream()
        .map(id -> PriorityInspectionDesignationResponse.from(current.get(id)))
        .toList();
  }

  private List<PriorityInspectionDesignationResponse> cancel(
      List<Long> applicationIds,
      Map<Long, PriorityInspectionDesignation> current,
      User operator,
      LocalDateTime now) {
    var removed = applicationIds.stream().filter(current::containsKey).map(current::get).toList();
    if (!removed.isEmpty()) {
      designations.deleteAll(removed);
      designations.flush();
      // 취소 후 남은 순위를 한 번만 정리
      var remaining = reader.findWithoutCompletedInspections();
      for (int index = 0; index < remaining.size(); index++)
        remaining.get(index).changeOrder(index + 1, operator, now);
      designations.flush();
    }
    return applicationIds.stream().map(PriorityInspectionDesignationResponse::cancelled).toList();
  }
}
