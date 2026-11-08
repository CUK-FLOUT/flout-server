package com.flout.server.domain.construction.dto.response;

import com.flout.server.domain.construction.entity.ConstructionStatus;
import com.flout.server.domain.inspectionapplication.entity.PreferredTimeSlot;
import com.flout.server.domain.supportapplication.entity.SupportApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "운영자 시공 상세. 업로드된 시공 사진은 완료 전에도 운영자에게 반환합니다.")
public record AdminConstructionDetailResponse(
    Long constructionId,
    Long supportApplicationId,
    Long applicationId,
    @Schema(description = "현장점검 신청 ID를 최소 6자리로 표시한 접수번호", example = "000003") String receiptNo,
    String applicantName,
    SupportApplicationStatus supportStatus,
    @Schema(
            description = "DB CONFIRMED는 VERIFIED로 반환",
            allowableValues = {"PENDING", "VERIFIED", "NOT_REQUIRED"})
        String landlordConsentStatus,
    String requestedFacility,
    @Schema(nullable = true, description = "운영자가 승인한 지원 범위. 화면의 재료·설치 형태로 사용") String supportScope,
    LocalDate preferredConstructionDate,
    PreferredTimeSlot preferredTimeSlot,
    @Schema(nullable = true, description = "확정 시공일. 미배정 시 null")
        LocalDate scheduledConstructionDate,
    @Schema(nullable = true, description = "확정 시공 시간. 미배정 시 null")
        LocalTime scheduledConstructionTime,
    ConstructionStatus status,
    @Schema(nullable = true, description = "배정된 운영자 ID. 미배정 시 null") Long managerId,
    @Schema(nullable = true, description = "배정된 운영자 이름. 미배정 시 null") String managerName,
    @Schema(description = "전체 완료되어 최종 결과가 주민에게 공개되었는지 여부") boolean resultPublished,
    @Schema(nullable = true, description = "주민 공개용 최종 설치 결과. 미완료 시 null")
        String finalInstallationResult,
    @Schema(nullable = true, description = "실제 완료 시각. 미완료 시 null") OffsetDateTime completedAt,
    @Schema(description = "운영자가 업로드한 시공 사진. 없으면 빈 배열") List<Photo> photos) {
  @Schema(name = "AdminConstructionDetailPhoto")
  public record Photo(
      Long photoId,
      String fileName,
      @Schema(description = "15분 유효한 비공개 사진 조회 URL") String url,
      long size,
      String contentType,
      OffsetDateTime uploadedAt) {}
}
