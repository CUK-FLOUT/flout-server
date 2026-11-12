package com.flout.server.domain.supportapplication.dto.response;

import java.time.LocalDate;
import java.util.List;

/** 주민에게 노출하는 매칭 결과. Gemini가 만든 사업 메타데이터는 사용하지 않는다. */
public record SupportProgramMatchingResponse(
    Long supportApplicationId, String matchStatus, Recommendation recommendation) {

  public record Recommendation(
      String itemType,
      long programId,
      String guidanceId,
      String matchType,
      String reason,
      List<Long> matchedActionIds,
      List<Evidence> evidence,
      List<String> missingInformation,
      ProgramInfo program,
      GuidanceInfo guidance) {}

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
