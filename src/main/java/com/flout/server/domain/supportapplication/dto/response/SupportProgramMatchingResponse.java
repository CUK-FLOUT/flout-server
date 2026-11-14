package com.flout.server.domain.supportapplication.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/** 주민에게 노출하는 매칭 결과. Gemini가 만든 사업 메타데이터는 사용하지 않는다. */
@Schema(description = "공개된 현장점검 결과와 실제 DB 지원사업을 비교한 안내")
public record SupportProgramMatchingResponse(
    @Schema(description = "입력받은 현장점검 신청 ID", example = "12") Long supportApplicationId,
    @Schema(allowableValues = {"MATCHED", "NEEDS_CONFIRMATION"}) String matchStatus,
    Recommendation recommendation) {

  public record Recommendation(
      @Schema(
              description = "현재는 실제 DB 사업만 반환",
              allowableValues = {"PROGRAM"})
          String itemType,
      @Schema(description = "서버 DB에서 검증한 지원사업 ID", example = "3") long programId,
      @Schema(nullable = true, description = "예약 필드. 현재 null") String guidanceId,
      @Schema(allowableValues = {"DIRECT", "COMPLEMENTARY"}) String matchType,
      String reason,
      List<Long> matchedActionIds,
      List<Evidence> evidence,
      List<String> missingInformation,
      ProgramInfo program,
      @Schema(nullable = true, description = "예약 필드. 현재 null") GuidanceInfo guidance) {}

  public record Evidence(String sourceField, String sourceText) {}

  public record ProgramInfo(
      String name,
      String description,
      String targetDescription,
      String eligibilityConditions,
      String exclusionConditions,
      String supportedFacilities,
      String requiredDocuments,
      String applicationMethod,
      LocalDate applicationStartDate,
      LocalDate applicationEndDate,
      String applicationStatus,
      String officialSource,
      String officialUrl) {}

  public record GuidanceInfo(String title, String description, String officialUrl) {}
}
