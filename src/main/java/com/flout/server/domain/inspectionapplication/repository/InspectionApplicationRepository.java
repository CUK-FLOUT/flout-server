package com.flout.server.domain.inspectionapplication.repository;

import com.flout.server.domain.inspectionapplication.entity.InspectionApplication;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InspectionApplicationRepository
    extends JpaRepository<InspectionApplication, Long> {
  @Query(
      "select a from InspectionApplication a where a.applicant.id = :userId and a.status <> com.flout.server.domain.inspectionapplication.entity.InspectionApplicationStatus.DRAFT order by coalesce(a.submittedAt, a.createdAt) desc, a.id desc")
  java.util.List<InspectionApplication> findResidentApplications(@Param("userId") Long userId);

  boolean existsByReceiptNo(String receiptNo);

  boolean existsByAssessmentId(Long assessmentId);

  @EntityGraph(attributePaths = {"applicant", "assessment"})
  Optional<InspectionApplication> findByAssessmentId(Long assessmentId);

  // 신청자와 취약도 분석 함께 조회
  @EntityGraph(attributePaths = {"applicant", "assessment"})
  @Query(
      "select a from InspectionApplication a where a.id = :applicationId and a.status <> com.flout.server.domain.inspectionapplication.entity.InspectionApplicationStatus.DRAFT")
  Optional<InspectionApplication> findForAdminDetail(@Param("applicationId") Long applicationId);

  @EntityGraph(attributePaths = {"applicant", "assessment"})
  @Query("select a from InspectionApplication a where a.id = :applicationId")
  Optional<InspectionApplication> findForResidentDetail(@Param("applicationId") Long applicationId);

  // 상세 패널에 필요한 신청자와 분석 결과 함께 조회
  @EntityGraph(attributePaths = {"applicant", "assessment"})
  @Query(
      "select a from InspectionApplication a where a.id = :applicationId and a.status <> com.flout.server.domain.inspectionapplication.entity.InspectionApplicationStatus.DRAFT")
  Optional<InspectionApplication> findForPriorityDetail(@Param("applicationId") Long applicationId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select a from InspectionApplication a where a.id = :applicationId and a.status <> com.flout.server.domain.inspectionapplication.entity.InspectionApplicationStatus.DRAFT")
  Optional<InspectionApplication> findForPriorityDesignation(
      @Param("applicationId") Long applicationId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from InspectionApplication a where a.id = :applicationId")
  Optional<InspectionApplication> findForAttachmentUpload(
      @Param("applicationId") Long applicationId);

  @Modifying
  @Query("update InspectionApplication a set a.internalMemo = :memo where a.id = :applicationId")
  int updateInternalMemo(@Param("applicationId") Long applicationId, @Param("memo") String memo);

  @Query(
      value =
          """
                    SELECT a.*
                    FROM inspection_applications a
                    WHERE REGEXP_REPLACE(
                            REGEXP_REPLACE(
                                TRIM(a.address), '[[:space:]]+', ' '
                            ),
                            '^경기 ', '경기도 '
                          ) = :normalizedAddress
                      AND a.status <> 'DRAFT'
                    ORDER BY a.created_at DESC, a.application_id DESC
                    """,
      nativeQuery = true)
  List<InspectionApplication> findRelatedByNormalizedAddress(
      @Param("normalizedAddress") String normalizedAddress);
}
