package com.flout.server.domain.inspectionapplication.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ResidentRecommendationsResponse(
    @Schema(description = "현장점검 신청 ID", example = "101") Long applicationId,
    @Schema(description = "공개된 권장 예방조치. 저장된 표시 순서와 원문을 유지하며 없으면 빈 배열입니다.")
        List<RecommendedAction> recommendedActions) {
  public record RecommendedAction(
      @Schema(description = "운영자가 등록한 권장 예방조치 원문", example = "출입구에 물막이판을 설치하세요.")
          String description) {}
}
