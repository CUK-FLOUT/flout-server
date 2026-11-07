package com.flout.server.domain.construction.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import tools.jackson.core.*;
import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JsonDeserialize;

public record SaveConstructionScheduleRequest(
    @NotNull(message = "확정 시공일을 입력해주세요.") @JsonDeserialize(using = StrictDate.class)
        @Schema(type = "string", format = "date", example = "2030-01-03")
        LocalDate constructionDate,
    @NotNull(message = "확정 시공 시간을 입력해주세요.") @JsonDeserialize(using = StrictTime.class)
        @Schema(type = "string", example = "10:00", description = "한국 시간 기준 HH:mm, 00:00~23:59")
        LocalTime constructionTime) {
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
      return context.reportInputMismatch(LocalDate.class, "확정 시공일은 유효한 yyyy-MM-dd 형식이어야 합니다.");
    }
  }

  public static final class StrictTime extends ValueDeserializer<LocalTime> {
    @Override
    public LocalTime deserialize(JsonParser parser, DeserializationContext context) {
      if (parser.hasToken(JsonToken.VALUE_STRING)) {
        String value = parser.getString();
        if (value.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]")) return LocalTime.parse(value);
      }
      return context.reportInputMismatch(LocalTime.class, "확정 시공 시간은 HH:mm 형식이어야 합니다.");
    }
  }
}
