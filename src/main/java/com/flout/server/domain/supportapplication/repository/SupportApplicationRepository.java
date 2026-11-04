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

  @org.springframework.data.jpa.repository.Query(
      value =
          """
      SELECT COUNT(*) AS totalCount,
             COALESCE(SUM(CASE WHEN status = 'REVIEWING' THEN 1 ELSE 0 END), 0) AS reviewingCount,
             COALESCE(SUM(CASE WHEN status = 'CONSENT_PENDING' THEN 1 ELSE 0 END), 0) AS consentRequiredCount,
             COALESCE(SUM(CASE WHEN status = 'APPROVED' THEN 1 ELSE 0 END), 0) AS approvedCount
      FROM support_applications
      WHERE REGEXP_LIKE(TRIM(address), :addressPattern)
      """,
      nativeQuery = true)
  StatusCounts countStatusByJurisdiction(
      @org.springframework.data.repository.query.Param("addressPattern") String addressPattern);

  interface StatusCounts {
    long getTotalCount();

    long getReviewingCount();

    long getConsentRequiredCount();

    long getApprovedCount();
  }
}
