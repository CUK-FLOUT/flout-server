package com.flout.server.domain.inspectionapplication.service;

import com.flout.server.domain.inspectionapplication.dto.response.ResidentRecommendationsResponse;
import com.flout.server.domain.inspectionapplication.dto.response.ResidentRecommendedFacilitiesResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ResidentRecommendedFacilitiesService {
  private final ResidentRecommendationsService recommendations;
  private final RecommendedFacilityExtractor extractor;

  @Transactional(readOnly = true)
  public ResidentRecommendedFacilitiesResponse get(Long userId, Long applicationId) {
    var actions = recommendations.get(userId, applicationId).recommendedActions();
    return new ResidentRecommendedFacilitiesResponse(
        applicationId,
        actions.size(),
        extractor.extract(
            actions.stream()
                .map(ResidentRecommendationsResponse.RecommendedAction::description)
                .toList()));
  }
}
