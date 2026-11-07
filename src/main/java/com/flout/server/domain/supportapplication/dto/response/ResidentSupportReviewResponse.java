package com.flout.server.domain.supportapplication.dto.response;

import com.flout.server.domain.inspectionapplication.entity.OccupancyType;
import com.flout.server.domain.inspectionapplication.entity.PreferredTimeSlot;
import com.flout.server.domain.supportapplication.entity.SupportApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.*;

public record ResidentSupportReviewResponse(
    Long supportApplicationId,
    Long applicationId,
    String receiptNo,
    String applicantName,
    String address,
    @Schema(nullable = true) String detailAddress,
    String programName,
    SupportApplicationStatus status,
    LocalDateTime submittedAt,
    String requestedFacility,
    OccupancyType occupancyType,
    @Schema(description = "설치 희망 방문일. 확정 시공 일정이 아닙니다.") LocalDate preferredConstructionDate,
    PreferredTimeSlot preferredTimeSlot,
    @Schema(allowableValues = {"PENDING", "VERIFIED", "NOT_REQUIRED"})
        String landlordConsentStatus) {}
