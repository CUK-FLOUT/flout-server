package com.flout.server.domain.supportapplication.dto.response;

import com.flout.server.domain.supportapplication.entity.SupportApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record SupportApplicationListResponse(
    List<Item> supportApplications,
    @Schema(description = "0부터 시작하는 페이지", example = "0") int page,
    @Schema(description = "페이지 크기 1~100", example = "10") int size,
    long totalElements,
    long totalPages) {
  public record Item(
      Long supportApplicationId,
      @Schema(description = "기존 지원 신청 응답과 동일한 현장점검 신청 ID 최소 6자리 표기", example = "000002")
          String receiptNo,
      String applicantName,
      String requestedFacility,
      @Schema(
              description = "DB CONFIRMED는 VERIFIED로 반환",
              allowableValues = {"PENDING", "VERIFIED", "NOT_REQUIRED"})
          String landlordConsentStatus,
      @Schema(allowableValues = {"SUBMITTED", "APPROVED", "REJECTED"})
          SupportApplicationStatus status) {}
}
