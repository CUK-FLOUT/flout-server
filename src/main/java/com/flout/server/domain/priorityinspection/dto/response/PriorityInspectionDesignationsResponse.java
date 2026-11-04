package com.flout.server.domain.priorityinspection.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record PriorityInspectionDesignationsResponse(
    @Schema(description = "요청 ID 순서의 지정·취소 결과")
        List<PriorityInspectionDesignationResponse> applications) {}
