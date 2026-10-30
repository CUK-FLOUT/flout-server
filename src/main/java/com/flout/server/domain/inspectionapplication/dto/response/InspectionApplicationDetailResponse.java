package com.flout.server.domain.inspectionapplication.dto.response;

import com.flout.server.domain.inspectionapplication.entity.*;
import com.flout.server.domain.riskassessment.entity.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/** GIS 신청 정보 탭의 기본 정보와 주민 입력 정보를 반환한다. 층수는 별도로 제공하지 않는다. */
public record InspectionApplicationDetailResponse(
    @Schema(description = "현장점검 신청 ID", example = "1") Long applicationId,
    @Schema(description = "신청 ID를 최소 6자리로 표시한 접수번호", example = "000001") String receiptNo,
    @Schema(description = "신청자 계정의 이름", example = "김주민") String applicantName,
    @Schema(description = "신청 시 입력한 연락처", example = "010-1234-1234") String contact,
    @Schema(description = "신청에 저장된 전체 주소", example = "경기도 부천시 원미구 길주로 53") String address,
    @Schema(
            description = "신청 상세주소. 값이 없으면 null",
            example = "101호",
            nullable = true,
            types = {"string", "null"})
        String detailAddress,
    @Schema(description = "거주 형태: OWNER=자가, TENANT=세입자, ETC=기타", example = "TENANT")
        OccupancyType occupancyType,
    @Schema(description = "연결된 AI 분석 취약도: HIGH=높음, CAUTION=주의, LOW=낮음", example = "HIGH")
        RiskLevel riskLevel,
    @Schema(description = "과거 침수 경험: YES=있음, NO=없음, UNKNOWN=모름", example = "YES")
        FloodExperience floodExperience,
    @Schema(description = "침수 유입 위치. 미입력 시 빈 배열", example = "[\"BATHROOM\",\"DRAIN\"]")
        List<InflowLocation> inflowLocations,
    @Schema(description = "기존 침수 방지 시설 설치 여부", example = "false") boolean existingFacility,
    @Schema(
            description = "주민 방문 희망일. 미입력 시 null",
            format = "date",
            example = "2026-11-03",
            nullable = true,
            types = {"string", "null"})
        LocalDate preferredVisitDate,
    @Schema(
            description = "점검 요청 사유. 미입력 시 null",
            example = "최근 배수구 역류가 있어 점검을 희망합니다.",
            nullable = true,
            types = {"string", "null"})
        String requestReason,
    @Schema(
            description = "방문 가능 시간대: MORNING=오전, AFTERNOON=오후, ANY=상관없음. 미입력 시 null",
            example = "MORNING",
            nullable = true,
            types = {"string", "null"})
        PreferredTimeSlot preferredTimeSlot) {

  public static InspectionApplicationDetailResponse from(
      InspectionApplication application, List<InflowLocation> inflowLocations) {
    return new InspectionApplicationDetailResponse(
        application.getId(),
        String.format(Locale.ROOT, "%06d", application.getId()),
        application.getApplicant().getName(),
        application.getContact(),
        application.getAddress(),
        application.getDetailAddress(),
        application.getOccupancyType(),
        application.getAssessment().getRiskLevel(),
        application.getFloodExperience(),
        List.copyOf(inflowLocations),
        application.isExistingFacility(),
        application.getPreferredVisitDate(),
        application.getRequestReason(),
        application.getPreferredTimeSlot());
  }
}
