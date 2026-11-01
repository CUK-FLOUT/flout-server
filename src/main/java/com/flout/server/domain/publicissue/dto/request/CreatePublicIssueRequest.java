package com.flout.server.domain.publicissue.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record CreatePublicIssueRequest(
    @NotNull @Positive @JsonDeserialize(using = StrictLong.class)
        @Schema(
            type = "integer",
            implementation = Long.class,
            format = "int64",
            description = "원본 점검 ID (양의 정수)",
            example = "1")
        Long sourceInspectionId,
    @NotBlank @Size(max = 255) @JsonDeserialize(using = StrictString.class)
        @Schema(type = "string", example = "경기도 부천시 원미구 길주로 210")
        String address,
    @NotBlank @Size(max = 50) @JsonDeserialize(using = StrictString.class)
        @Schema(
            type = "string",
            allowableValues = {
              "REPEATED_FLOODING",
              "SEWAGE_BACKFLOW",
              "POOR_ROAD_DRAINAGE",
              "BLOCKED_STORM_DRAIN"
            },
            example = "REPEATED_FLOODING")
        String issueType,
    @NotBlank @Size(max = 4000) @JsonDeserialize(using = StrictString.class)
        @Schema(type = "string", example = "빗물받이와 주변 도로 배수 상태 확인이 필요합니다.")
        String description,
    @NotBlank @Size(max = 4000) @JsonDeserialize(using = StrictString.class)
        @Schema(type = "string", example = "배수구 덮개 주변에 이물질이 쌓여 있습니다.")
        String fieldFindings) {
  public CreatePublicIssueRequest {
    address = strip(address);
    issueType = strip(issueType);
    description = strip(description);
    fieldFindings = strip(fieldFindings);
  }

  private static String strip(String value) {
    return value == null ? null : value.strip();
  }

  public static final class StrictLong extends ValueDeserializer<Long> {
    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) {
      if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT))
        return context.reportInputMismatch(Long.class, "원본 점검 ID는 정수여야 합니다.");
      return parser.getLongValue();
    }
  }

  public static final class StrictString extends ValueDeserializer<String> {
    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) {
      if (!parser.hasToken(JsonToken.VALUE_STRING))
        return context.reportInputMismatch(String.class, "문자열 필드의 형식이 잘못되었습니다.");
      return parser.getString();
    }
  }
}
