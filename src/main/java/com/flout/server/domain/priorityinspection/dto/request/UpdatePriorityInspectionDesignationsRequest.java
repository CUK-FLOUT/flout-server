package com.flout.server.domain.priorityinspection.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record UpdatePriorityInspectionDesignationsRequest(
    @Schema(description = "지정·취소할 신청 ID 목록. 중복 불가. 신규 지정은 이 순서로 추가", example = "[5, 6, 7]")
        @NotEmpty List<@NotNull @Positive Long> applicationIds,
    @Schema(description = "true: 일괄 지정, false: 일괄 취소", example = "true") @NotNull Boolean designated) {}
