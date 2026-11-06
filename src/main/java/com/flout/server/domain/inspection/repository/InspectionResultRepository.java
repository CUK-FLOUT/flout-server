package com.flout.server.domain.inspection.repository;

import com.flout.server.domain.inspection.entity.InspectionResult;
import com.flout.server.domain.inspection.entity.ResultStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionResultRepository extends JpaRepository<InspectionResult, Long> {
  java.util.List<InspectionResult> findByInspectionIdInAndResultStatus(
      java.util.Collection<Long> inspectionIds, ResultStatus resultStatus);

  Optional<InspectionResult> findByInspectionIdAndResultStatus(
      Long inspectionId, ResultStatus resultStatus);
}
