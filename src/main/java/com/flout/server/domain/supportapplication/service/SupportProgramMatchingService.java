package com.flout.server.domain.supportapplication.service;

import com.flout.server.domain.inspection.entity.ResultStatus;
import com.flout.server.domain.inspection.repository.InspectionRepository;
import com.flout.server.domain.inspection.repository.InspectionResultRepository;
import com.flout.server.domain.inspection.repository.InspectionVulnerabilityRepository;
import com.flout.server.domain.inspectionapplication.repository.InspectionApplicationRepository;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationErrorCode;
import com.flout.server.domain.supportapplication.client.GeminiSupportClient;
import com.flout.server.domain.supportapplication.dto.response.SupportProgramMatchingResponse;
import com.flout.server.domain.supportapplication.dto.response.SupportProgramMatchingResponse.Evidence;
import com.flout.server.domain.supportapplication.dto.response.SupportProgramMatchingResponse.GuidanceInfo;
import com.flout.server.domain.supportapplication.dto.response.SupportProgramMatchingResponse.ProgramInfo;
import com.flout.server.domain.supportapplication.dto.response.SupportProgramMatchingResponse.Recommendation;
import com.flout.server.domain.supportapplication.response.code.SupportApplicationErrorCode;
import com.flout.server.domain.supportprogram.entity.SupportProgram;
import com.flout.server.domain.supportprogram.repository.SupportProgramRepository;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupportProgramMatchingService {
  private static final Set<String> EXCLUDED = Set.of("CLOSED", "ENDED", "SUSPENDED");
  private final InspectionApplicationRepository applications;
  private final InspectionRepository inspections;
  private final InspectionResultRepository results;
  private final InspectionVulnerabilityRepository vulnerabilities;
  private final SupportProgramRepository programs;
  private final GeminiSupportClient gemini;

  @Value("${app.support.fallback-guidance.id:}")
  private String guidanceId;

  @Value("${app.support.fallback-guidance.title:}")
  private String guidanceTitle;

  @Value("${app.support.fallback-guidance.description:}")
  private String guidanceDescription;

  @Value("${app.support.fallback-guidance.official-url:}")
  private String guidanceUrl;

  @Transactional(readOnly = true)
  public SupportProgramMatchingResponse match(Long userId, Long applicationId) {
    if (applicationId == null || applicationId <= 0)
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);

    var application =
        applications
            .findForResidentDetail(applicationId)
            .orElseThrow(
                () -> new BaseException(InspectionApplicationErrorCode.APPLICATION_NOT_FOUND));
    if (!application.getApplicant().getId().equals(userId))
      throw new BaseException(InspectionApplicationErrorCode.APPLICATION_DETAIL_ACCESS_DENIED);

    var result =
        inspections
            .findByApplicationId(applicationId)
            .flatMap(
                inspection ->
                    results.findByInspectionIdAndResultStatus(
                        inspection.getId(), ResultStatus.PUBLISHED))
            .orElseThrow(() -> new BaseException(SupportApplicationErrorCode.RESULT_NOT_PUBLISHED));
    var findings = vulnerabilities.findByResultIdOrderBySortOrderAsc(result.getId());
    LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
    var available =
        programs.findAllByOrderByIdAsc().stream().filter(p -> !isExcluded(p, today)).toList();

    // 실제 공식 상담 안내가 없는 상태에서 임의 연락처를 생성하지 않는다.
    validateGuidance();
    if (available.isEmpty()) return guidance(applicationId, "현재 확인 가능한 지원사업이 없어 공식 상담을 안내해 드립니다.");

    String region = extractRegion(application.getAddress());
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
        available.stream()
            .map(
                p -> {
                  Map<String, Object> row = new HashMap<>();
                  row.put("id", p.getId());
                  row.put("name", p.getName());
                  row.put("description", p.getDescription());
                  row.put("targetDescription", p.getTargetDescription());
                  row.put("eligibilityConditions", p.getEligibilityConditions());
                  row.put("exclusionConditions", p.getExclusionConditions());
                  row.put("supportedFacilities", p.getSupportedFacilities());
                  row.put("applicationStatus", p.getApplicationStatus());
                  row.put("applicationStartDate", p.getApplicationStartDate());
                  row.put("applicationEndDate", p.getApplicationEndDate());
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
    input.put("expertRecommendations", findingInputs);
    input.put("programs", programInputs);
    input.put(
        "fallbackGuidance",
        Map.of("id", guidanceId, "title", guidanceTitle, "description", guidanceDescription));
    var decision = gemini.decide(input);

    if ("CONSULTATION".equals(decision.itemType())) {
      if (!"GUIDANCE".equals(decision.matchStatus())
          || !"GUIDANCE".equals(decision.matchType())
          || !Objects.equals(guidanceId, decision.guidanceId()))
        return guidance(applicationId, "신청 가능한 사업을 확인하지 못해 공식 상담을 안내해 드립니다.");
      return guidance(applicationId, safeReason(decision.reason(), "공식 상담에서 지원 가능 여부를 확인해 주세요."));
    }
    if (!"PROGRAM".equals(decision.itemType()) || decision.programId() == null)
      return guidance(applicationId, "신청 가능한 사업을 확인하지 못해 공식 상담을 안내해 드립니다.");
    if (!Set.of("DIRECT", "COMPLEMENTARY").contains(decision.matchType()))
      return guidance(applicationId, "지원사업의 관련성을 확인하지 못해 공식 상담을 안내해 드립니다.");
    SupportProgram chosen =
        available.stream()
            .filter(p -> p.getId().equals(decision.programId()))
            .findFirst()
            .orElse(null);
    if (chosen == null) return guidance(applicationId, "현재 신청 가능한 사업을 확인하지 못해 공식 상담을 안내해 드립니다.");

    List<String> missing = new ArrayList<>(decision.missingInformation());
    // 모델의 MATCHED 판정을 그대로 신뢰하지 않고 접수 상태와 기간을 서버에서 검증한다.
    if (!"OPEN".equalsIgnoreCase(String.valueOf(chosen.getApplicationStatus())))
      missing.add("접수상태 확인 필요");
    if (chosen.getApplicationStartDate() == null || chosen.getApplicationEndDate() == null)
      missing.add("공식 접수기간 확인 필요");
    if (chosen.getEligibilityConditions() == null || chosen.getEligibilityConditions().isBlank())
      missing.add("신청 자격 확인 필요");
    // 주소의 행정구역 판정 및 기타 제외조건도 공식 공고 대조가 필요하다.
    if (region == null) missing.add("대상 지역 확인 필요");
    String matchStatus =
        missing.isEmpty() && "MATCHED".equals(decision.matchStatus())
            ? "MATCHED"
            : "NEEDS_CONFIRMATION";

    ProgramInfo program =
        new ProgramInfo(
            chosen.getName(),
            chosen.getDescription(),
            chosen.getTargetDescription(),
            chosen.getEligibilityConditions(),
            chosen.getExclusionConditions(),
            chosen.getSupportedFacilities(),
            chosen.getRequiredDocuments(),
            chosen.getApplicationMethod(),
            chosen.getApplicationStartDate(),
            chosen.getApplicationEndDate(),
            chosen.getApplicationStatus(),
            chosen.getOfficialSource(),
            chosen.getOfficialUrl());
    List<Evidence> evidence =
        Stream.of(
                new Evidence("supportProgram.name", chosen.getName()),
                new Evidence("supportProgram.targetDescription", chosen.getTargetDescription()),
                new Evidence("inspectionResult.summary", result.getSummary()))
            .filter(e -> e.sourceText() != null && !e.sourceText().isBlank())
            .toList();
    var recommendation =
        new Recommendation(
            "PROGRAM",
            chosen.getId(),
            null,
            decision.matchType(),
            safeReason(decision.reason(), "현장점검 결과와 관련된 사업으로 검토되었습니다."),
            List.of(),
            evidence,
            missing.stream().distinct().toList(),
            program,
            null);
    return new SupportProgramMatchingResponse(applicationId, matchStatus, recommendation);
  }

  private boolean isExcluded(SupportProgram p, LocalDate today) {
    String status = String.valueOf(p.getApplicationStatus()).toUpperCase(Locale.ROOT);
    if (EXCLUDED.contains(status)) return true;
    if (p.getApplicationEndDate() != null && today.isAfter(p.getApplicationEndDate())) return true;
    return p.getApplicationStartDate() != null && today.isBefore(p.getApplicationStartDate());
  }

  private void validateGuidance() {
    if (Stream.of(guidanceId, guidanceTitle, guidanceDescription, guidanceUrl)
        .anyMatch(s -> s == null || s.isBlank()))
      throw new BaseException(SupportApplicationErrorCode.MATCHING_INPUT_INVALID);
  }

  private SupportProgramMatchingResponse guidance(Long applicationId, String reason) {
    Recommendation rec =
        new Recommendation(
            "CONSULTATION",
            null,
            guidanceId,
            "GUIDANCE",
            reason,
            List.of(),
            List.of(),
            List.of(),
            null,
            new GuidanceInfo(guidanceTitle, guidanceDescription, guidanceUrl));
    return new SupportProgramMatchingResponse(applicationId, "GUIDANCE", rec);
  }

  private String safeReason(String reason, String fallback) {
    if (reason == null || reason.isBlank()) return fallback;
    return reason.length() > 300 ? reason.substring(0, 300) : reason;
  }

  private String extractRegion(String address) {
    if (address == null || address.isBlank()) return null;
    String[] parts = address.strip().split("\\s+");
    for (String part : parts) if (part.endsWith("시") || part.endsWith("군")) return part;
    return null;
  }
}
