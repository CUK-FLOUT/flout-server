package com.flout.server.domain.inspectionapplication.dto.response;

import com.flout.server.domain.construction.entity.ConstructionStatus;
import com.flout.server.domain.supportapplication.entity.SupportApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record ApplicationSupportResponse(
    Long applicationId,
    String receiptNo,
    String applicantName,
    String address,
    @Schema(nullable = true, description = "원본 현장점검 신청의 상세주소. 미입력 시 null") String detailAddress,
    @Schema(nullable = true, description = "가장 최근 접수된 지원 신청. 미신청 또는 DRAFT만 있으면 null")
        SupportInfo support) {
  public record SupportInfo(
      Long supportApplicationId,
      Long programId,
      String programName,
      SupportApplicationStatus status,
      String requestedFacility,
      @Schema(nullable = true, description = "APPROVED일 때 저장된 승인 지원 범위. 그 외 null")
          String supportScope,
      @Schema(description = "지원 신청 당시 거주 형태가 TENANT이면 true, OWNER·ETC는 false")
          boolean landlordConsentRequired,
      @Schema(description = "해당 신청에 임대인 동의서 파일이 실제 저장되어 있는지 여부. 확인 완료 상태와는 별개")
          boolean landlordConsentDocumentSubmitted,
      @Schema(nullable = true, description = "APPROVED이며 실제 시공 건이 있을 때의 시공 ID") Long constructionId,
      @Schema(nullable = true, description = "연결된 시공의 실제 상태. 시공 건이 없으면 null")
          ConstructionStatus constructionStatus,
      @Schema(nullable = true, description = "REJECTED일 때 운영자가 저장한 미승인 사유. 그 외 null")
          String rejectionReason) {}
}
