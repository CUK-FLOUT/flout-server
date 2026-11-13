package com.flout.server.domain.supportapplication.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관할 내 지원 신청 현황 건수")
public record SupportApplicationStatusResponse(
    @Schema(description = "DRAFT를 제외한 전체 건수", example = "12") long totalCount,
    @Schema(description = "SUBMITTED 상태 건수", example = "7") long reviewingCount,
    @Schema(description = "REJECTED 상태 건수", example = "2") long rejectedCount,
    @Schema(description = "APPROVED 상태 건수", example = "3") long approvedCount) {}
