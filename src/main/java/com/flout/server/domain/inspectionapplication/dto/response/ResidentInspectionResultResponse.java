package com.flout.server.domain.inspectionapplication.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "주민에게 공개된 현장점검 결과")
public record ResidentInspectionResultResponse(
    @Schema(description = "현장점검 신청 ID", example = "101") Long applicationId,
    @Schema(description = "점검 결과 요약 및 전체 전문가 의견", example = "출입구와 배수구의 침수 예방이 필요합니다.")
        String summary,
    @Schema(description = "결과 공개 시각", format = "date-time", example = "2026-11-04T10:00:00+09:00")
        OffsetDateTime publishedAt,
    @Schema(description = "등록 순서의 취약지점 목록") List<Vulnerability> vulnerabilities,
    @Schema(description = "현장점검 결과 사진 목록") List<Photo> photos) {
  @Schema(description = "취약지점")
  public record Vulnerability(
      @Schema(
              description = "취약지점 유형",
              allowableValues = {"ENTRANCE", "WINDOW_OPENING", "DRAIN", "OTHER_STRUCTURE"},
              example = "ENTRANCE")
          String type,
      @Schema(description = "취약지점 제목", example = "낮은 출입구 높이") String title,
      @Schema(description = "취약지점별 전문가 의견", example = "빗물 유입 가능성이 있습니다.") String expertOpinion,
      @Schema(
              description = "현장 확인 내용. 입력되지 않으면 null",
              nullable = true,
              example = "출입구 주변 바닥보다 낮습니다.")
          String fieldFindings) {}

  @Schema(description = "현장점검 결과 사진")
  public record Photo(
      @Schema(description = "사진 ID", example = "10") Long photoId,
      @Schema(
              description = "유효기간이 있는 임시 열람 URL",
              format = "uri",
              example = "https://example.com/photo?signature=example")
          String url) {}
}
