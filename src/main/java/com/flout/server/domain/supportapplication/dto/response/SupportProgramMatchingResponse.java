package com.flout.server.domain.supportapplication.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "현장점검 결과와 주소를 바탕으로 매칭한 지원사업")
public record SupportProgramMatchingResponse(
    @Schema(description = "현장점검 신청 ID", example = "12") Long supportApplicationId,
    @Schema(description = "지원사업 ID", example = "3") Long programId,
    @Schema(description = "지원사업명", example = "물막이판 설치 지원") String programName,
    @Schema(description = "사업 담당 지자체", example = "부천시") String jurisdiction,
    @Schema(description = "지원사업 설명") String description,
    @Schema(description = "지원 대상 및 신청 조건") String targetDescription,
    @Schema(description = "사업 운영 상태", example = "접수중") String operatingStatus,
    @Schema(description = "이 사업이 표시된 이유", example = "현장점검에서 출입구 취약지점이 확인됐어요.") String matchedReason,
    @Schema(description = "실제 지원 대상 확정 여부. 최종 대상 여부는 신청 후 행정 검토로 결정됩니다.", example = "false")
        boolean eligibilityConfirmed) {}
