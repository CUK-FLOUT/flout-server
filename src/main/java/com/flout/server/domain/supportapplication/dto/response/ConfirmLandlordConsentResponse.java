package com.flout.server.domain.supportapplication.dto.response;

import com.flout.server.domain.supportapplication.entity.LandlordConsentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record ConfirmLandlordConsentResponse(
    @Schema(description = "지원 신청 ID", example = "15") Long supportApplicationId,
    @Schema(description = "변경된 임대인 동의 확인 상태", example = "CONFIRMED")
        LandlordConsentStatus landlordConsentStatus) {}
