package com.flout.server.domain.inspection.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

public record InspectionResultResponse(
    Long inspectionId,
    Long resultId,
    @Schema(description = "DRAFT 또는 PUBLISHED. 초안 우선 조회") String resultStatus,
    String summary,
    List<Vulnerability> vulnerabilities,
    List<RecommendedAction> recommendedActions,
    @Schema(description = "INDIVIDUAL_HOUSE 또는 PUBLIC_AREA. 미입력 시 null") String followUpType,
    @Schema(description = "운영자 전용 내부 메모") String internalMemo,
    OffsetDateTime savedAt,
    @Schema(description = "공개 시각. 초안은 null") OffsetDateTime publishedAt,
    List<InspectionPhotoUploadResponse.Photo> photos) {
  public record Vulnerability(
      String type, String title, String expertOpinion, String fieldFindings) {}

  public record RecommendedAction(String description) {}

  public InspectionResultResponse withPhotos(List<InspectionPhotoUploadResponse.Photo> photos) {
    return new InspectionResultResponse(
        inspectionId,
        resultId,
        resultStatus,
        summary,
        vulnerabilities,
        recommendedActions,
        followUpType,
        internalMemo,
        savedAt,
        publishedAt,
        photos);
  }
}
