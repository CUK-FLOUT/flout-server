package com.flout.server.domain.construction.dto.response;

import com.flout.server.domain.construction.entity.ConstructionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "주민 시공 진행·완료 화면. 완료 결과와 사진은 COMPLETED 상태에서만 공개합니다.")
public record ResidentConstructionResultResponse(
    Long constructionId,
    Long supportApplicationId,
    Long applicationId,
    String receiptNo,
    String applicantName,
    String address,
    @Schema(nullable = true) String detailAddress,
    ConstructionStatus status,
    String requestedFacility,
    @Schema(nullable = true) String supportScope,
    @Schema(nullable = true) LocalDate scheduledConstructionDate,
    @Schema(nullable = true) LocalTime scheduledConstructionTime,
    @Schema(nullable = true, description = "실제 완료 시각. 미완료 시 null") OffsetDateTime completedAt,
    @Schema(nullable = true, description = "운영자가 완료 처리하며 공개한 최종 설치 결과. 미완료 시 null")
        String finalInstallationResult,
    @Schema(description = "시공 레코드의 마지막 변경 시각. 실시간 현장 보고 시각과는 구분합니다.") OffsetDateTime updatedAt,
    @Schema(description = "실제 저장된 상태 변경 이력만 시간순으로 반환합니다. 세부 현장 작업일지가 아닙니다.")
        List<WorkHistory> workHistory,
    @Schema(description = "시공 전 공개된 현장점검 사진. 완료 시에만 제공하며 없으면 빈 배열입니다. 완료 사진과 일대일 대응을 보장하지 않습니다.")
        List<Photo> beforePhotos,
    @Schema(description = "운영자가 업로드한 시공 완료 사진. 완료 전에는 비공개이며 빈 배열을 반환합니다.") List<Photo> photos) {
  @Schema(name = "ResidentConstructionWorkHistory")
  public record WorkHistory(ConstructionStatus status, OffsetDateTime changedAt) {}

  @Schema(name = "ResidentConstructionPhoto")
  public record Photo(
      Long photoId,
      String fileName,
      @Schema(description = "15분 유효한 비공개 사진 조회 URL") String url,
      long size,
      String contentType,
      OffsetDateTime uploadedAt) {}
}
