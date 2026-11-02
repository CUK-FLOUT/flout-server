package com.flout.server.domain.supportapplication.repository;

import com.flout.server.domain.supportapplication.entity.SupportApplication;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportApplicationRepository extends JpaRepository<SupportApplication, Long> {
  boolean existsByApplicationIdAndProgramId(Long applicationId, Long programId);
}
