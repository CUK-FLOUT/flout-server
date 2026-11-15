package com.flout.server.domain.supportapplication.service;

import com.flout.server.domain.supportapplication.client.GeminiSupportClient;
import com.flout.server.domain.supportapplication.dto.response.SupportProgramMatchingResponse;
import com.flout.server.domain.supportapplication.dto.response.SupportProgramMatchingResponse.Evidence;
import com.flout.server.domain.supportapplication.dto.response.SupportProgramMatchingResponse.ProgramInfo;
import com.flout.server.domain.supportapplication.dto.response.SupportProgramMatchingResponse.Recommendation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SupportProgramMatchingDecisionPolicy {
  private static final Set<String> PROGRAM_MATCH_TYPES = Set.of("DIRECT", "COMPLEMENTARY");
  private final SupportFacilityMatcher facilityMatcher;

  public SupportProgramMatchingResponse resolve(
      Long applicationId,
      SupportProgramMatchingSnapshot snapshot,
      GeminiSupportClient.Decision decision) {
    SupportProgramCandidate chosen =
        snapshot.candidates().stream()
            .filter(p -> p.id().equals(decision.programId()))
            .findFirst()
            // 모델이 허용된 후보 밖의 ID를 반환해도 실제 DB 후보만 노출한다.
            .orElse(snapshot.candidates().getFirst());
    boolean validDecision =
        "PROGRAM".equals(decision.itemType())
            && chosen.id().equals(decision.programId())
            && PROGRAM_MATCH_TYPES.contains(decision.matchType());
    String matchType = validDecision ? decision.matchType() : "COMPLEMENTARY";

    ProgramInfo info = chosen.info();
    var facilityMatch =
        "DIRECT".equals(matchType)
            ? facilityMatcher.match(info.supportedFacilities(), snapshot.actions())
            : emptyFacilityMatch();
    // DIRECT는 전문가 권장시설과 실제로 일치할 때만 유지한다.
    String reason = decision.reason();
    if ("DIRECT".equals(matchType)
        && facilityMatch.facilitiesKnown()
        && !facilityMatch.hasMatch()) {
      matchType = "COMPLEMENTARY";
      facilityMatch = emptyFacilityMatch();
      reason = "권장시설과 직접 일치하지 않지만 참고할 수 있는 지원사업입니다.";
      validDecision = false;
    } else if (!validDecision) {
      reason = "가장 관련 있는 지원사업을 참고용으로 안내합니다.";
    }
    List<String> missing = new ArrayList<>(decision.missingInformation());
    if ("DIRECT".equals(matchType) && !facilityMatch.facilitiesKnown()) {
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
        validDecision && missing.isEmpty() && "MATCHED".equals(decision.matchStatus())
            ? "MATCHED"
            : "NEEDS_CONFIRMATION";

    List<Evidence> evidence = evidence(info, snapshot, facilityMatch);
    var recommendation =
        new Recommendation(
            "PROGRAM",
            chosen.id(),
            null,
            matchType,
            safeReason(reason, "현장점검 결과와 관련된 사업으로 검토되었습니다."),
            facilityMatch.matchedActionIds(),
            evidence,
            missing.stream().distinct().toList(),
            info,
            null);
    return new SupportProgramMatchingResponse(applicationId, matchStatus, recommendation);
  }

  private List<Evidence> evidence(
      ProgramInfo info,
      SupportProgramMatchingSnapshot snapshot,
      SupportFacilityMatcher.MatchResult facilityMatch) {
    return Stream.concat(
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
  }

  private SupportFacilityMatcher.MatchResult emptyFacilityMatch() {
    return new SupportFacilityMatcher.MatchResult(true, List.of(), List.of());
  }

  private String safeReason(String reason, String fallback) {
    if (reason == null || reason.isBlank()) return fallback;
    return reason.length() > 300 ? reason.substring(0, 300) : reason;
  }
}
