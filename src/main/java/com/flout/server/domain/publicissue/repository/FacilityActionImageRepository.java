package com.flout.server.domain.publicissue.repository;

import com.flout.server.domain.publicissue.entity.FacilityActionImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FacilityActionImageRepository extends JpaRepository<FacilityActionImage, Long> {
  boolean existsByActionId(Long actionId);
}
