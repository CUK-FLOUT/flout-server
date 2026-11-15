package com.flout.server.domain.supportapplication.service;

import com.flout.server.domain.inspection.entity.ResultStatus;
import com.flout.server.domain.inspection.repository.InspectionRepository;
import com.flout.server.domain.inspection.repository.InspectionResultRepository;
import com.flout.server.domain.inspection.repository.InspectionVulnerabilityRepository;
import com.flout.server.domain.inspection.repository.PreventionRecommendationRepository;
import com.flout.server.domain.inspectionapplication.repository.InspectionApplicationRepository;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationErrorCode;
import com.flout.server.domain.supportapplication.dto.response.SupportProgramMatchingResponse.ProgramInfo;
import com.flout.server.domain.supportapplication.response.code.SupportApplicationErrorCode;
import com.flout.server.domain.supportprogram.repository.SupportProgramRepository;
import com.flout.server.global.exception.BaseException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SupportProgramMatchingSnapshotLoader {
  private final InspectionApplicationRepository applications;
  private final InspectionRepository inspections;
  private final InspectionResultRepository results;
  private final InspectionVulnerabilityRepository vulnerabilities;
  private final PreventionRecommendationRepository preventionRecommendations;
  private final SupportProgramRepository programs;
  private final String guidanceId;
  private final String guidanceTitle;
  private final String guidanceDescription;
  private final String guidanceUrl;

  public SupportProgramMatchingSnapshotLoader(
      InspectionApplicationRepository applications,
      InspectionRepository inspections,
      InspectionResultRepository results,
      InspectionVulnerabilityRepository vulnerabilities,
      PreventionRecommendationRepository preventionRecommendations,
      SupportProgramRepository programs,
      @Value("${app.support.fallback-guidance.id:}") String guidanceId,
      @Value("${app.support.fallback-guidance.title:}") String guidanceTitle,
      @Value("${app.support.fallback-guidance.description:}") String guidanceDescription,
      @Value("${app.support.fallback-guidance.official-url:}") String guidanceUrl) {
    this.applications = applications;
    this.inspections = inspections;
    this.results = results;
    this.vulnerabilities = vulnerabilities;
    this.preventionRecommendations = preventionRecommendations;
    this.programs = programs;
    this.guidanceId = guidanceId;
    this.guidanceTitle = guidanceTitle;
    this.guidanceDescription = guidanceDescription;
    this.guidanceUrl = guidanceUrl;
  }

  @Transactional(readOnly = true)
  public SupportProgramMatchingSnapshot load(Long userId, Long applicationId) {
    validateGuidance();
    var application =
        applications
            .findForResidentDetail(applicationId)
            .orElseThrow(
                () -> new BaseException(InspectionApplicationErrorCode.APPLICATION_NOT_FOUND));
    if (!application.getApplicant().getId().equals(userId)) {
      throw new BaseException(InspectionApplicationErrorCode.APPLICATION_DETAIL_ACCESS_DENIED);
    }

    var result =
        inspections
            .findByApplicationId(applicationId)
            .flatMap(
                inspection ->
                    results.findByInspectionIdAndResultStatus(
                        inspection.getId(), ResultStatus.PUBLISHED))
            .orElseThrow(() -> new BaseException(SupportApplicationErrorCode.RESULT_NOT_PUBLISHED));
    var findings = vulnerabilities.findByResultIdOrderBySortOrderAsc(result.getId());
    List<SupportFacilityMatcher.Action> actions =
        preventionRecommendations.findByResultIdOrderBySortOrderAsc(result.getId()).stream()
            .map(item -> new SupportFacilityMatcher.Action(item.getId(), item.getDescription()))
            .toList();
    LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
    // 종료·예정 사업도 참고 연결 후보로 유지한다. 접수 가능 여부는 응답에 그대로 노출한다.
    var available = programs.findAllByOrderByIdAsc();
    String region = extractRegion(application.getAddress());

    List<SupportProgramCandidate> candidates =
        available.stream()
            .map(
                p ->
                    new SupportProgramCandidate(
                        p.getId(),
                        new ProgramInfo(
                            p.getName(),
                            p.getDescription(),
                            p.getTargetDescription(),
                            p.getEligibilityConditions(),
                            p.getExclusionConditions(),
                            p.getSupportedFacilities(),
                            p.getRequiredDocuments(),
                            p.getApplicationMethod(),
                            p.getApplicationStartDate(),
                            p.getApplicationEndDate(),
                            p.getApplicationStatus(),
                            p.getOfficialSource(),
                            p.getOfficialUrl())))
            .toList();
    List<Map<String, Object>> findingInputs =
        findings.stream()
            .map(
                f -> {
                  Map<String, Object> row = new HashMap<>();
                  row.put("type", f.getType());
                  row.put("title", f.getTitle());
                  row.put("expertOpinion", f.getExpertOpinion());
                  row.put("fieldFindings", f.getFieldFindings());
                  return row;
                })
            .toList();
    List<Map<String, Object>> programInputs =
        candidates.stream()
            .map(
                candidate -> {
                  ProgramInfo p = candidate.info();
                  Map<String, Object> row = new HashMap<>();
                  row.put("id", candidate.id());
                  row.put("name", p.name());
                  row.put("description", p.description());
                  row.put("targetDescription", p.targetDescription());
                  row.put("eligibilityConditions", p.eligibilityConditions());
                  row.put("exclusionConditions", p.exclusionConditions());
                  row.put("supportedFacilities", p.supportedFacilities());
                  row.put("applicationStatus", p.applicationStatus());
                  row.put("applicationStartDate", p.applicationStartDate());
                  row.put("applicationEndDate", p.applicationEndDate());
                  return row;
                })
            .toList();

    Map<String, Object> input = new HashMap<>();
    input.put("serverDate", today.toString());
    input.put("region", region);
    input.put(
        "housing",
        Map.of(
            "occupancyType", application.getOccupancyType().name(),
            "floodExperience", application.getFloodExperience().name(),
            "existingFacility", application.isExistingFacility()));
    input.put("inspectionFindings", findingInputs);
    input.put(
        "expertRecommendations",
        actions.stream()
            .map(
                action -> {
                  Map<String, Object> row = new HashMap<>();
                  row.put("id", action.id());
                  row.put("description", action.description());
                  return row;
                })
            .toList());
    input.put("programs", programInputs);
    input.put(
        "fallbackGuidance",
        Map.of("id", guidanceId, "title", guidanceTitle, "description", guidanceDescription));
    return new SupportProgramMatchingSnapshot(
        input, candidates, region, result.getSummary(), actions);
  }

  private void validateGuidance() {
    if (Stream.of(guidanceId, guidanceTitle, guidanceDescription, guidanceUrl)
        .anyMatch(s -> s == null || s.isBlank()))
      throw new BaseException(SupportApplicationErrorCode.MATCHING_INPUT_INVALID);
  }

  private String extractRegion(String address) {
    if (address == null || address.isBlank()) return null;
    String[] parts = address.strip().split("\\s+");
    for (String part : parts) if (part.endsWith("시") || part.endsWith("군")) return part;
    return null;
  }
}
