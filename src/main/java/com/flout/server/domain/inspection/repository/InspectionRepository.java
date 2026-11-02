package com.flout.server.domain.inspection.repository;

import com.flout.server.domain.inspection.entity.Inspection;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface InspectionRepository extends JpaRepository<Inspection, Long> {
  Optional<Inspection> findByApplicationId(Long applicationId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from Inspection i where i.id = :inspectionId")
  Optional<Inspection> findForStatusUpdate(@Param("inspectionId") Long inspectionId);

  @Query(
      "select i.id from Inspection i where i.status = com.flout.server.domain.inspection.entity.InspectionStatus.SCHEDULED and i.scheduledAt <= :now order by i.scheduledAt, i.id")
  List<Long> findDueInspectionIds(@Param("now") LocalDateTime now, Pageable pageable);
}
