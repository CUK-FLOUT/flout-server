package com.flout.server.domain.supportapplication.dto.response;

import com.flout.server.domain.supportapplication.entity.*;
import java.time.*;

public record CreateSupportApplicationResponse(
    Long supportApplicationId,
    Long applicationId,
    Long programId,
    SupportApplicationStatus status,
    OffsetDateTime submittedAt) {
  public static CreateSupportApplicationResponse from(SupportApplication application) {
    return new CreateSupportApplicationResponse(
        application.getId(),
        application.getApplication().getId(),
        application.getProgram().getId(),
        application.getStatus(),
        application
            .getSubmittedAt()
            .atZone(ZoneId.systemDefault())
            .withZoneSameInstant(ZoneId.of("Asia/Seoul"))
            .toOffsetDateTime());
  }
}
