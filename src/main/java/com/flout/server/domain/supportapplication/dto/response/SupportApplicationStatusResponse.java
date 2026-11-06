package com.flout.server.domain.supportapplication.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관할 내 지원 신청 현황 건수")
public record SupportApplicationStatusResponse(
    long totalCount, long reviewingCount, long rejectedCount, long approvedCount) {}
