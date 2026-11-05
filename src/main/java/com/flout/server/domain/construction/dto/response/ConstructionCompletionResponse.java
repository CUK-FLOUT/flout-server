package com.flout.server.domain.construction.dto.response;

import com.flout.server.domain.construction.entity.*;
import java.time.*;
import java.util.Locale;

public record ConstructionCompletionResponse(
    Long constructionId,
    Long supportApplicationId,
    Long applicationId,
    String receiptNo,
    String applicantName,
    ConstructionStatus status,
    String finalInstallationResult,
    OffsetDateTime completedAt) {
  public static ConstructionCompletionResponse from(Construction construction) {
    var support = construction.getSupportApplication();
    return new ConstructionCompletionResponse(
        construction.getId(),
        support.getId(),
        support.getApplication().getId(),
        String.format(Locale.ROOT, "%06d", support.getApplication().getId()),
        support.getApplicantName(),
        construction.getStatus(),
        construction.getFinalInstallationResult(),
        construction.getCompletedAt().atOffset(ZoneOffset.ofHours(9)));
  }
}
