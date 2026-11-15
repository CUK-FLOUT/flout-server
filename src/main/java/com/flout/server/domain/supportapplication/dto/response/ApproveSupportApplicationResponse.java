package com.flout.server.domain.supportapplication.dto.response;

import com.flout.server.domain.supportapplication.entity.*;
import com.flout.server.global.time.SeoulDateTimes;
import java.time.OffsetDateTime;

public record ApproveSupportApplicationResponse(
    Long supportApplicationId,
    SupportApplicationStatus status,
    String supportScope,
    Long approvedByUserId,
    OffsetDateTime approvedAt,
    Long constructionId) {
  public static ApproveSupportApplicationResponse from(
      SupportApplication application, Long constructionId) {
    return new ApproveSupportApplicationResponse(
        application.getId(),
        application.getStatus(),
        application.getSupportScope(),
        application.getApprovedBy().getId(),
        SeoulDateTimes.toOffset(application.getApprovedAt()),
        constructionId);
  }
}
