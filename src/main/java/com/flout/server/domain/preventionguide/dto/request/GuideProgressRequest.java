package com.flout.server.domain.preventionguide.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record GuideProgressRequest(
    @NotNull @Schema(
            description = "항목 완료 여부",
            example = "true",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean completed) {}
