package com.flout.server.domain.preventionguide.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

public record PreventionGuideResponse(
    @Schema(example = "1") Long guideId,
    @Schema(example = "1") Long assessmentId,
    @Schema(example = "이것부터 확인하세요") String title,
    @Schema(example = "우리 집 주변을 먼저 살펴보고, 할 수 있는 것부터 준비해 주세요.") String intro,
    @Schema(example = "0") int completedCount,
    @Schema(example = "4") int totalCount,
    List<PreventionGuideItemResponse> items,
    OffsetDateTime generatedAt) {}
