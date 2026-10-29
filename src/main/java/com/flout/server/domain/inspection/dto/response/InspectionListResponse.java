package com.flout.server.domain.inspection.dto.response;

import com.flout.server.domain.inspection.entity.InspectionStatus;
import com.flout.server.domain.inspectionapplication.entity.PreferredTimeSlot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

public record InspectionListResponse(
    @Schema(description = "현재 페이지의 점검 목록") List<Item> inspections,
    @Schema(description = "0부터 시작하는 페이지", example = "0") int page,
    @Schema(description = "페이지 크기", example = "10") int size,
    @Schema(description = "검색·필터 조건에 맞는 전체 점검 수", example = "1") long totalElements,
    @Schema(description = "전체 페이지 수", example = "1") long totalPages) {
  public record Item(
      @Schema(description = "현장점검 ID", example = "2") Long inspectionId,
      @Schema(description = "신청 ID", example = "5") Long applicationId,
      @Schema(description = "신청 ID를 최소 6자리로 표시한 접수번호", example = "000005") String receiptNo,
      @Schema(
              description = "구 단위 주소만 반환. 원미구·소사구·오정구. 구 식별 불가 시 null",
              example = "원미구",
              nullable = true)
          String address,
      @Schema(description = "우선점검 지정 여부", example = "true") boolean designated,
      @Schema(
              description = "주민 방문 희망일. 확정 방문일과 구분",
              format = "date",
              example = "2026-11-03",
              nullable = true)
          LocalDate preferredVisitDate,
      @Schema(description = "주민 방문 희망 시간대", example = "MORNING", nullable = true)
          PreferredTimeSlot preferredTimeSlot,
      @Schema(description = "배정된 담당자 사용자 ID. 미배정 시 null", example = "3", nullable = true)
          Long inspectorUserId,
      @Schema(description = "배정된 담당자 이름. 미배정 시 null", example = "김담당", nullable = true)
          String inspectorName,
      @Schema(
              description = "RECEIVED=배정 대기, SCHEDULED=예정, IN_PROGRESS=진행, COMPLETED=완료. 배정 여부와 별개",
              example = "RECEIVED")
          InspectionStatus status) {}
}
