package com.flout.server.domain.construction.dto.response;

import com.flout.server.domain.construction.entity.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public record ConstructionScheduleResponse(
    Long constructionId,
    Long supportApplicationId,
    Long applicationId,
    String receiptNo,
    String applicantName,
    Long managerId,
    String managerName,
    LocalDate constructionDate,
    @Schema(example = "10:00") String constructionTime,
    ConstructionStatus status,
    OffsetDateTime updatedAt) {
  public static ConstructionScheduleResponse from(Construction construction) {
    var support = construction.getSupportApplication();
    return new ConstructionScheduleResponse(
        construction.getId(),
        support.getId(),
        support.getApplication().getId(),
        String.format(Locale.ROOT, "%06d", support.getApplication().getId()),
        support.getApplicantName(),
        construction.getManager().getId(),
        construction.getManager().getName(),
        construction.getScheduledAt().toLocalDate(),
        construction.getScheduledAt().toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")),
        construction.getStatus(),
        construction.getUpdatedAt().atOffset(ZoneOffset.ofHours(9)));
  }
}
