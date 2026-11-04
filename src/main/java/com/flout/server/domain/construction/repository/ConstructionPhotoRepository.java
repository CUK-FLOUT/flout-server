package com.flout.server.domain.construction.repository;

import com.flout.server.domain.construction.entity.ConstructionPhoto;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConstructionPhotoRepository extends JpaRepository<ConstructionPhoto, Long> {
  List<ConstructionPhoto> findByConstructionIdOrderByIdAsc(Long constructionId);
}
