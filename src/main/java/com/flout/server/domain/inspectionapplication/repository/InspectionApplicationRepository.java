package com.flout.server.domain.inspectionapplication.repository;

import com.flout.server.domain.inspectionapplication.entity.InspectionApplication;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InspectionApplicationRepository
    extends JpaRepository<InspectionApplication, Long> {
  boolean existsByReceiptNo(String receiptNo);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from InspectionApplication a where a.id = :applicationId")
  Optional<InspectionApplication> findForPriorityDesignation(
      @Param("applicationId") Long applicationId);

  @Modifying
  @Query("update InspectionApplication a set a.internalMemo = :memo where a.id = :applicationId")
  int updateInternalMemo(@Param("applicationId") Long applicationId, @Param("memo") String memo);
}
