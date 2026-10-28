package com.flout.server.domain.inspection.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.flout.server.domain.inspection.entity.Inspection;
import com.flout.server.domain.inspection.entity.InspectionStatus;
import java.time.*;
import java.util.Locale;

public record InspectionAssignmentResponse(
    Long inspectionId,
    String receiptNo,
    String applicantName,
    Long inspectorId,
    String inspectorName,
    LocalDate visitDate,
    @JsonFormat(pattern = "HH:mm") LocalTime visitTime,
    String preVisitNotice,
    InspectionStatus status,
    LocalDateTime updatedAt) {
  public static InspectionAssignmentResponse from(Inspection inspection) {
    return new InspectionAssignmentResponse(
        inspection.getId(),
        String.format(Locale.ROOT, "%06d", inspection.getApplication().getId()),
        inspection.getApplication().getApplicant().getName(),
        inspection.getInspector().getId(),
        inspection.getInspector().getName(),
        inspection.getScheduledAt().toLocalDate(),
        inspection.getScheduledAt().toLocalTime(),
        inspection.getPreVisitNotice(),
        inspection.getStatus(),
        inspection.getUpdatedAt());
  }
}
