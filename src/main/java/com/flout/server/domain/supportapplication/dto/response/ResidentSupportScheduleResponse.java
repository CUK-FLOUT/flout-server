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
    @Schema(allowableValues = {"APPROVED"}) SupportApplicationStatus status,
    String requestedFacility,
    @Schema(nullable = true, description = "확정 시공 일자. 미확정 시 null")
        LocalDate scheduledConstructionDate,
    @Schema(nullable = true, description = "확정 시공 시간. 미확정 시 null")
        LocalTime scheduledConstructionTime) {}
