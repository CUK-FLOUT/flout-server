package com.flout.server.domain.publicissue.dto.response;

import com.flout.server.domain.publicissue.entity.PublicIssueStatus;
import com.flout.server.domain.publicissue.entity.PublicIssueType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record AdminPublicIssueListResponse(
    @Schema(description = "현재 페이지의 지역·공공 문제 목록") List<Item> publicIssues,
    @Schema(description = "0부터 시작하는 페이지", example = "0") int page,
    @Schema(description = "페이지 크기", example = "10") int size,
    @Schema(description = "검색·필터 조건에 맞는 전체 문제 수", example = "5") long totalElements,
    @Schema(description = "전체 페이지 수", example = "1") long totalPages) {
  public record Item(
      @Schema(description = "지역·공공 문제 ID", example = "1") Long publicIssueId,
      @Schema(description = "문제 유형", example = "REPEATED_FLOODING") PublicIssueType issueType,
      @Schema(description = "문제 위치에서 직선거리 1km 이하의 관련 신청 수", example = "3")
          long relatedApplicationCount,
      @Schema(description = "관련 신청 중 과거 침수 경험이 있는 신청 수", example = "2") long floodHistoryCount,
      @Schema(description = "문제 처리 상태", example = "UNDER_REVIEW") PublicIssueStatus status) {}
}
