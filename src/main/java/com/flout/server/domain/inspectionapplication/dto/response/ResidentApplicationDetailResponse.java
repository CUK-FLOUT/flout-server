package com.flout.server.domain.inspectionapplication.dto.response;

import com.flout.server.domain.inspection.entity.InspectionStatus;
import com.flout.server.domain.inspectionapplication.entity.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

public record ResidentApplicationDetailResponse(
    Long applicationId,
    @Schema(description = "신청 ID를 최소 6자리로 표시한 접수번호", example = "000001") String receiptNumber,
    Applicant applicant,
    String address,
    @Schema(description = "신청 상세주소", nullable = true) String detailAddress,
    OccupancyType occupancyType,
    FloodExperience floodExperience,
    List<InflowLocation> inflowLocations,
    boolean existingFacility,
    String requestReason,
    LocalDate preferredVisitDate,
    PreferredTimeSlot preferredTimeSlot,
    long attachmentCount,
    InspectionApplicationStatus applicationStatus,
    @Schema(description = "현장점검 진행 상태. 점검 데이터가 없으면 null", nullable = true)
        InspectionStatus inspectionStatus,
    @Schema(description = "공개된 점검 결과 존재 여부") boolean resultPublished,
    LocalDateTime createdAt) {

  public record Applicant(String name, String phone) {}

  public static ResidentApplicationDetailResponse from(
      InspectionApplication application,
      List<InflowLocation> locations,
      long attachmentCount,
      InspectionStatus inspectionStatus,
      boolean resultPublished) {
    return new ResidentApplicationDetailResponse(
        application.getId(),
        String.format(Locale.ROOT, "%06d", application.getId()),
        new Applicant(application.getApplicant().getName(), application.getContact()),
        application.getAddress(),
        application.getDetailAddress(),
        application.getOccupancyType(),
        application.getFloodExperience(),
        List.copyOf(locations),
        application.isExistingFacility(),
        application.getRequestReason(),
        application.getPreferredVisitDate(),
        application.getPreferredTimeSlot(),
        attachmentCount,
        application.getStatus(),
        inspectionStatus,
        resultPublished,
        application.getCreatedAt());
  }
}
