package com.flout.server.domain.inspectionapplication.dto.response;

import com.flout.server.domain.construction.entity.ConstructionStatus;
import com.flout.server.domain.inspectionapplication.entity.PreferredTimeSlot;
import com.flout.server.domain.supportapplication.entity.SupportApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.*;

public record ApplicationConstructionResponse(
    Long applicationId,
    String receiptNo,
    String applicantName,
    String address,
    @Schema(nullable = true, description = "원본 현장점검 신청의 상세주소") String detailAddress,
    @Schema(nullable = true, description = "최근 접수된 지원 신청 ID. 미신청 또는 지원 DRAFT만 있으면 null")
        Long supportApplicationId,
    @Schema(nullable = true, description = "최근 접수된 지원 상태. 미신청 또는 지원 DRAFT만 있으면 null")
        SupportApplicationStatus supportStatus,
    @Schema(nullable = true, description = "승인 지원에 실제 연결된 시공. 연결되지 않았으면 null")
        ConstructionInfo construction) {
  public record ConstructionInfo(
      Long constructionId,
      ConstructionStatus status,
      @Schema(description = "지원 신청에 저장된 신청 시설. 실제 조치 내용은 최종 설치 결과와 구분") String requestedFacility,
      @Schema(nullable = true, description = "운영자가 승인한 지원 범위") String supportScope,
      @Schema(description = "지원 신청 당시 주민 시공 희망일. 확정 일정이 아님") LocalDate preferredConstructionDate,
      PreferredTimeSlot preferredTimeSlot,
      @Schema(nullable = true, description = "저장된 확정 시공일. 미배정이면 null") LocalDate constructionDate,
      @Schema(nullable = true, description = "저장된 확정 시공 시간. HH:mm, 미배정이면 null", example = "10:00")
          String constructionTime,
      @Schema(nullable = true, description = "배정된 담당자 ID. 미배정이면 null") Long managerId,
      @Schema(nullable = true, description = "배정된 담당자 이름. 마스킹하지 않음") String managerName,
      @Schema(nullable = true, description = "COMPLETED에서만 반환하는 실제 전체 완료 시각. 한국 시간 +09:00")
          OffsetDateTime completedAt,
      @Schema(nullable = true, description = "COMPLETED에서만 반환하는 저장된 최종 설치 결과")
          String finalInstallationResult) {}
}
