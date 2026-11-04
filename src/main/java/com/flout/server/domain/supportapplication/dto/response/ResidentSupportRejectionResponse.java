package com.flout.server.domain.supportapplication.dto.response;

import com.flout.server.domain.supportapplication.entity.SupportApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.*;

public record ResidentSupportRejectionResponse(
    Long supportApplicationId,
    Long applicationId,
    String receiptNo,
    String applicantName,
    String address,
    @Schema(nullable = true) String detailAddress,
    String programName,
    SupportApplicationStatus status,
    String requestedFacility,
    @Schema(nullable = true, description = "DB에 저장된 실제 미승인 사유") String rejectionReason,
    @Schema(nullable = true, description = "DB의 승인·미승인 공용 처리 시각") LocalDateTime rejectedAt,
    @Schema(nullable = true, description = "현장점검 결과 다시 보기용 점검 ID") Long inspectionId,
    boolean inspectionResultPublished) {}
