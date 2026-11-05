package com.flout.server.domain.riskassessment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRiskAssessmentRequest(
    @NotBlank(message = "주소는 필수입니다.") @Size(max = 255, message = "주소는 255자 이하여야 합니다.") @Schema(example = "경기도 부천시 원미구 지봉로 43")
        String address,
    @NotBlank(message = "상세 주소는 필수입니다.") @Size(max = 255, message = "상세 주소는 255자 이하여야 합니다.") @Schema(example = "101호")
        String detailAddress) {}
