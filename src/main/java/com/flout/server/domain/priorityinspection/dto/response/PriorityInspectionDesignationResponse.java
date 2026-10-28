package com.flout.server.domain.priorityinspection.dto.response;

import com.flout.server.domain.priorityinspection.entity.PriorityInspectionDesignation;
import java.time.LocalDateTime;
import java.util.Locale;

public record PriorityInspectionDesignationResponse(
    Long applicationId, String receiptNo, boolean designated, LocalDateTime designatedAt) {
  public static PriorityInspectionDesignationResponse cancelled(Long applicationId) {
    return new PriorityInspectionDesignationResponse(
        applicationId, String.format(Locale.ROOT, "%06d", applicationId), false, null);
  }

  public static PriorityInspectionDesignationResponse from(
      PriorityInspectionDesignation designation) {
    return new PriorityInspectionDesignationResponse(
        designation.getApplicationId(),
        String.format(Locale.ROOT, "%06d", designation.getApplicationId()),
        true,
        designation.getDesignatedAt());
  }
}
