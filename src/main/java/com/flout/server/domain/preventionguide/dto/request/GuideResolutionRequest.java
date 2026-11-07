package com.flout.server.domain.preventionguide.dto.request;

import com.flout.server.domain.preventionguide.entity.GuideResolution;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record GuideResolutionRequest(
    @NotNull @Schema(
            description = "CONFIRMED: 확인 완료, NEEDS_INSPECTION: 추가 점검 필요",
            example = "CONFIRMED",
            requiredMode = Schema.RequiredMode.REQUIRED)
        GuideResolution resolution) {}
