package com.flout.server.domain.inspectionapplication.dto.response;

import com.flout.server.domain.construction.entity.ConstructionStatus;
import com.flout.server.domain.supportapplication.entity.SupportApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "내 신청 목록과 상세에 공통 적용하는 실제 진행 상태. 지원은 최신 접수 건을 사용하며 DRAFT는 제외합니다.")
public record ResidentApplicationProgressResponse(
    String title,
    @Schema(
            nullable = true,
            description =
                "임시 신청 상세는 null. 점검 종료 후 미공개는 INSPECTION_RESULT_PENDING이며 공개 시에만 INSPECTION_COMPLETED")
        ResidentApplicationStage stage,
    @Schema(description = "COMPLETED 또는 SUPPORT_REJECTED일 때만 true") boolean closed,
    OffsetDateTime updatedAt,
    @Schema(nullable = true) Long inspectionId,
    @Schema(nullable = true) Long supportApplicationId,
    @Schema(nullable = true) Long constructionId,
    boolean resultPublished,
    @Schema(nullable = true, description = "PUBLISHED 점검 결과의 요약만 제공하며 초안은 공개하지 않습니다.")
        String inspectionResultSummary,
    @Schema(nullable = true) Visit visit,
    @Schema(nullable = true) Support support,
    @Schema(nullable = true) Construction construction) {
  @Schema(name = "ResidentApplicationVisit")
  public record Visit(
      @Schema(nullable = true, description = "확정 방문 시각. 주민 희망일로 대체하지 않습니다.")
          OffsetDateTime scheduledAt,
      @Schema(nullable = true) String inspectorName,
      @Schema(nullable = true, description = "운영자가 주민에게 전달한 방문 전 안내") String preVisitNotice) {}

  @Schema(name = "ResidentApplicationSupportSummary")
  public record Support(
      Long supportApplicationId,
      SupportApplicationStatus status,
      String requestedFacility,
      @Schema(nullable = true, description = "APPROVED일 때만 공개") String supportScope,
      @Schema(description = "실제 저장된 NOT_REQUIRED/PENDING/CONFIRMED. 이 값만으로 별도 검토 단계를 추정하지 않습니다.")
          String landlordConsentStatus,
      @Schema(nullable = true, description = "REJECTED일 때만 공개") String rejectionReason) {}

  @Schema(name = "ResidentApplicationConstructionSummary")
  public record Construction(
      Long constructionId,
      ConstructionStatus status,
      @Schema(nullable = true) OffsetDateTime scheduledAt,
      @Schema(nullable = true, description = "COMPLETED일 때만 공개") OffsetDateTime completedAt,
      @Schema(nullable = true, description = "COMPLETED일 때만 공개. 시설명으로 결과를 생성하지 않습니다.")
          String finalInstallationResult) {}
}
