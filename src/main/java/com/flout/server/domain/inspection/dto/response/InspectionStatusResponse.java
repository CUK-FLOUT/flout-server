package com.flout.server.domain.inspection.dto.response;

import com.flout.server.domain.inspection.entity.Inspection;
import com.flout.server.domain.inspection.entity.InspectionStatus;
import java.time.LocalDateTime;
import java.util.Locale;

public record InspectionStatusResponse(
    Long inspectionId, String receiptNo, InspectionStatus status, LocalDateTime updatedAt) {
  public static InspectionStatusResponse from(Inspection inspection) {
    return new InspectionStatusResponse(
        inspection.getId(),
        String.format(Locale.ROOT, "%06d", inspection.getApplication().getId()),
        inspection.getStatus(),
        inspection.getUpdatedAt());
  }
}
