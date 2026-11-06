package com.flout.server.domain.construction.dto.response;

import com.flout.server.domain.construction.entity.Construction;
import com.flout.server.domain.construction.entity.ConstructionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;

@Schema(description = "주민 본인의 시공 일정. 희망 일정과 구분한 실제 확정 일정만 반환합니다.")
public record ResidentConstructionScheduleResponse(
    Long constructionId,
    Long supportApplicationId,
    Long applicationId,
    @Schema(description = "원본 현장점검 신청 ID를 최소 6자리로 표시한 접수번호", example = "000003") String receiptNo,
    String applicantName,
    String address,
    @Schema(nullable = true) String detailAddress,
    ConstructionStatus status,
    @Schema(description = "주민이 신청한 시설") String requestedFacility,
    @Schema(nullable = true, description = "운영자가 승인한 지원 범위") String supportScope,
    @Schema(nullable = true, description = "한국 시간 기준 확정 시공일. 미배정 시 null", example = "2026-10-21")
        LocalDate scheduledConstructionDate,
    @Schema(nullable = true, description = "한국 시간 기준 확정 방문 시간. 미배정 시 null", example = "10:00:00")
        LocalTime scheduledConstructionTime) {
  public static ResidentConstructionScheduleResponse from(Construction construction) {
    var support = construction.getSupportApplication();
    var source = support.getApplication();
    var scheduledAt = construction.getScheduledAt();
    return new ResidentConstructionScheduleResponse(
        construction.getId(),
        support.getId(),
        source.getId(),
        String.format(Locale.ROOT, "%06d", source.getId()),
        support.getApplicantName(),
        support.getAddress(),
        source.getDetailAddress(),
        construction.getStatus(),
        support.getRequestedFacility(),
        support.getSupportScope(),
        scheduledAt == null ? null : scheduledAt.toLocalDate(),
        scheduledAt == null ? null : scheduledAt.toLocalTime());
  }
}
