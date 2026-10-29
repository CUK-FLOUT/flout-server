package com.flout.server.domain.inspection.dto.response;

import com.flout.server.domain.inspection.entity.Inspection;
import com.flout.server.domain.inspectionapplication.entity.PreferredTimeSlot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.Locale;

public record InspectionDetailResponse(
    Long inspectionId,
    Long applicationId,
    @Schema(description = "신청 ID를 최소 6자리로 표시한 접수번호", example = "000001") String receiptNo,
    String applicantName,
    @Schema(description = "신청 당시 전체 주소") String address,
    String detailAddress,
    @Schema(description = "신청 시 입력한 연락처") String contact,
    @Schema(description = "주민 방문 희망일. 미입력 시 null") LocalDate preferredVisitDate,
    @Schema(description = "주민 희망 시간대: MORNING / AFTERNOON / ANY. 미입력 시 null")
        PreferredTimeSlot preferredTimeSlot,
    @Schema(description = "배정된 담당자 사용자 ID. 미배정 시 null") Long inspectorId,
    @Schema(description = "배정된 담당자 이름. 미배정 시 null") String inspectorName) {
  public static InspectionDetailResponse from(Inspection inspection) {
    var application = inspection.getApplication();
    var inspector = inspection.getInspector();
    return new InspectionDetailResponse(
        inspection.getId(),
        application.getId(),
        String.format(Locale.ROOT, "%06d", application.getId()),
        application.getApplicant().getName(),
        application.getAddress(),
        application.getDetailAddress(),
        application.getContact(),
        application.getPreferredVisitDate(),
        application.getPreferredTimeSlot(),
        inspector == null ? null : inspector.getId(),
        inspector == null ? null : inspector.getName());
  }
}
