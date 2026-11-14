package com.flout.server.domain.inspection.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ValidateRecommendedFacilitiesRequest(
    @NotEmpty @Size(max = 20) @Schema(
            description = "검증할 권장 예방조치 문장 1~20개. 각 문장은 null이나 공백만으로 구성될 수 없으며 공백을 포함해 최대 4000자",
            example = "[\"출입구에 물막이판 설치를 권장합니다.\", \"배수구 주변을 정리해 주세요.\"]",
            requiredMode = Schema.RequiredMode.REQUIRED)
        List<@NotBlank @Size(max = 4000) String> descriptions) {}
