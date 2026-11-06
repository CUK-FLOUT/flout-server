package com.flout.server.domain.inspectionapplication.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ResidentRecommendationsResponse(
    Long applicationId,
    @Schema(description = "공개된 권장 예방조치. 운영자가 등록한 순서와 원문을 유지합니다.")
        List<RecommendedAction> recommendedActions) {
  public record RecommendedAction(
      @Schema(description = "운영자가 등록한 권장 예방조치 원문") String description) {}
}
