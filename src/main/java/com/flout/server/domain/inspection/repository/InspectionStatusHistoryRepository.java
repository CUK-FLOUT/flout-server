package com.flout.server.domain.inspection.repository;

import com.flout.server.domain.inspection.entity.InspectionStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionStatusHistoryRepository
    extends JpaRepository<InspectionStatusHistory, Long> {}
