package com.flout.server.domain.inspectionapplication.dto.response;

import com.flout.server.domain.inspectionapplication.entity.ApplicationProcessingStatus;
import com.flout.server.domain.riskassessment.entity.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

public record AdminApplicationMapResponse(List<Marker> applications) {
  public record Marker(
      @Schema(description = "신청 상세 조회에 사용할 ID", example = "29") long applicationId,
      @Schema(example = "37.4874850") BigDecimal latitude,
      @Schema(example = "126.7985672") BigDecimal longitude,
      @Schema(example = "HIGH") RiskLevel riskLevel,
      @Schema(example = "RECEIVED") ApplicationProcessingStatus processingStatus) {}
}
