package com.flout.server.domain.supportapplication.dto.response;

import com.flout.server.domain.supportapplication.entity.SupportApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.*;

public record ResidentSupportApprovalResponse(
    @Schema(description = "지원 신청 ID", example = "201") Long supportApplicationId,
    @Schema(description = "현장점검 신청 ID", example = "101") Long applicationId,
    @Schema(description = "현장점검 신청 ID를 최소 6자리로 표시한 접수번호", example = "000101") String receiptNo,
    @Schema(description = "지원 신청 당시 이름", example = "김주민") String applicantName,
    @Schema(description = "지원 신청 당시 주소", example = "경기도 부천시 원미구 지봉로 43") String address,
    @Schema(nullable = true) String detailAddress,
    @Schema(description = "지원사업명", example = "침수 예방시설 설치 지원") String programName,
    @Schema(description = "지원 신청 상태", example = "APPROVED") SupportApplicationStatus status,
    @Schema(nullable = true, description = "DB에 저장된 승인 시각") LocalDateTime approvedAt,
    @Schema(description = "주민이 신청한 시설. 승인 범위와 구분합니다.", example = "출입구 물막이판")
        String requestedFacility,
    @Schema(nullable = true, description = "운영자가 승인 시 저장한 지원 범위", example = "출입구 물막이판 설치 지원")
        String supportScope,
    @Schema(nullable = true, description = "저장된 확정 시공일. 일정 미배정이면 null이며 희망일로 대체하지 않습니다.")
        LocalDate scheduledConstructionDate,
    @Schema(nullable = true, description = "저장된 확정 시공 시간. 일정 미배정이면 null")
        LocalTime scheduledConstructionTime) {}
