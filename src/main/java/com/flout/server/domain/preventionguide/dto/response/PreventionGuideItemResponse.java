package com.flout.server.domain.preventionguide.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record PreventionGuideItemResponse(
    @Schema(example = "1") int order,
    @Schema(example = "DRAIN") String ruleId,
    @Schema(example = "배수구·빗물받이 막힘 확인") String title,
    @Schema(example = "낙엽과 쓰레기를 치우고 물이 빠지는지 확인해 주세요.") String description,
    @Schema(description = "생성 직후에는 false", example = "false") boolean completed) {}
