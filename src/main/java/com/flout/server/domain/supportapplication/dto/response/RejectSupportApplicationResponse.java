package com.flout.server.domain.supportapplication.dto.response;

import com.flout.server.domain.supportapplication.entity.*;
import java.time.*;

public record RejectSupportApplicationResponse(
    Long supportApplicationId,
    SupportApplicationStatus status,
    String reason,
    Long processedByUserId,
    OffsetDateTime processedAt) {
  public static RejectSupportApplicationResponse from(SupportApplication application) {
    return new RejectSupportApplicationResponse(
        application.getId(),
        application.getStatus(),
        application.getRejectionReason(),
        application.getApprovedBy().getId(),
        application
            .getApprovedAt()
            .atZone(ZoneId.systemDefault())
            .withZoneSameInstant(ZoneId.of("Asia/Seoul"))
            .toOffsetDateTime());
  }
}
