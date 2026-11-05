package com.flout.server.domain.supportapplication.dto.response;

import com.flout.server.domain.supportapplication.entity.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.*;
import java.util.Locale;

public record CreateSupportApplicationResponse(
    Long supportApplicationId,
    Long applicationId,
    Long programId,
    SupportApplicationStatus status,
    OffsetDateTime submittedAt,
    @Schema(description = "기존 주민 신청 상세와 동일한 현장점검 신청 ID 최소 6자리 표기", example = "000101")
        String receiptNumber,
    @Schema(description = "지원 신청에 저장된 신청자 이름 스냅샷", example = "홍길동") String applicantName,
    @Schema(description = "연결된 실제 지원사업명", example = "침수 예방시설 설치 지원") String programName,
    @Schema(description = "지원 신청 생성 시각", example = "2026-11-03T14:00:00+09:00")
        OffsetDateTime createdAt) {
  public static CreateSupportApplicationResponse from(SupportApplication application) {
    var submittedAt = toOffset(application.getSubmittedAt());
    return new CreateSupportApplicationResponse(
        application.getId(),
        application.getApplication().getId(),
        application.getProgram().getId(),
        application.getStatus(),
        submittedAt,
        String.format(Locale.ROOT, "%06d", application.getApplication().getId()),
        application.getApplicantName(),
        application.getProgram().getName(),
        toOffset(application.getCreatedAt()));
  }

  private static OffsetDateTime toOffset(LocalDateTime value) {
    return value == null
        ? null
        : value
            .atZone(ZoneId.systemDefault())
            .withZoneSameInstant(ZoneId.of("Asia/Seoul"))
            .toOffsetDateTime();
  }
}
