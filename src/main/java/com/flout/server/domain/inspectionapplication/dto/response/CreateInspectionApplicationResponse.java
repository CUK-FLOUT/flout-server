package com.flout.server.domain.inspectionapplication.dto.response;

import com.flout.server.domain.inspectionapplication.entity.InspectionApplication;
import com.flout.server.domain.inspectionapplication.entity.InspectionApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Locale;

public record CreateInspectionApplicationResponse(
    Long applicationId,
    @Schema(
            description = "임시 신청에는 점검이 생성되지 않아 null",
            nullable = true,
            types = {"integer", "null"})
        Long inspectionId,
    String receiptNo,
    @Schema(description = "임시 저장 상태", example = "DRAFT") InspectionApplicationStatus status,
    @Schema(
            description = "임시 신청의 접수 시각은 null",
            nullable = true,
            types = {"string", "null"})
        LocalDateTime submittedAt) {
  public static CreateInspectionApplicationResponse from(
      InspectionApplication application, Long inspectionId) {
    return new CreateInspectionApplicationResponse(
        application.getId(),
        inspectionId,
        String.format(Locale.ROOT, "%06d", application.getId()),
        application.getStatus(),
        application.getStatus() == InspectionApplicationStatus.DRAFT
            ? null
            : application.getCreatedAt());
  }
}
