package com.flout.server.domain.preventionguide.repository;

import com.flout.server.domain.preventionguide.entity.PreventionGuide;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PreventionGuideRepository extends JpaRepository<PreventionGuide, Long> {
  boolean existsByIdAndAssessmentId(Long id, Long assessmentId);
}
