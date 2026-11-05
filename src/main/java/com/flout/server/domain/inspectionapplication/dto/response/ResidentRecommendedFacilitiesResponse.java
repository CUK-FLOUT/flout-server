package com.flout.server.domain.inspectionapplication.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ResidentRecommendedFacilitiesResponse(
    Long applicationId,
    @Schema(description = "공개된 권장 예방조치 수. 시설을 추출하지 못한 경우와 권장조치가 없는 경우를 구분") int recommendationCount,
    @Schema(description = "설치 의도가 명확한 시설명. 없으면 빈 배열") List<String> facilities) {}
