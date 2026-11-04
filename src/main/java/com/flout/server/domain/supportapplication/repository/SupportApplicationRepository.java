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
             COALESCE(SUM(CASE WHEN status IN ('SUBMITTED', 'REVIEWING') THEN 1 ELSE 0 END), 0) AS reviewingCount,
             COALESCE(SUM(CASE WHEN status = 'CONSENT_PENDING' THEN 1 ELSE 0 END), 0) AS consentRequiredCount,
             COALESCE(SUM(CASE WHEN status = 'APPROVED' THEN 1 ELSE 0 END), 0) AS approvedCount
      FROM support_applications
      WHERE status <> 'DRAFT' AND REGEXP_LIKE(TRIM(address), :addressPattern)
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

  @org.springframework.data.jpa.repository.Query(
      value =
          "SELECT s.* FROM support_applications s WHERE s.support_application_id = :id AND s.status <> 'DRAFT' AND REGEXP_LIKE(TRIM(s.address), :addressPattern)",
      nativeQuery = true)
  java.util.Optional<SupportApplication> findAdminDetail(
      @org.springframework.data.repository.query.Param("id") Long id,
      @org.springframework.data.repository.query.Param("addressPattern") String addressPattern);

  String ADMIN_SEARCH_FILTER =
      """
      FROM support_applications s
      WHERE s.status <> 'DRAFT' AND REGEXP_LIKE(TRIM(s.address), :addressPattern)
        AND (:keyword = '' OR LOCATE(:keyword, s.applicant_name) > 0
          OR LOCATE(:keyword, LPAD(CONCAT('', s.application_id),
            GREATEST(6, LENGTH(CONCAT('', s.application_id))), '0')) > 0)
      """;

  @org.springframework.data.jpa.repository.Query(
      value =
          """
      SELECT s.support_application_id AS supportApplicationId,
        LPAD(CONCAT('', s.application_id), GREATEST(6, LENGTH(CONCAT('', s.application_id))), '0') AS receiptNo,
        s.applicant_name AS applicantName, s.requested_facility AS requestedFacility,
        s.landlord_consent_status AS landlordConsentStatus, s.status AS status
      """
              + ADMIN_SEARCH_FILTER
              + " ORDER BY s.support_application_id DESC LIMIT :limit OFFSET :offset",
      nativeQuery = true)
  java.util.List<AdminListItem> searchAdminApplications(
      @org.springframework.data.repository.query.Param("addressPattern") String addressPattern,
      @org.springframework.data.repository.query.Param("keyword") String keyword,
      @org.springframework.data.repository.query.Param("limit") int limit,
      @org.springframework.data.repository.query.Param("offset") long offset);

  @org.springframework.data.jpa.repository.Query(
      value = "SELECT COUNT(*) " + ADMIN_SEARCH_FILTER,
      nativeQuery = true)
  long countAdminApplications(
      @org.springframework.data.repository.query.Param("addressPattern") String addressPattern,
      @org.springframework.data.repository.query.Param("keyword") String keyword);

  interface AdminListItem {
    Long getSupportApplicationId();

    String getReceiptNo();

    String getApplicantName();

    String getRequestedFacility();

    String getLandlordConsentStatus();

    String getStatus();
  }
}
