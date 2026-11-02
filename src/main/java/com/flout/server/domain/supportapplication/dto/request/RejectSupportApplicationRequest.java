package com.flout.server.domain.supportapplication.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import tools.jackson.databind.annotation.JsonDeserialize;

public record RejectSupportApplicationRequest(
    @NotBlank @Size(max = 500) @JsonDeserialize(using = CreateSupportApplicationRequest.StrictString.class)
        @Schema(description = "미승인 사유. 앞뒤 공백 제거 후 1~500자", example = "자격 미달")
        String reason) {
  public RejectSupportApplicationRequest {
    reason = reason == null ? null : reason.strip();
  }
}
