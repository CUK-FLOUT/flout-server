package com.flout.server.domain.preventionguide.repository;

import com.flout.server.domain.preventionguide.entity.PreventionGuide;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PreventionGuideRepository extends JpaRepository<PreventionGuide, Long> {
  boolean existsByIdAndAssessmentId(Long id, Long assessmentId);

  Optional<PreventionGuide> findByAssessmentId(Long assessmentId);
}
