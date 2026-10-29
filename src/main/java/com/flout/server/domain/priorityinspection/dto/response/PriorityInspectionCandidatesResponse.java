package com.flout.server.domain.priorityinspection.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record PriorityInspectionCandidatesResponse(
    @Schema(description = "현재 페이지의 후보 목록") List<Item> applications,
    @Schema(description = "0부터 시작하는 페이지", example = "0") int page,
    @Schema(description = "페이지 크기", example = "10") int size,
    @Schema(description = "검색 결과 전체 신청 수", example = "1") long totalElements,
    @Schema(description = "전체 페이지 수", example = "1") long totalPages) {
  public record Item(
      @Schema(description = "신청 ID", example = "1") Long applicationId,
      @Schema(description = "신청 ID를 최소 6자리로 표시한 접수번호", example = "000001") String receiptNo,
      @Schema(description = "신청 주소", example = "경기도 부천시 원미구 길주로 210") String address,
      @Schema(
              description = "위험도",
              allowableValues = {"HIGH", "CAUTION", "LOW"},
              example = "HIGH")
          String riskLevel,
      @Schema(
              description = "침수 경험",
              allowableValues = {"YES", "NO", "UNKNOWN"},
              example = "YES")
          String floodExperience,
      @Schema(description = "주민 요청 사유", example = "최근 배수구 역류", nullable = true)
          String requestReason,
      @Schema(
              description = "동일 관할 내 본인 제외 직선거리 1km 이하의 신청 건수. 검색·페이지·지정 여부·상태·기간으로 제한하지 않음",
              example = "3")
          long nearbyApplicationCount,
      @Schema(description = "우선점검 지정 여부", example = "false") boolean designated) {}
}
