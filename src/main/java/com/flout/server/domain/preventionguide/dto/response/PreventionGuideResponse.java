package com.flout.server.domain.preventionguide.dto.response;

import com.flout.server.domain.preventionguide.client.GuideGenerationSource;
import com.flout.server.domain.riskassessment.entity.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

public record PreventionGuideResponse(
    @Schema(example = "1") Long guideId,
    @Schema(example = "1") Long assessmentId,
    @Schema(example = "AI") GuideGenerationSource source,
    @Schema(example = "HIGH") RiskLevel grade,
    @Schema(example = "이것부터 확인하세요") String title,
    @Schema(example = "반지하는 대피 요령부터 확인해 주세요.") String subtitle,
    @Schema(example = "0") int completedCount,
    @Schema(example = "4") int totalCount,
    List<PreventionGuideItemResponse> items,
    OffsetDateTime generatedAt) {}
