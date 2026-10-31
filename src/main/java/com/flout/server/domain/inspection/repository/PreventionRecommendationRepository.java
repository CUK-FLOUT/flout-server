package com.flout.server.domain.inspection.repository;

import com.flout.server.domain.inspection.entity.PreventionRecommendation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PreventionRecommendationRepository
        extends JpaRepository<PreventionRecommendation, Long> {

    List<PreventionRecommendation> findByResultIdOrderBySortOrderAsc(
            Long resultId
    );
    void deleteByResultId(Long resultId);
}
