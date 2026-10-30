package com.flout.server.domain.inspectionapplication.repository;

import com.flout.server.domain.inspectionapplication.entity.ApplicationAttachment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationAttachmentRepository
    extends JpaRepository<ApplicationAttachment, Long> {
  long countByApplicationId(Long applicationId);

  List<ApplicationAttachment> findAllByApplicationIdAndUploadRequestKeyOrderByIdAsc(
      Long applicationId, String uploadRequestKey);
}
