package com.flout.server.domain.publicissue.dto.request;

import com.flout.server.domain.publicissue.entity.FacilityReviewStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

public record SaveFacilityReviewRequest(
    @NotBlank @Size(max = 100) @JsonDeserialize(using = CreatePublicIssueRequest.StrictString.class)
        @Schema(type = "string", description = "검토 대상", example = "빗물받이")
        String reviewTarget,
    @NotNull @Schema(
            allowableValues = {"PENDING", "UNDER_REVIEW", "INSPECTION_REQUIRED"},
            example = "UNDER_REVIEW")
        FacilityReviewStatus reviewStatus,
    @NotBlank @Size(max = 4000) @JsonDeserialize(using = CreatePublicIssueRequest.StrictString.class)
        @Schema(type = "string", example = "빗물받이 막힘 여부와 주변 도로 배수 상태 현장 확인")
        String inspectionNotes,
    @Size(max = 4000) @JsonDeserialize(using = CreatePublicIssueRequest.StrictString.class)
        @Schema(
            type = "string",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED,
            example = "인근 신청 3건과 침수 이력 2건 확인")
        String reviewReason) {
  public SaveFacilityReviewRequest {
    reviewTarget = strip(reviewTarget);
    inspectionNotes = strip(inspectionNotes);
    reviewReason = strip(reviewReason);
    if (reviewReason != null && reviewReason.isEmpty()) reviewReason = null;
  }

  private static String strip(String value) {
    return value == null ? null : value.strip();
  }
}
