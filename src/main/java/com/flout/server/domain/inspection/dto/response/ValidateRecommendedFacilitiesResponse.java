package com.flout.server.domain.inspection.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ValidateRecommendedFacilitiesResponse(
    @Schema(description = "추출된 시설이 하나 이상이면 true", example = "true") boolean hasFacilities,
    @Schema(
            description = "추출 순서대로 중복을 제거한 시설명. 위치가 명시되면 위치를 포함하며 없으면 빈 배열",
            example = "[\"출입구 물막이판\"]")
        List<String> facilities) {}
