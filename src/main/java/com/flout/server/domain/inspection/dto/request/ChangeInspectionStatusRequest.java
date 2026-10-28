package com.flout.server.domain.inspection.dto.request;

import com.flout.server.domain.inspection.entity.InspectionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record ChangeInspectionStatusRequest(
    @NotNull(message = "점검 상태를 선택해주세요.") @Schema(
            description = "SCHEDULED: 예정, IN_PROGRESS: 진행, COMPLETED: 완료. WAITING은 요청할 수 없습니다.",
            allowableValues = {"SCHEDULED", "IN_PROGRESS", "COMPLETED"},
            example = "SCHEDULED")
        InspectionStatus status) {}
