package com.flout.server.domain.inspectionapplication.repository;

import com.flout.server.domain.inspectionapplication.entity.InspectionApplication;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionApplicationRepository
    extends JpaRepository<InspectionApplication, Long> {
  boolean existsByReceiptNo(String receiptNo);
}
