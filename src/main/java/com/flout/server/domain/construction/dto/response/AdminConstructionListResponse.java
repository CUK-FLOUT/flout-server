package com.flout.server.domain.construction.dto.response;

import com.flout.server.domain.construction.entity.ConstructionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record AdminConstructionListResponse(
    List<Item> constructions,
    @Schema(description = "0부터 시작하는 페이지", example = "0") int page,
    @Schema(description = "페이지 크기 1~100", example = "10") int size,
    long totalElements,
    long totalPages) {
  public record Item(
      Long constructionId,
      @Schema(description = "현장점검 신청 ID를 최소 6자리로 표시한 접수번호", example = "000003") String receiptNo,
      String applicantName,
      String requestedFacility,
      @Schema(nullable = true, description = "확정 시공일. 미배정 시 null", example = "2026-10-21")
          LocalDate scheduledConstructionDate,
      @Schema(nullable = true, description = "확정 시공 시간. 미배정 시 null", example = "10:00:00")
          LocalTime scheduledConstructionTime,
      ConstructionStatus status) {}
}
