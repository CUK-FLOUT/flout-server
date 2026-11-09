package com.flout.server.domain.supportapplication.dto.response;

import com.flout.server.domain.supportapplication.entity.SupportApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.*;

public record ResidentSupportApprovalResponse(
    Long supportApplicationId,
    Long applicationId,
    String receiptNo,
    String applicantName,
    String address,
    @Schema(nullable = true) String detailAddress,
    String programName,
    SupportApplicationStatus status,
    @Schema(nullable = true, description = "DB에 저장된 승인 시각") LocalDateTime approvedAt,
    @Schema(description = "주민이 신청한 시설. 승인 범위와 구분합니다.") String requestedFacility,
    @Schema(nullable = true, description = "운영자가 승인 시 저장한 지원 범위") String supportScope,
    @Schema(nullable = true, description = "저장된 확정 시공일. 일정 미배정이면 null이며 희망일로 대체하지 않습니다.")
        LocalDate scheduledConstructionDate,
    @Schema(nullable = true, description = "저장된 확정 시공 시간. 일정 미배정이면 null")
        LocalTime scheduledConstructionTime) {}
