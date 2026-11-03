package com.flout.server.domain.supportapplication.dto.request;

import com.flout.server.domain.supportapplication.entity.LandlordConsentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import tools.jackson.core.*;
import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JsonDeserialize;

public record ConfirmLandlordConsentRequest(
    @NotNull @JsonDeserialize(using = ConfirmedStatus.class)
        @Schema(
            description = "변경할 동의 확인 상태. CONFIRMED만 허용",
            allowableValues = {"CONFIRMED"},
            example = "CONFIRMED",
            type = "string")
        LandlordConsentStatus status) {
  public static final class ConfirmedStatus extends ValueDeserializer<LandlordConsentStatus> {
    @Override
    public LandlordConsentStatus deserialize(JsonParser parser, DeserializationContext context) {
      if (parser.hasToken(JsonToken.VALUE_STRING) && "CONFIRMED".equals(parser.getString()))
        return LandlordConsentStatus.CONFIRMED;
      return context.reportInputMismatch(LandlordConsentStatus.class, "CONFIRMED만 요청할 수 있습니다.");
    }
  }
}
