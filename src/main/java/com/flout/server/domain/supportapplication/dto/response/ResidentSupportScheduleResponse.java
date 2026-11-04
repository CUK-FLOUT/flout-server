package com.flout.server.domain.supportapplication.dto.response;

import com.flout.server.domain.supportapplication.entity.SupportApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;

public record ResidentSupportScheduleResponse(
    Long supportApplicationId,
    Long applicationId,
    String receiptNo,
    String applicantName,
    String address,
    @Schema(nullable = true) String detailAddress,
    String programName,
    @Schema(allowableValues = {"SCHEDULING"}) SupportApplicationStatus status,
    String requestedFacility,
    @Schema(allowableValues = {"PENDING", "VERIFIED", "NOT_REQUIRED"}) String landlordConsentStatus,
    @Schema(nullable = true, description = "확정 시공일 저장 구조가 없어 null. 희망 일정으로 대체하지 않습니다.")
        LocalDate scheduledConstructionDate,
    @Schema(nullable = true, description = "확정 시공 시간 저장 구조가 없어 null")
        LocalTime scheduledConstructionTime) {}
