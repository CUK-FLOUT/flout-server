package com.flout.server.domain.riskassessment.repository;

import com.flout.server.domain.riskassessment.entity.RiskAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, Long> {}
