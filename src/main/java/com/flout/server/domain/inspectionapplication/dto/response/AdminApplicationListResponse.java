package com.flout.server.domain.inspectionapplication.dto.response;

import com.flout.server.domain.inspectionapplication.entity.ApplicationProcessingStatus;
import com.flout.server.domain.riskassessment.entity.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record AdminApplicationListResponse(
    List<Item> applications, int page, int size, long totalElements, long totalPages) {
  public record Item(
      @Schema(example = "29") Long applicationId,
      @Schema(example = "000029") String receiptNo,
      @Schema(example = "김주민") String applicantName,
      @Schema(example = "경기도 부천시 원미구 지봉로 43") String address,
      @Schema(nullable = true, example = "101호") String detailAddress,
      @Schema(example = "HIGH") RiskLevel riskLevel,
      @Schema(example = "RECEIVED") ApplicationProcessingStatus processingStatus) {}
}
