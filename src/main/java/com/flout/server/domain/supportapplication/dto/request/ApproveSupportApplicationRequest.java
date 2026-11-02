package com.flout.server.domain.supportapplication.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import tools.jackson.core.*;
import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JsonDeserialize;

public record ApproveSupportApplicationRequest(
    @NotBlank @Size(max = 500) @JsonDeserialize(using = CreateSupportApplicationRequest.StrictString.class)
        @Schema(description = "지원 범위. 앞뒤 공백 제거 후 1~500자", example = "출입구 물막이판 설치 지원")
        String supportScope,
    @NotNull @AssertTrue @JsonDeserialize(using = StrictBoolean.class)
        @Schema(description = "신청자·주택 정보 확인. 반드시 true", example = "true")
        Boolean applicantHousingReviewed,
    @NotNull @AssertTrue @JsonDeserialize(using = StrictBoolean.class)
        @Schema(description = "전문가 점검 결과 확인. 반드시 true", example = "true")
        Boolean expertInspectionReviewed,
    @NotNull @AssertTrue @JsonDeserialize(using = StrictBoolean.class)
        @Schema(description = "사업 자격·필요서류 확인. 반드시 true", example = "true")
        Boolean eligibilityDocumentsReviewed) {
  public ApproveSupportApplicationRequest {
    supportScope = supportScope == null ? null : supportScope.strip();
  }

  public static final class StrictBoolean extends ValueDeserializer<Boolean> {
    @Override
    public Boolean deserialize(JsonParser parser, DeserializationContext context) {
      if (parser.hasToken(JsonToken.VALUE_TRUE)) return true;
      if (parser.hasToken(JsonToken.VALUE_FALSE)) return false;
      return context.reportInputMismatch(Boolean.class, "검토 항목은 Boolean이어야 합니다.");
    }
  }
}
