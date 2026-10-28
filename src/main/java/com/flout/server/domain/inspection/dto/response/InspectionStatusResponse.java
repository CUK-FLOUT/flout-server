package com.flout.server.domain.inspection.dto.response;

import com.flout.server.domain.inspection.entity.Inspection;
import com.flout.server.domain.inspection.entity.InspectionStatus;
import java.time.LocalDateTime;

public record InspectionStatusResponse(
    Long inspectionId, InspectionStatus status, LocalDateTime updatedAt) {
  public static InspectionStatusResponse from(Inspection inspection) {
    return new InspectionStatusResponse(
        inspection.getId(), inspection.getStatus(), inspection.getUpdatedAt());
  }
}
