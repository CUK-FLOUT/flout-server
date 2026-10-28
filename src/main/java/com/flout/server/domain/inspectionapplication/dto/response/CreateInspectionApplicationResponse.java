package com.flout.server.domain.inspectionapplication.dto.response;

import com.flout.server.domain.inspectionapplication.entity.InspectionApplication;
import com.flout.server.domain.inspectionapplication.entity.InspectionApplicationStatus;
import java.time.LocalDateTime;

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
        application.getReceiptNo(),
        application.getStatus(),
        application.getCreatedAt());
  }
}
