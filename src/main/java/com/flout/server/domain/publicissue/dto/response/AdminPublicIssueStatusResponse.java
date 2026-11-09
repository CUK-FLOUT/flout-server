package com.flout.server.domain.publicissue.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record AdminPublicIssueStatusResponse(
    @Schema(description = "검색·필터 조건에 맞는 등록된 지역·공공 문제 수", example = "5") long registeredCount,
    @Schema(description = "검토 대기·검토 중·현장점검 필요 상태의 문제 수", example = "2")
        long pendingOrUnderReviewCount,
    @Schema(description = "조치 진행 중인 문제 수", example = "2") long actionInProgressCount,
    @Schema(description = "조치 완료된 문제 수", example = "1") long completedCount) {}
