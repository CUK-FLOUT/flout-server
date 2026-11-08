package com.flout.server.domain.supportapplication.service;

import com.flout.server.domain.inspection.entity.InspectionVulnerability;
import com.flout.server.domain.inspection.entity.ResultStatus;
import com.flout.server.domain.inspection.repository.InspectionRepository;
import com.flout.server.domain.inspection.repository.InspectionResultRepository;
import com.flout.server.domain.inspection.repository.InspectionVulnerabilityRepository;
import com.flout.server.domain.inspectionapplication.repository.InspectionApplicationRepository;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationErrorCode;
import com.flout.server.domain.supportapplication.dto.response.SupportProgramMatchingResponse;
import com.flout.server.domain.supportapplication.response.code.SupportApplicationErrorCode;
import com.flout.server.domain.supportprogram.entity.SupportProgram;
import com.flout.server.domain.supportprogram.repository.SupportProgramRepository;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.util.*;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupportProgramMatchingService {
  private static final Map<String, List<String>> TYPE_KEYWORDS =
      Map.of(
          "ENTRANCE", List.of("물막이판", "차수판", "방수판", "출입구", "현관", "문턱"),
          "WINDOW_OPENING", List.of("창호", "창문", "개구부", "방수"),
          "DRAIN", List.of("배수", "하수", "역류", "펌프", "배수구"),
          "OTHER_STRUCTURE", List.of("벽체", "균열", "지붕", "방수", "구조"));

  private final InspectionApplicationRepository applications;
  private final InspectionRepository inspections;
  private final InspectionResultRepository results;
  private final InspectionVulnerabilityRepository vulnerabilities;
  private final SupportProgramRepository programs;

  @Transactional(readOnly = true)
  public SupportProgramMatchingResponse match(Long userId, Long applicationId) {
    if (applicationId == null || applicationId <= 0) {
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    }

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
    var candidates = programs.findAllByOrderByIdAsc();
    if (candidates.isEmpty()) {
      throw new BaseException(SupportApplicationErrorCode.PROGRAM_NOT_FOUND);
    }

    var scored = bestMatch(candidates, findings, application.getAddress()).orElseThrow();
    if (scored.score() <= 0) {
      // 관련 근거가 없는 사업을 임의로 추천하거나 지원 자격이 있는 것처럼 보이지 않는다.
      throw new BaseException(SupportApplicationErrorCode.MATCHED_PROGRAM_NOT_FOUND);
    }
    var matched = scored.program();
    var reasonFinding = bestFinding(matched, findings);

    return new SupportProgramMatchingResponse(
        applicationId,
        matched.getId(),
        matched.getName(),
        jurisdiction(application.getAddress()),
        matched.getDescription(),
        matched.getTargetDescription(),
        matched.getOperatingStatus(),
        matchedReason(reasonFinding, result.getSummary()),
        false);
  }

  private ScoredProgram scored(
      SupportProgram program, List<InspectionVulnerability> findings, String address) {
    return new ScoredProgram(program, score(program, findings, address));
  }

  private Optional<ScoredProgram> bestMatch(
      List<SupportProgram> candidates, List<InspectionVulnerability> findings, String address) {
    return candidates.stream()
        .map(program -> scored(program, findings, address))
        .max(
            Comparator.comparingInt(ScoredProgram::score)
                .thenComparing(value -> value.program().getId(), Comparator.reverseOrder()));
  }

  private int score(
      SupportProgram program, List<InspectionVulnerability> findings, String address) {
    String programText =
        Stream.of(
                program.getName(),
                program.getDescription(),
                program.getTargetDescription(),
                program.getOperatingStatus())
            .filter(Objects::nonNull)
            .map(String::toLowerCase)
            .reduce("", (left, right) -> left + " " + right);

    int score = 0;
    for (var finding : findings) {
      var typeKeywords =
          finding.getType() == null
              ? List.<String>of()
              : TYPE_KEYWORDS.getOrDefault(finding.getType(), List.of());
      for (String keyword : typeKeywords) {
        if (programText.contains(keyword)) score += 10;
      }
      for (String token : tokens(finding.getTitle())) {
        if (programText.contains(token)) score += 3;
      }
    }
    for (String token : tokens(address)) {
      if ((token.endsWith("시") || token.endsWith("군") || token.endsWith("구"))
          && programText.contains(token)) score += 2;
    }
    return score;
  }

  private InspectionVulnerability bestFinding(
      SupportProgram program, List<InspectionVulnerability> findings) {
    return findings.stream()
        .filter(finding -> score(program, List.of(finding), "") > 0)
        .max(
            Comparator.<InspectionVulnerability>comparingInt(
                    finding -> score(program, List.of(finding), ""))
                .thenComparingInt(finding -> -finding.getSortOrder()))
        .orElse(null);
  }

  private List<String> tokens(String value) {
    if (value == null || value.isBlank()) return List.of();
    return Arrays.stream(value.toLowerCase().split("[^0-9a-zA-Z가-힣]+"))
        .filter(token -> token.length() >= 2)
        .toList();
  }

  private String jurisdiction(String address) {
    if (address == null) return null;
    return Arrays.stream(address.strip().split("\\s+"))
        .filter(token -> token.endsWith("시") || token.endsWith("군"))
        .reduce((first, second) -> second)
        .orElse(null);
  }

  private String matchedReason(InspectionVulnerability finding, String summary) {
    if (finding != null && finding.getTitle() != null && !finding.getTitle().isBlank()) {
      return "현장점검에서 " + finding.getTitle().strip() + " 취약지점이 확인됐어요.";
    }
    if (summary != null && !summary.isBlank()) {
      return "현장점검 결과를 바탕으로 확인할 수 있는 지원사업이에요.";
    }
    return "입력한 주소에서 확인할 수 있는 지원사업이에요.";
  }

  private record ScoredProgram(SupportProgram program, int score) {}
}
