package com.flout.server.domain.inspectionapplication.dto.response;

import com.flout.server.domain.inspectionapplication.entity.InspectionApplication;
import com.flout.server.domain.inspectionapplication.entity.InspectionApplicationStatus;
import java.time.LocalDateTime;
import java.util.Locale;

public record CreateInspectionApplicationResponse(
    Long applicationId,
    Long inspectionId,
    String receiptNo,
    InspectionApplicationStatus status,
    LocalDateTime submittedAt) {
  public static CreateInspectionApplicationResponse from(
      InspectionApplication application, Long inspectionId) {
    return new CreateInspectionApplicationResponse(
        application.getId(),
        inspectionId,
        String.format(Locale.ROOT, "%06d", application.getId()),
        application.getStatus(),
        application.getCreatedAt());
  }
}
