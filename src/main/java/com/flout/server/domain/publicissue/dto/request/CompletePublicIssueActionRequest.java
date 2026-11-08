package com.flout.server.domain.publicissue.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

public record CompletePublicIssueActionRequest(
    @NotBlank @Size(max = 4000) @JsonDeserialize(using = CreatePublicIssueRequest.StrictString.class)
        @Schema(
            type = "string",
            description = "실제 점검·조치 결과. 앞뒤 공백 제거 후 1~4000자",
            example = "빗물받이 퇴적물 제거 및 주변 배수로 정비")
        String actionDescription) {
  public CompletePublicIssueActionRequest {
    actionDescription = actionDescription == null ? null : actionDescription.strip();
  }
}
