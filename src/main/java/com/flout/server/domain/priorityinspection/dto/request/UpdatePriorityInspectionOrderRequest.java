package com.flout.server.domain.priorityinspection.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record UpdatePriorityInspectionOrderRequest(
    @Schema(
            description = "관할 지역 우선점검 대상 전체 신청 ID를 원하는 순서대로 전달. 1 이상의 실제 신청 ID 사용. 필터·페이지 밖 대상도 포함",
            example = "[3, 1, 2]")
        @NotEmpty(message = "선정 순서를 저장할 신청 목록을 입력해주세요.") List<@NotNull(message = "신청 ID는 필수입니다.") @Positive(message = "신청 ID는 양수여야 합니다.") Long>
            applicationIds) {}
