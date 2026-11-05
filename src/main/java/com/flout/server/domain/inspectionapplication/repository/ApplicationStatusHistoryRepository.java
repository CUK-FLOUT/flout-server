package com.flout.server.domain.inspectionapplication.repository;

import com.flout.server.domain.inspectionapplication.entity.ApplicationStatusHistory;
import com.flout.server.domain.inspectionapplication.entity.InspectionApplicationStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationStatusHistoryRepository
    extends JpaRepository<ApplicationStatusHistory, Long> {
  Optional<ApplicationStatusHistory> findFirstByApplicationIdAndStatusOrderByCreatedAtAscIdAsc(
      Long applicationId, InspectionApplicationStatus status);
}
