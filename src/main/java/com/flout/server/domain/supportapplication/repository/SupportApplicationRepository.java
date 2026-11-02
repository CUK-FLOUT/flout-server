package com.flout.server.domain.supportapplication.repository;

import com.flout.server.domain.supportapplication.entity.SupportApplication;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportApplicationRepository extends JpaRepository<SupportApplication, Long> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query(
      "select a from SupportApplication a where a.id = :id")
  java.util.Optional<SupportApplication> findForDocumentUpload(
      @org.springframework.data.repository.query.Param("id") Long id);

  boolean existsByApplicationIdAndProgramId(Long applicationId, Long programId);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query(
      "select a from SupportApplication a where a.id = :id")
  java.util.Optional<SupportApplication> findForApproval(
      @org.springframework.data.repository.query.Param("id") Long id);
}
