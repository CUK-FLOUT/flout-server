package com.flout.server.domain.priorityinspection.dto.response;

import com.flout.server.domain.priorityinspection.entity.PriorityInspectionDesignation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Locale;

public record PriorityInspectionOrderListResponse(
    @Schema(description = "완료 건을 제외한 관할 지정 대상 전체. 저장된 선정 순서 오름차순") List<Item> applications) {
  public record Item(
      @Schema(description = "현장점검 신청 ID", example = "3") Long applicationId,
      @Schema(description = "접수번호", example = "000003") String receiptNo,
      @Schema(description = "저장된 선정 순서", example = "1") int selectionOrder,
      @Schema(description = "신청 주소") String address,
      @Schema(description = "취약도: HIGH, CAUTION, LOW") String riskLevel,
      @Schema(description = "침수 경험: YES, NO, UNKNOWN") String floodExperience,
      @Schema(description = "주민 요청 사유", nullable = true) String requestReason,
      @Schema(description = "동일 관할 내 본인 제외 직선거리 1km 이하의 신청 건수") long nearbyApplicationCount,
      @Schema(description = "우선점검 지정 여부. 항상 true") boolean designated) {
    public static Item from(PriorityInspectionDesignation designation, long nearbyCount) {
      var application = designation.getApplication();
      return new Item(
          designation.getApplicationId(),
          String.format(Locale.ROOT, "%06d", designation.getApplicationId()),
          designation.getSelectionOrder(),
          application.getAddress(),
          application.getAssessment().getRiskLevel().name(),
          application.getFloodExperience().name(),
          application.getRequestReason(),
          nearbyCount,
          true);
    }
  }
}
