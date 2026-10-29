package com.flout.server.domain.priorityinspection.service;

import com.flout.server.domain.inspectionapplication.entity.InspectionApplication;
import com.flout.server.domain.inspectionapplication.repository.InspectionApplicationRepository;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationErrorCode;
import com.flout.server.domain.priorityinspection.dto.response.PriorityInspectionDesignationResponse;
import com.flout.server.domain.priorityinspection.dto.response.PriorityInspectionDesignationsResponse;
import com.flout.server.domain.priorityinspection.entity.PriorityInspectionDesignation;
import com.flout.server.domain.priorityinspection.repository.PriorityInspectionDesignationRepository;
import com.flout.server.domain.priorityinspection.response.code.PriorityInspectionErrorCode;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
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

  // 전체 검증 후 일괄 변경하며 실패 시 모두 롤백
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public PriorityInspectionDesignationsResponse designateAll(
      Long operatorId, List<Long> applicationIds, boolean designated) {
    if (applicationIds == null
        || applicationIds.isEmpty()
        || applicationIds.stream().anyMatch(id -> id == null || id <= 0))
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY);
    if (new HashSet<>(applicationIds).size() != applicationIds.size())
      throw new BaseException(PriorityInspectionErrorCode.DUPLICATE_APPLICATION);
    var operator = access.lockJurisdiction(operatorId);
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
    if (found.values().stream().anyMatch(application -> !access.includes(application.getAddress())))
      throw new BaseException(PriorityInspectionErrorCode.JURISDICTION_ACCESS_DENIED);
    var current =
        designations.findAllByOrderBySelectionOrderAscApplicationIdAsc().stream()
            .filter(item -> access.includes(item.getApplication().getAddress()))
            .collect(
                Collectors.toMap(
                    PriorityInspectionDesignation::getApplicationId, Function.identity()));
    var now = LocalDateTime.now(inspectionClock).truncatedTo(ChronoUnit.SECONDS);
    if (designated) {
      int lastOrder =
          current.values().stream()
              .mapToInt(PriorityInspectionDesignation::getSelectionOrder)
              .max()
              .orElse(0);
      // 기존 지정은 유지하고 신규 신청만 요청 순서대로 추가
      for (Long id : applicationIds) {
        if (current.containsKey(id)) continue;
        var item = PriorityInspectionDesignation.designated(found.get(id), operator, now);
        item.changeOrder(++lastOrder, operator, now);
        current.put(id, designations.saveAndFlush(item));
      }
      return new PriorityInspectionDesignationsResponse(
          applicationIds.stream()
              .map(id -> PriorityInspectionDesignationResponse.from(current.get(id)))
              .toList());
    }
    var removed = applicationIds.stream().filter(current::containsKey).map(current::get).toList();
    if (!removed.isEmpty()) {
      designations.deleteAll(removed);
      designations.flush();
      // 취소 후 남은 순위를 한 번만 정리
      var remaining =
          designations.findAllByOrderBySelectionOrderAscApplicationIdAsc().stream()
              .filter(item -> access.includes(item.getApplication().getAddress()))
              .toList();
      for (int index = 0; index < remaining.size(); index++)
        remaining.get(index).changeOrder(index + 1, operator, now);
      designations.flush();
    }
    return new PriorityInspectionDesignationsResponse(
        applicationIds.stream().map(PriorityInspectionDesignationResponse::cancelled).toList());
  }
}
