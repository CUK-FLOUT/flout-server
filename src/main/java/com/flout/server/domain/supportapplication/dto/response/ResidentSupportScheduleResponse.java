package com.flout.server.domain.supportapplication.dto.response;

import com.flout.server.domain.supportapplication.entity.SupportApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;

public record ResidentSupportScheduleResponse(
    @Schema(description = "지원 신청 ID", example = "201") Long supportApplicationId,
    @Schema(description = "현장점검 신청 ID", example = "101") Long applicationId,
    @Schema(description = "현장점검 신청 ID를 최소 6자리로 표시한 접수번호", example = "000101") String receiptNo,
    @Schema(description = "지원 신청 당시 이름", example = "김주민") String applicantName,
    @Schema(description = "지원 신청 당시 주소", example = "경기도 부천시 원미구 지봉로 43") String address,
    @Schema(nullable = true) String detailAddress,
    @Schema(description = "지원사업명", example = "침수 예방시설 설치 지원") String programName,
    @Schema(
            description = "지원 신청 상태",
            allowableValues = {"APPROVED"},
            example = "APPROVED")
        SupportApplicationStatus status,
    @Schema(description = "신청 시설", example = "출입구 물막이판") String requestedFacility,
    @Schema(nullable = true, description = "확정 시공 일자. 미확정 시 null")
        LocalDate scheduledConstructionDate,
    @Schema(nullable = true, description = "확정 시공 시간. 미확정 시 null")
        LocalTime scheduledConstructionTime) {}
