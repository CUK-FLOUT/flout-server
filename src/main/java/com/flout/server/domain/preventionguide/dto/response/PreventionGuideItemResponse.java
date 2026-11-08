package com.flout.server.domain.preventionguide.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record PreventionGuideItemResponse(
    @Schema(description = "체크리스트 항목 ID", example = "1") Long itemId,
    @Schema(example = "1") int order,
    @Schema(example = "DRAIN") String ruleId,
    @Schema(example = "배수구·빗물받이 막힘 확인") String title,
    @Schema(example = "낙엽과 쓰레기를 치우고 물이 빠지는지 확인해 주세요.") String description,
    @Schema(description = "저장된 완료 여부", example = "false") boolean completed) {}
