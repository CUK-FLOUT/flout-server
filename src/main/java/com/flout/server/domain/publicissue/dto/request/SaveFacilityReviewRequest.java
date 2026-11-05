package com.flout.server.domain.publicissue.dto.request;

import com.flout.server.domain.publicissue.entity.FacilityReviewStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

public record SaveFacilityReviewRequest(
    @NotNull @Positive @JsonDeserialize(using = CreatePublicIssueRequest.StrictLong.class)
        @Schema(type = "integer", implementation = Long.class, format = "int64", example = "17")
        Long facilityId,
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
    inspectionNotes = strip(inspectionNotes);
    reviewReason = strip(reviewReason);
    if (reviewReason != null && reviewReason.isEmpty()) reviewReason = null;
  }

  private static String strip(String value) {
    return value == null ? null : value.strip();
  }
}
