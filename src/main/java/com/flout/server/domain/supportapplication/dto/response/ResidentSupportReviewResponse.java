package com.flout.server.domain.supportapplication.dto.response;

import com.flout.server.domain.inspectionapplication.entity.OccupancyType;
import com.flout.server.domain.inspectionapplication.entity.PreferredTimeSlot;
import com.flout.server.domain.supportapplication.entity.SupportApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.*;

public record ResidentSupportReviewResponse(
    @Schema(description = "지원 신청 ID", example = "201") Long supportApplicationId,
    @Schema(description = "현장점검 신청 ID", example = "101") Long applicationId,
    @Schema(description = "현장점검 신청 ID를 최소 6자리로 표시한 접수번호", example = "000101") String receiptNo,
    @Schema(description = "지원 신청 당시 이름", example = "김주민") String applicantName,
    @Schema(description = "지원 신청 당시 주소", example = "경기도 부천시 원미구 지봉로 43") String address,
    @Schema(nullable = true) String detailAddress,
    @Schema(description = "지원사업명", example = "침수 예방시설 설치 지원") String programName,
    @Schema(description = "지원 신청 상태", example = "SUBMITTED") SupportApplicationStatus status,
    LocalDateTime submittedAt,
    @Schema(description = "신청 시설", example = "출입구 물막이판") String requestedFacility,
    OccupancyType occupancyType,
    @Schema(description = "설치 희망 방문일. 확정 시공 일정이 아닙니다.") LocalDate preferredConstructionDate,
    PreferredTimeSlot preferredTimeSlot) {}
