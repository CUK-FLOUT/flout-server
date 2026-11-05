package com.flout.server.domain.riskassessment.dto.response;

import com.flout.server.domain.riskassessment.entity.RiskAssessmentStatus;
import com.flout.server.domain.riskassessment.entity.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;

public record CreateRiskAssessmentResponse(
    @Schema(description = "현장점검 신청에 사용할 분석 결과 ID", example = "1") Long assessmentId,
    @Schema(description = "침수 취약도 등급", example = "HIGH") RiskLevel riskLevel,
    @Schema(description = "분석 상태", example = "COMPLETED") RiskAssessmentStatus status) {}
