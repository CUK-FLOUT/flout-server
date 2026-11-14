package com.flout.server.domain.inspectionapplication.dto.response;

import com.flout.server.domain.inspectionapplication.entity.InspectionApplication;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Locale;

public record ResidentApplicationSummaryResponse(
    Long applicationId,
    @Schema(description = "표시용 접수번호. URL에는 숫자 applicationId를 사용합니다.", example = "000001")
        String receiptNumber,
    @Schema(description = "진행 단계에 따른 신청 제목", example = "시공 · 출입구 물막이판") String title,
    @Schema(description = "신청 주소", example = "경기도 부천시 원미구 지봉로 43") String address,
    ResidentApplicationStage stage,
    boolean closed,
    OffsetDateTime createdAt,
    @Schema(nullable = true, description = "실제 접수 시각. 과거 접수 건에서 확인할 수 없으면 null")
        OffsetDateTime submittedAt,
    OffsetDateTime updatedAt,
    @Schema(nullable = true) Long inspectionId,
    @Schema(nullable = true) Long supportApplicationId,
    @Schema(nullable = true) Long constructionId,
    boolean resultPublished) {
  public static ResidentApplicationSummaryResponse from(
      InspectionApplication application, ResidentApplicationProgressResponse progress) {
    var zone = ZoneId.of("Asia/Seoul");
    return new ResidentApplicationSummaryResponse(
        application.getId(),
        String.format(Locale.ROOT, "%06d", application.getId()),
        progress.title(),
        application.getAddress(),
        progress.stage(),
        progress.closed(),
        application.getCreatedAt().atZone(zone).toOffsetDateTime(),
        application.getSubmittedAt() == null
            ? null
            : application.getSubmittedAt().atZone(zone).toOffsetDateTime(),
        progress.updatedAt(),
        progress.inspectionId(),
        progress.supportApplicationId(),
        progress.constructionId(),
        progress.resultPublished());
  }
}
