package com.flout.server.domain.priorityinspection.repository;

import com.flout.server.domain.priorityinspection.entity.PriorityInspectionDesignation;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriorityInspectionDesignationRepository
    extends JpaRepository<PriorityInspectionDesignation, Long> {
  @EntityGraph(attributePaths = "application")
  List<PriorityInspectionDesignation> findAllByOrderBySelectionOrderAscApplicationIdAsc();
}
