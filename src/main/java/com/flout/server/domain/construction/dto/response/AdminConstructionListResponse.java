package com.flout.server.domain.construction.dto.response;

import com.flout.server.domain.construction.entity.ConstructionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Schema(description = "운영자 시공 대상 목록과 페이지 정보")
public record AdminConstructionListResponse(
    @Schema(description = "현재 페이지의 시공 대상") List<Item> constructions,
    @Schema(description = "0부터 시작하는 페이지", example = "0") int page,
    @Schema(description = "페이지 크기 1~100", example = "10") int size,
    @Schema(description = "검색 조건에 맞는 전체 시공 수", example = "25") long totalElements,
    @Schema(description = "전체 페이지 수", example = "3") long totalPages) {
  public record Item(
      @Schema(description = "시공 ID", example = "9") Long constructionId,
      @Schema(description = "현장점검 신청 ID를 최소 6자리로 표시한 접수번호", example = "000003") String receiptNo,
      @Schema(description = "지원 신청 당시 신청자 이름", example = "김주민") String applicantName,
      @Schema(description = "주민이 신청한 설치 시설", example = "출입구 물막이판") String requestedFacility,
      @Schema(nullable = true, description = "확정 시공일. 미배정 시 null", example = "2026-10-21")
          LocalDate scheduledConstructionDate,
      @Schema(nullable = true, description = "확정 시공 시간. 미배정 시 null", example = "10:00:00")
          LocalTime scheduledConstructionTime,
      @Schema(description = "현재 시공 상태", example = "SCHEDULED") ConstructionStatus status) {}
}
