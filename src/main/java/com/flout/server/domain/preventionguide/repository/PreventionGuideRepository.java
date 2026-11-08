package com.flout.server.domain.preventionguide.repository;

import com.flout.server.domain.preventionguide.entity.PreventionGuide;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PreventionGuideRepository extends JpaRepository<PreventionGuide, Long> {
  boolean existsByIdAndAssessmentId(Long id, Long assessmentId);

  Optional<PreventionGuide> findByAssessmentId(Long assessmentId);

  @Query("select g.id from PreventionGuide g where g.assessment.id = :assessmentId")
  Optional<Long> findGuideIdByAssessmentId(@Param("assessmentId") Long assessmentId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select g from PreventionGuide g where g.id = :id")
  Optional<PreventionGuide> findLockedById(@Param("id") Long id);
}
