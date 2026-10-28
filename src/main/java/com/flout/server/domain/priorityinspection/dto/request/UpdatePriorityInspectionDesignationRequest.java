package com.flout.server.domain.priorityinspection.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdatePriorityInspectionDesignationRequest(
    @NotNull(message = "우선점검 지정 여부를 선택해주세요.") Boolean designated) {}
