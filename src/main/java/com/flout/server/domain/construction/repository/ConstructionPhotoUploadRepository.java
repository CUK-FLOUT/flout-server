package com.flout.server.domain.construction.repository;

import com.flout.server.domain.construction.entity.ConstructionPhotoUpload;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ConstructionPhotoUploadRepository
    extends JpaRepository<ConstructionPhotoUpload, Long> {
  Optional<ConstructionPhotoUpload> findByConstructionIdAndOperatorIdAndRequestKey(
      Long constructionId, Long operatorId, String requestKey);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from ConstructionPhotoUpload u where u.id = :id")
  Optional<ConstructionPhotoUpload> findForUpdate(@Param("id") Long id);
}
