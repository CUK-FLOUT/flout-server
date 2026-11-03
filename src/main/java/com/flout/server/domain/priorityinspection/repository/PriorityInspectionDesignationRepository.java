package com.flout.server.domain.priorityinspection.repository;

import com.flout.server.domain.priorityinspection.entity.PriorityInspectionDesignation;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PriorityInspectionDesignationRepository
    extends JpaRepository<PriorityInspectionDesignation, Long> {
  @EntityGraph(attributePaths = "application")
  List<PriorityInspectionDesignation> findAllByOrderBySelectionOrderAscApplicationIdAsc();

  @EntityGraph(attributePaths = "application")
  @Query(
      "select d from PriorityInspectionDesignation d where not exists "
          + "(select i.id from Inspection i where i.application = d.application "
          + "and i.status = com.flout.server.domain.inspection.entity.InspectionStatus.COMPLETED) "
          + "order by d.selectionOrder, d.applicationId")
  List<PriorityInspectionDesignation> findAllWithoutCompletedInspections();

  @EntityGraph(attributePaths = {"application", "application.assessment"})
  @Query(
      """
      select d from PriorityInspectionDesignation d
      where d.application.status <> com.flout.server.domain.inspectionapplication.entity.InspectionApplicationStatus.DRAFT
        and not exists (select i.id from Inspection i where i.application = d.application
          and i.status = com.flout.server.domain.inspection.entity.InspectionStatus.COMPLETED)
      order by d.selectionOrder, d.applicationId
      """)
  List<PriorityInspectionDesignation> findActiveOrder();
}
