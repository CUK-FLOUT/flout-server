package com.flout.server.domain.supportapplication.dto.response;

import com.flout.server.domain.inspection.entity.InspectionStatus;
import com.flout.server.domain.inspectionapplication.entity.OccupancyType;
import com.flout.server.domain.inspectionapplication.entity.PreferredTimeSlot;
import com.flout.server.domain.supportapplication.entity.SupportApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

public record SupportApplicationDetailResponse(
    Long supportApplicationId,
    @Schema(description = "원본 현장점검 신청 ID를 최소 6자리로 표시한 접수번호") String receiptNo,
    String applicantName,
    @Schema(description = "가운데 번호를 마스킹한 연락처", example = "010-****-0002") String contact,
    String address,
    String detailAddress,
    @Schema(description = "현재 저장 구조가 없어 null 반환. 주소에서 추정하지 않습니다.", nullable = true)
        String housingType,
    OccupancyType occupancyType,
    @Schema(description = "공개된 점검 결과의 권장조치. 저장된 표시 순서 유지") List<String> recommendedActions,
    String requestedFacility,
    @Schema(description = "지원 전용 주민 요청사항 저장 구조가 없어 null 반환. 점검 신청 사유와 다릅니다.", nullable = true)
        String residentRequest,
    @Schema(description = "지원 신청에 저장된 설치 희망 방문일. 확정 일정과 다릅니다.") LocalDate preferredConstructionDate,
    PreferredTimeSlot preferredTimeSlot,
    Long inspectionId,
    InspectionStatus inspectionStatus,
    boolean inspectionResultPublished,
    List<Attachment> attachments,
    @Schema(allowableValues = {"PENDING", "VERIFIED", "NOT_REQUIRED"}) String landlordConsentStatus,
    @Schema(
            allowableValues = {
              "REVIEWING",
              "CONSENT_PENDING",
              "SCHEDULING",
              "APPROVED",
              "REJECTED"
            })
        SupportApplicationStatus status) {
  public record Attachment(
      String originalName,
      @Schema(description = "권한 검증 후 발급한 15분 유효 비공개 파일 조회 URL. 외부 공유 금지") String fileUrl) {}
}
