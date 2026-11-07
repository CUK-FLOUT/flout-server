package com.flout.server.domain.construction.repository;

import com.flout.server.domain.construction.entity.ConstructionStatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.*;

public interface ConstructionStatusHistoryRepository
    extends JpaRepository<ConstructionStatusHistory, Long> {
  List<ConstructionStatusHistory> findByConstructionIdOrderByChangedAtAscIdAsc(Long constructionId);

  @EntityGraph(attributePaths = {"construction.supportApplication", "changedBy"})
  List<ConstructionStatusHistory>
      findByConstructionSupportApplicationApplicationIdOrderByChangedAtAscIdAsc(Long applicationId);
}
