package com.flout.server.domain.supportapplication.dto.request;

import com.flout.server.domain.inspectionapplication.entity.PreferredTimeSlot;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import tools.jackson.core.*;
import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JsonDeserialize;

public record CreateSupportApplicationRequest(
    @NotNull @Positive @JsonDeserialize(using = StrictLong.class)
        @Schema(
            type = "integer",
            format = "int64",
            implementation = Long.class,
            description = "본인 소유 현장점검 신청 ID",
            example = "101")
        Long applicationId,
    @NotNull @Positive @JsonDeserialize(using = StrictLong.class)
        @Schema(
            type = "integer",
            format = "int64",
            implementation = Long.class,
            description = "지원사업 ID",
            example = "1")
        Long programId,
    @NotBlank @Size(max = 255) @JsonDeserialize(using = StrictString.class)
        @Schema(description = "신청 시설 (최대 255자)", example = "출입구 물막이판")
        String requestedFacility,
    @NotNull @FutureOrPresent @JsonDeserialize(using = StrictDate.class)
        @Schema(
            type = "string",
            format = "date",
            description = "지원시설 설치 희망 방문일 (오늘 이후, yyyy-MM-dd). 현장점검 일정과 별개",
            example = "2030-12-01")
        LocalDate preferredVisitDate,
    @NotNull @JsonDeserialize(using = StrictVisitTime.class)
        @Schema(
            description =
                "지원시설 설치 희망 시간: MORNING=오전 9~12시, AFTERNOON=오후 13~18시, ANY=상관없음. 현장점검·확정 시공 일정과 별개",
            allowableValues = {"MORNING", "AFTERNOON", "ANY"},
            example = "MORNING")
        PreferredTimeSlot preferredVisitTime) {
  public CreateSupportApplicationRequest {
    requestedFacility = strip(requestedFacility);
  }

  private static String strip(String value) {
    return value == null ? null : value.strip();
  }

  public static final class StrictLong extends ValueDeserializer<Long> {
    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) {
      if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT))
        return context.reportInputMismatch(Long.class, "ID는 정수여야 합니다.");
      return parser.getLongValue();
    }
  }

  public static final class StrictString extends ValueDeserializer<String> {
    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) {
      if (!parser.hasToken(JsonToken.VALUE_STRING))
        return context.reportInputMismatch(String.class, "문자열 형식이 필요합니다.");
      return parser.getString();
    }
  }

  public static final class StrictVisitTime extends ValueDeserializer<PreferredTimeSlot> {
    @Override
    public PreferredTimeSlot deserialize(JsonParser parser, DeserializationContext context) {
      if (parser.hasToken(JsonToken.VALUE_STRING)) {
        try {
          return PreferredTimeSlot.valueOf(parser.getString().strip());
        } catch (IllegalArgumentException ignored) {
        }
      }
      return context.reportInputMismatch(
          PreferredTimeSlot.class, "방문 가능 시간은 MORNING, AFTERNOON, ANY 중 하나여야 합니다.");
    }
  }

  public static final class StrictDate extends ValueDeserializer<LocalDate> {
    @Override
    public LocalDate deserialize(JsonParser parser, DeserializationContext context) {
      if (parser.hasToken(JsonToken.VALUE_STRING)) {
        String value = parser.getString();
        if (value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
          try {
            return LocalDate.parse(value);
          } catch (DateTimeParseException ignored) {
          }
        }
      }
      return context.reportInputMismatch(LocalDate.class, "희망 방문일은 유효한 yyyy-MM-dd 형식이어야 합니다.");
    }
  }
}
