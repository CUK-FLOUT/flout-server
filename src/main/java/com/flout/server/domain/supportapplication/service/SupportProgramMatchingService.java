package com.flout.server.domain.supportapplication.service;

import com.flout.server.domain.inspection.entity.ResultStatus;
import com.flout.server.domain.inspection.repository.InspectionRepository;
import com.flout.server.domain.inspection.repository.InspectionResultRepository;
import com.flout.server.domain.inspection.repository.InspectionVulnerabilityRepository;
import com.flout.server.domain.inspection.repository.PreventionRecommendationRepository;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class SupportProgramMatchingService {
  private static final Set<String> EXCLUDED = Set.of("CLOSED", "ENDED", "SUSPENDED");
  private final InspectionApplicationRepository applications;
  private final InspectionRepository inspections;
  private final InspectionResultRepository results;
  private final InspectionVulnerabilityRepository vulnerabilities;
  private final PreventionRecommendationRepository preventionRecommendations;
  private final SupportFacilityMatcher facilityMatcher;
  private final SupportProgramRepository programs;
  private final GeminiSupportClient gemini;
  private final PlatformTransactionManager transactionManager;

  @Value("${app.support.fallback-guidance.id:}")
  private String guidanceId;

  @Value("${app.support.fallback-guidance.title:}")
  private String guidanceTitle;

  @Value("${app.support.fallback-guidance.description:}")
  private String guidanceDescription;

  @Value("${app.support.fallback-guidance.official-url:}")
  private String guidanceUrl;

  public SupportProgramMatchingResponse match(Long userId, Long applicationId) {
    if (applicationId == null || applicationId <= 0) {
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    }

    // 서버 설정 오류는 사용자 입력 오류가 아니며, DB 조회 전에 발견한다.
    validateGuidance();

    // DB 조회와 필요한 데이터 복사를 완료한 후 트랜잭션을 종료한다.
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    transaction.setReadOnly(true);
    MatchingSnapshot snapshot = transaction.execute(status -> loadSnapshot(userId, applicationId));
    if (snapshot == null) {
      throw new IllegalStateException("지원사업 조회 결과를 준비하지 못했습니다.");
    }
    if (snapshot.candidates().isEmpty()) {
      return guidance(applicationId, "현재 확인 가능한 지원사업이 없어 공식 상담을 안내해 드립니다.");
    }

    // 외부 네트워크 호출은 트랜잭션이 완전히 종료된 뒤에 수행한다.
    var decision = gemini.decide(snapshot.input());
    if ("CONSULTATION".equals(decision.itemType())) {
      if (!"GUIDANCE".equals(decision.matchStatus())
          || !"GUIDANCE".equals(decision.matchType())
          || !Objects.equals(guidanceId, decision.guidanceId())) {
        return guidance(applicationId, "신청 가능한 사업을 확인하지 못해 공식 상담을 안내해 드립니다.");
      }
      return guidance(applicationId, safeReason(decision.reason(), "공식 상담에서 지원 가능 여부를 확인해 주세요."));
    }
    if (!"PROGRAM".equals(decision.itemType()) || decision.programId() == null) {
      return guidance(applicationId, "신청 가능한 사업을 확인하지 못해 공식 상담을 안내해 드립니다.");
    }
    if (!Set.of("DIRECT", "COMPLEMENTARY").contains(decision.matchType())) {
      return guidance(applicationId, "지원사업의 관련성을 확인하지 못해 공식 상담을 안내해 드립니다.");
    }

    ProgramCandidate chosen =
        snapshot.candidates().stream()
            .filter(p -> p.id().equals(decision.programId()))
            .findFirst()
            .orElse(null);
    if (chosen == null) {
      return guidance(applicationId, "현재 신청 가능한 사업을 확인하지 못해 공식 상담을 안내해 드립니다.");
    }

    ProgramInfo info = chosen.info();
    var facilityMatch =
        "DIRECT".equals(decision.matchType())
            ? facilityMatcher.match(info.supportedFacilities(), snapshot.actions())
            : new SupportFacilityMatcher.MatchResult(true, List.of(), List.of());
    // DIRECT means related to an expert-recommended facility. Do not claim a false match.
    if ("DIRECT".equals(decision.matchType())
        && facilityMatch.facilitiesKnown()
        && !facilityMatch.hasMatch()) {
      return guidance(applicationId, "권장시설과 지원시설이 일치하지 않아 공식 상담을 안내합니다.");
    }
    List<String> missing = new ArrayList<>(decision.missingInformation());
    if ("DIRECT".equals(decision.matchType()) && !facilityMatch.facilitiesKnown()) {
      missing.add("지원시설 정보 확인 필요");
    }
    if (!"OPEN".equalsIgnoreCase(String.valueOf(info.applicationStatus()))) {
      missing.add("접수상태 확인 필요");
    }
    if (info.applicationStartDate() == null || info.applicationEndDate() == null) {
      missing.add("공식 접수기간 확인 필요");
    }
    if (info.eligibilityConditions() == null || info.eligibilityConditions().isBlank()) {
      missing.add("신청 자격 확인 필요");
    }
    if (snapshot.region() == null) {
      missing.add("대상 지역 확인 필요");
    }
    String matchStatus =
        missing.isEmpty() && "MATCHED".equals(decision.matchStatus())
            ? "MATCHED"
            : "NEEDS_CONFIRMATION";

    List<Evidence> evidence =
        Stream.concat(
                Stream.of(
                    new Evidence("supportProgram.name", info.name()),
                    new Evidence("supportProgram.targetDescription", info.targetDescription()),
                    new Evidence("inspectionResult.summary", snapshot.summary())),
                facilityMatch.matchedActions().stream()
                    .map(
                        action ->
                            new Evidence(
                                "preventionRecommendation." + action.id(), action.description())))
            .filter(e -> e.sourceText() != null && !e.sourceText().isBlank())
            .toList();

    var recommendation =
        new Recommendation(
            "PROGRAM",
            chosen.id(),
            null,
            decision.matchType(),
            safeReason(decision.reason(), "현장점검 결과와 관련된 사업으로 검토되었습니다."),
            facilityMatch.matchedActionIds(),
            evidence,
            missing.stream().distinct().toList(),
            info,
            null);
    return new SupportProgramMatchingResponse(applicationId, matchStatus, recommendation);
  }

  private MatchingSnapshot loadSnapshot(Long userId, Long applicationId) {
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
    var available =
        programs.findAllByOrderByIdAsc().stream().filter(p -> !isExcluded(p, today)).toList();
    String region = extractRegion(application.getAddress());

    List<ProgramCandidate> candidates =
        available.stream()
            .map(
                p ->
                    new ProgramCandidate(
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
    return new MatchingSnapshot(input, candidates, region, result.getSummary(), actions);
  }

  private record ProgramCandidate(Long id, ProgramInfo info) {}

  private record MatchingSnapshot(
      Map<String, Object> input,
      List<ProgramCandidate> candidates,
      String region,
      String summary,
      List<SupportFacilityMatcher.Action> actions) {}

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
