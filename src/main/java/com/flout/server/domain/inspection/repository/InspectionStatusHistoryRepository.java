package com.flout.server.domain.inspection.repository;

import com.flout.server.domain.inspection.entity.InspectionStatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionStatusHistoryRepository
    extends JpaRepository<InspectionStatusHistory, Long> {
  @EntityGraph(attributePaths = "changedBy")
  List<InspectionStatusHistory> findByInspectionIdOrderByChangedAtAscIdAsc(Long inspectionId);
}
