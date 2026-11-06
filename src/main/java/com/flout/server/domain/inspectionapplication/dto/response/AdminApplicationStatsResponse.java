package com.flout.server.domain.inspectionapplication.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record AdminApplicationStatsResponse(
    @Schema(description = "현재 검색 조건에 맞는 신청 전체 건수", example = "128") long totalCount,
    @Schema(description = "접수 후 현장점검 배정 대기 건수", example = "32") long assignmentPendingCount,
    @Schema(description = "현장점검 진행 중 건수", example = "8") long inspectionInProgressCount,
    @Schema(description = "지원 검토·승인 및 시공 예정·진행 중 건수", example = "24")
        long supportConstructionInProgressCount) {}
