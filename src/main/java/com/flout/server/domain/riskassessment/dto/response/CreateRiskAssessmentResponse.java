package com.flout.server.domain.riskassessment.dto.response;

import com.flout.server.domain.riskassessment.entity.RiskAssessmentStatus;
import com.flout.server.domain.riskassessment.entity.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

public record CreateRiskAssessmentResponse(
    @Schema(description = "현장점검 신청에 사용할 분석 결과 ID", example = "1") Long assessmentId,
    @Schema(description = "분석 지표 유형", example = "REGIONAL_RELATIVE_VULNERABILITY") String riskType,
    @Schema(description = "0~1 사이 모델 점수. 실제 침수 확률이 아님", example = "0.2841") BigDecimal score,
    @Schema(description = "침수 취약도 등급", example = "HIGH") RiskLevel riskLevel,
    @Schema(description = "분석 상태", example = "COMPLETED") RiskAssessmentStatus status,
    @Schema(description = "모델 점수를 높인 주요 지형 근거. 최대 3개") List<String> reasons,
    @Schema(description = "결과 해석 시 함께 표시할 주의 문구", example = "지역 상대 취약도이며 개별 주택 침수 위험도가 아닙니다.")
        String disclaimer) {}
