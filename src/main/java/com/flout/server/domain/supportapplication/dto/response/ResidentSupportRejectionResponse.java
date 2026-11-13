package com.flout.server.domain.supportapplication.dto.response;

import com.flout.server.domain.supportapplication.entity.SupportApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.*;

public record ResidentSupportRejectionResponse(
    @Schema(description = "지원 신청 ID", example = "201") Long supportApplicationId,
    @Schema(description = "현장점검 신청 ID", example = "101") Long applicationId,
    @Schema(description = "현장점검 신청 ID를 최소 6자리로 표시한 접수번호", example = "000101") String receiptNo,
    @Schema(description = "지원 신청 당시 이름", example = "김주민") String applicantName,
    @Schema(description = "지원 신청 당시 주소", example = "경기도 부천시 원미구 지봉로 43") String address,
    @Schema(nullable = true) String detailAddress,
    @Schema(description = "지원사업명", example = "침수 예방시설 설치 지원") String programName,
    @Schema(description = "지원 신청 상태", example = "REJECTED") SupportApplicationStatus status,
    @Schema(description = "신청 시설", example = "출입구 물막이판") String requestedFacility,
    @Schema(nullable = true, description = "DB에 저장된 실제 미승인 사유", example = "지원 대상 요건 미충족")
        String rejectionReason,
    @Schema(nullable = true, description = "DB의 승인·미승인 공용 처리 시각") LocalDateTime rejectedAt,
    @Schema(nullable = true, description = "현장점검 결과 다시 보기용 점검 ID") Long inspectionId,
    @Schema(description = "공개된 현장점검 결과 존재 여부", example = "true")
        boolean inspectionResultPublished) {}
