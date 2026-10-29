package com.flout.server.domain.priorityinspection.dto.response;

import com.flout.server.domain.inspectionapplication.entity.FloodExperience;
import com.flout.server.domain.inspectionapplication.entity.InspectionApplication;
import com.flout.server.domain.riskassessment.entity.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Locale;

public record PriorityInspectionDetailResponse(
    @Schema(description = "현장점검 신청 ID", example = "5") Long applicationId,
    @Schema(description = "신청 ID를 최소 6자리로 표시한 접수번호", example = "000005") String receiptNo,
    @Schema(description = "신청자 계정의 이름", example = "김주민") String applicantName,
    @Schema(description = "신청 주소", example = "경기도 부천시 원미구 길주로 53") String address,
    @Schema(description = "분석 결과에서 복사해 신청에 저장한 상세주소", example = "101동 302호", nullable = true)
        String detailAddress,
    @Schema(
            description = "AI 취약도 기준 점검 필요도. 운영자의 우선점검 지정 여부와 별개",
            allowableValues = {"우선점검 권장", "점검 검토", "일반 점검"},
            example = "우선점검 권장")
        String inspectionNeed,
    @Schema(description = "해당 신청에 연결된 AI 취약도 분석 결과", example = "HIGH") RiskLevel riskLevel,
    @Schema(description = "신청 시 입력한 침수 경험", example = "YES") FloodExperience floodExperience,
    @Schema(description = "주민 점검 요청 사유", example = "최근 배수구 역류", nullable = true)
        String requestReason,
    @Schema(description = "같은 관할 내 본인을 제외한 직선거리 1km 이하 신청 건수", example = "3")
        long nearbyApplicationCount,
    @Schema(description = "기존 내부 메모 API로 저장한 메모", example = "출입구 누수 확인 필요", nullable = true)
        String internalMemo) {

  // 저장된 신청정보와 인근 건수를 응답으로 변환하고 연결된 취약도로 점검 필요도 결정
  public static PriorityInspectionDetailResponse from(
      InspectionApplication application, long nearbyCount) {
    var risk = application.getAssessment().getRiskLevel();
    var need =
        switch (risk) {
          case HIGH -> "우선점검 권장";
          case CAUTION -> "점검 검토";
          case LOW -> "일반 점검";
        };
    return new PriorityInspectionDetailResponse(
        application.getId(),
        String.format(Locale.ROOT, "%06d", application.getId()),
        application.getApplicant().getName(),
        application.getAddress(),
        application.getDetailAddress(),
        need,
        risk,
        application.getFloodExperience(),
        application.getRequestReason(),
        nearbyCount,
        application.getInternalMemo());
  }
}
