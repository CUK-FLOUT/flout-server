package com.flout.server.domain.priorityinspection.service;

import com.flout.server.domain.priorityinspection.entity.PriorityInspectionDesignation;
import com.flout.server.domain.priorityinspection.repository.PriorityInspectionDesignationRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PriorityInspectionDesignationReader {
  private final PriorityInspectionAccess access;
  private final PriorityInspectionDesignationRepository designations;

  Map<Long, PriorityInspectionDesignation> findAllByApplicationId() {
    return withinJurisdiction(designations.findAllByOrderBySelectionOrderAscApplicationIdAsc())
        .collect(
            Collectors.toMap(PriorityInspectionDesignation::getApplicationId, Function.identity()));
  }

  List<PriorityInspectionDesignation> findWithoutCompletedInspections() {
    return withinJurisdiction(designations.findAllWithoutCompletedInspections()).toList();
  }

  List<PriorityInspectionDesignation> findActiveOrder() {
    return withinJurisdiction(designations.findActiveOrder()).toList();
  }

  private Stream<PriorityInspectionDesignation> withinJurisdiction(
      List<PriorityInspectionDesignation> items) {
    return items.stream().filter(item -> access.includes(item.getApplication().getAddress()));
  }
}
