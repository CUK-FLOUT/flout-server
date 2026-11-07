package com.flout.server.domain.supportapplication.repository;

import com.flout.server.domain.supportapplication.entity.SupportApplicationStatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportApplicationStatusHistoryRepository
    extends JpaRepository<SupportApplicationStatusHistory, Long> {
  @EntityGraph(attributePaths = {"application", "changedBy"})
  List<SupportApplicationStatusHistory> findByApplicationApplicationIdOrderByChangedAtAscIdAsc(
      Long applicationId);
}
