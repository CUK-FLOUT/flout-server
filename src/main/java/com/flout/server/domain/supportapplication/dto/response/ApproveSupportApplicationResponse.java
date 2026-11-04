package com.flout.server.domain.supportapplication.dto.response;

import com.flout.server.domain.supportapplication.entity.*;
import java.time.*;

public record ApproveSupportApplicationResponse(
    Long supportApplicationId,
    SupportApplicationStatus status,
    String supportScope,
    Long approvedByUserId,
    OffsetDateTime approvedAt) {
  public static ApproveSupportApplicationResponse from(SupportApplication application) {
    return new ApproveSupportApplicationResponse(
        application.getId(),
        application.getStatus(),
        application.getSupportScope(),
        application.getApprovedBy().getId(),
        application
            .getApprovedAt()
            .atZone(ZoneId.systemDefault())
            .withZoneSameInstant(ZoneId.of("Asia/Seoul"))
            .toOffsetDateTime());
  }
}
