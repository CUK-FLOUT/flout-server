package com.flout.server.domain.supportapplication.dto.response;

import com.flout.server.domain.inspection.entity.InspectionStatus;
import com.flout.server.domain.inspectionapplication.entity.OccupancyType;
import com.flout.server.domain.inspectionapplication.entity.PreferredTimeSlot;
import com.flout.server.domain.supportapplication.entity.SupportApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "운영자 지원 자격 검토 화면 정보")
public record SupportApplicationReviewResponse(
    Long supportApplicationId,
    Long applicationId,
    @Schema(description = "원본 현장점검 신청 ID의 최소 6자리 접수번호") String receiptNo,
    String applicantName,
    @Schema(description = "가운데 번호를 마스킹한 신청 당시 연락처") String contact,
    String address,
    String detailAddress,
    OccupancyType occupancyType,
    @Schema(description = "공개된 점검 결과의 취약지점. 저장 순서 유지, 중복 유형 제외") List<String> vulnerabilityTypes,
    Long inspectionId,
    InspectionStatus inspectionStatus,
    boolean inspectionResultPublished,
    List<String> recommendedActions,
    String requestedFacility,
    @Schema(description = "현장점검 신청 때 입력한 주민 요청 사유. 미입력 시 null", nullable = true)
        String residentRequest,
    @Schema(description = "설치 희망 방문일. 확정 시공일과 다릅니다.") LocalDate preferredConstructionDate,
    PreferredTimeSlot preferredTimeSlot,
    @Schema(description = "세입자(TENANT)이면 true. 서류 확인 완료 상태와 별개입니다.")
        boolean landlordConsentRequired,
    Program program,
    @Schema(description = "운영자가 승인 시 저장한 지원 범위. 미확정이면 null", nullable = true) String supportScope,
    List<SupportApplicationDetailResponse.Attachment> attachments,
    SupportApplicationStatus status) {
  public record Program(
      Long programId,
      String name,
      String description,
      String targetDescription,
      String operatingStatus) {}
}
