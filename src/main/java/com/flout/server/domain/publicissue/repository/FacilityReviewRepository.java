package com.flout.server.domain.publicissue.repository;

import com.flout.server.domain.publicissue.entity.FacilityReview;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FacilityReviewRepository extends JpaRepository<FacilityReview, Long> {
  Optional<FacilityReview> findByLocalProblemId(Long localProblemId);
}
