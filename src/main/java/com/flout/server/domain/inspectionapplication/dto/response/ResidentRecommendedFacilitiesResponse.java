package com.flout.server.domain.inspectionapplication.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ResidentRecommendedFacilitiesResponse(
    @Schema(description = "현장점검 신청 ID", example = "101") Long applicationId,
    @Schema(description = "공개된 권장 예방조치 수. 추출된 시설 수와 다를 수 있습니다.", example = "2")
        int recommendationCount,
    @Schema(description = "설치 의도가 명확한 시설명. 중복을 제외하며 없으면 빈 배열") List<String> facilities) {}
