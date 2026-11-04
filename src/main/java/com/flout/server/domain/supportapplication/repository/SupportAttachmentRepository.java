package com.flout.server.domain.supportapplication.repository;

import com.flout.server.domain.supportapplication.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportAttachmentRepository extends JpaRepository<SupportAttachment, Long> {
  java.util.List<SupportAttachment> findByApplicationIdOrderByIdAsc(Long applicationId);

  boolean existsByApplicationIdAndAttachmentType(Long applicationId, SupportAttachmentType type);
}
