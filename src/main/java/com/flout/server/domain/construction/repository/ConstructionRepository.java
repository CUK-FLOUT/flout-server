package com.flout.server.domain.construction.repository;

import com.flout.server.domain.construction.entity.Construction;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ConstructionRepository extends JpaRepository<Construction, Long> {
  String ADMIN_LIST_FILTER =
      """
      FROM constructions c
      JOIN support_applications s ON s.support_application_id = c.support_application_id
      WHERE s.status = 'APPROVED' AND REGEXP_LIKE(TRIM(s.address), :addressPattern)
        AND (:status = ''
          OR (:status = 'SCHEDULED' AND c.status IN ('PENDING', 'SCHEDULED'))
          OR (:status <> 'SCHEDULED' AND CONCAT('', c.status) = :status))
        AND (:keyword = '' OR LOCATE(:keyword, s.applicant_name) > 0
          OR LOCATE(:keyword, LPAD(CONCAT('', s.application_id),
            GREATEST(6, LENGTH(CONCAT('', s.application_id))), '0')) > 0)
      """;

  @Query(
      value =
          """
          SELECT c.construction_id AS constructionId,
            LPAD(CONCAT('', s.application_id),
              GREATEST(6, LENGTH(CONCAT('', s.application_id))), '0') AS receiptNo,
            s.applicant_name AS applicantName,
            s.requested_facility AS requestedFacility,
            c.scheduled_at AS scheduledAt,
            c.status AS status
          """
              + ADMIN_LIST_FILTER
              + " ORDER BY c.construction_id DESC LIMIT :limit OFFSET :offset",
      nativeQuery = true)
  List<AdminListItem> searchAdminConstructions(
      @Param("addressPattern") String addressPattern,
      @Param("keyword") String keyword,
      @Param("status") String status,
      @Param("limit") int limit,
      @Param("offset") long offset);

  @Query(value = "SELECT COUNT(*) " + ADMIN_LIST_FILTER, nativeQuery = true)
  long countAdminConstructions(
      @Param("addressPattern") String addressPattern,
      @Param("keyword") String keyword,
      @Param("status") String status);

  interface AdminListItem {
    Long getConstructionId();

    String getReceiptNo();

    String getApplicantName();

    String getRequestedFacility();

    LocalDateTime getScheduledAt();

    String getStatus();
  }

  java.util.List<Construction> findBySupportApplicationIdIn(
      java.util.Collection<Long> supportApplicationIds);

  Optional<Construction> findBySupportApplicationId(Long supportApplicationId);

  @Query("select c.supportApplication.id from Construction c where c.id = :id")
  Optional<Long> findSupportApplicationId(@Param("id") Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Construction c where c.id = :id")
  Optional<Construction> findForScheduleUpdate(@Param("id") Long id);

  @Query(
      "select c.id from Construction c where c.status = com.flout.server.domain.construction.entity.ConstructionStatus.SCHEDULED and c.scheduledAt <= :now and c.supportApplication.status = com.flout.server.domain.supportapplication.entity.SupportApplicationStatus.APPROVED order by c.scheduledAt, c.id")
  List<Long> findDueConstructionIds(@Param("now") LocalDateTime now, Pageable pageable);
}
