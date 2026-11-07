package com.flout.server.domain.inspectionapplication.service;

import com.flout.server.domain.construction.entity.Construction;
import com.flout.server.domain.construction.entity.ConstructionStatus;
import com.flout.server.domain.construction.repository.ConstructionRepository;
import com.flout.server.domain.inspection.entity.*;
import com.flout.server.domain.inspection.repository.*;
import com.flout.server.domain.inspectionapplication.dto.response.*;
import com.flout.server.domain.inspectionapplication.entity.*;
import com.flout.server.domain.supportapplication.entity.*;
import com.flout.server.domain.supportapplication.repository.SupportApplicationRepository;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResidentApplicationProgressReader {
  private final InspectionRepository inspections;
  private final InspectionResultRepository results;
  private final SupportApplicationRepository supports;
  private final ConstructionRepository constructions;

  public Map<Long, ResidentApplicationProgressResponse> read(
      List<InspectionApplication> applications) {
    if (applications.isEmpty()) return Map.of();
    var ids = applications.stream().map(InspectionApplication::getId).toList();
    var inspectionRows = inspections.findByApplicationIdIn(ids);
    var inspectionByApplication =
        inspectionRows.stream()
            .collect(Collectors.toMap(i -> i.getApplication().getId(), Function.identity()));
    var published =
        inspectionRows.isEmpty()
            ? List.<InspectionResult>of()
            : results.findByInspectionIdInAndResultStatus(
                inspectionRows.stream().map(Inspection::getId).toList(), ResultStatus.PUBLISHED);
    var resultByInspection =
        published.stream()
            .collect(Collectors.toMap(r -> r.getInspection().getId(), Function.identity()));
    var supportRows =
        supports.findByApplicationIdInAndStatusNotOrderBySubmittedAtDescIdDesc(
            ids, SupportApplicationStatus.DRAFT);
    Map<Long, SupportApplication> supportByApplication = new HashMap<>();
    for (var support : supportRows)
      supportByApplication.putIfAbsent(support.getApplication().getId(), support);
    var approvedIds =
        supportByApplication.values().stream()
            .filter(s -> s.getStatus() == SupportApplicationStatus.APPROVED)
            .map(SupportApplication::getId)
            .toList();
    var constructionRows =
        approvedIds.isEmpty()
            ? List.<Construction>of()
            : constructions.findBySupportApplicationIdIn(approvedIds);
    var constructionBySupport =
        constructionRows.stream()
            .collect(Collectors.toMap(c -> c.getSupportApplication().getId(), Function.identity()));
    Map<Long, ResidentApplicationProgressResponse> progress = new HashMap<>();
    for (var application : applications) {
      var inspection = inspectionByApplication.get(application.getId());
      var result = inspection == null ? null : resultByInspection.get(inspection.getId());
      var support = supportByApplication.get(application.getId());
      var construction = support == null ? null : constructionBySupport.get(support.getId());
      progress.put(
          application.getId(), map(application, inspection, result, support, construction));
    }
    return progress;
  }

  private ResidentApplicationProgressResponse map(
      InspectionApplication a,
      Inspection i,
      InspectionResult r,
      SupportApplication s,
      Construction c) {
    boolean draft = a.getStatus() == InspectionApplicationStatus.DRAFT;
    if (draft) {
      i = null;
      r = null;
      s = null;
      c = null;
    }
    var stage = stage(a, i, r, s, c);
    String title =
        c != null && c.getStatus() != ConstructionStatus.PENDING
            ? "시공 · " + s.getRequestedFacility()
            : s != null
                ? "지원 · " + s.getRequestedFacility()
                : r != null ? "현장점검 · 공개된 결과 확인" : "현장점검 · 침수 위험 점검 신청";
    LocalDateTime updated =
        max(
            a.getCreatedAt(),
            a.getSubmittedAt(),
            i == null ? null : i.getUpdatedAt(),
            r == null ? null : r.getPublishedAt(),
            s == null ? null : s.getSubmittedAt(),
            s == null ? null : s.getApprovedAt(),
            c == null ? null : c.getUpdatedAt());
    return new ResidentApplicationProgressResponse(
        title,
        stage,
        stage == ResidentApplicationStage.COMPLETED
            || stage == ResidentApplicationStage.SUPPORT_REJECTED,
        time(updated),
        i == null ? null : i.getId(),
        s == null ? null : s.getId(),
        c == null ? null : c.getId(),
        r != null,
        r == null ? null : r.getSummary(),
        i == null
            ? null
            : new ResidentApplicationProgressResponse.Visit(
                time(i.getScheduledAt()),
                i.getInspector() == null ? null : i.getInspector().getName(),
                i.getPreVisitNotice()),
        s == null
            ? null
            : new ResidentApplicationProgressResponse.Support(
                s.getId(),
                s.getStatus(),
                s.getRequestedFacility(),
                s.getStatus() == SupportApplicationStatus.APPROVED ? s.getSupportScope() : null,
                s.getLandlordConsentStatus().name(),
                s.getStatus() == SupportApplicationStatus.REJECTED ? s.getRejectionReason() : null),
        c == null
            ? null
            : new ResidentApplicationProgressResponse.Construction(
                c.getId(),
                c.getStatus(),
                time(c.getScheduledAt()),
                c.getStatus() == ConstructionStatus.COMPLETED ? time(c.getCompletedAt()) : null,
                c.getStatus() == ConstructionStatus.COMPLETED
                    ? c.getFinalInstallationResult()
                    : null));
  }

  /**
   * 주민 표시 단계를 시공, 최신 접수 지원, 공개된 점검 결과, 점검 상태 순으로 결정한다.
   *
   * <p>임시 신청은 단계가 없으며, 점검이 완료돼도 결과 공개 전에는 점검 진행으로 표시한다.
   */
  private ResidentApplicationStage stage(
      InspectionApplication a,
      Inspection i,
      InspectionResult r,
      SupportApplication s,
      Construction c) {
    if (a.getStatus() == InspectionApplicationStatus.DRAFT) return null;
    if (c != null) {
      switch (c.getStatus()) {
        case COMPLETED:
          return ResidentApplicationStage.COMPLETED;
        case IN_PROGRESS:
          return ResidentApplicationStage.CONSTRUCTION_IN_PROGRESS;
        case SCHEDULED:
          return ResidentApplicationStage.CONSTRUCTION_SCHEDULED;
        case PENDING:
          break;
      }
    }
    if (s != null)
      return switch (s.getStatus()) {
        case APPROVED -> ResidentApplicationStage.SUPPORT_APPROVED;
        case REJECTED -> ResidentApplicationStage.SUPPORT_REJECTED;
        case SUBMITTED -> ResidentApplicationStage.SUPPORT_REVIEW;
        case DRAFT -> throw new IllegalStateException("임시 지원 신청은 진행 상태에 포함하지 않습니다.");
      };
    if (r != null) return ResidentApplicationStage.INSPECTION_COMPLETED;
    if (i == null) return ResidentApplicationStage.INSPECTION_RECEIVED;
    return switch (i.getStatus()) {
      case RECEIVED -> ResidentApplicationStage.INSPECTION_RECEIVED;
      case SCHEDULED -> ResidentApplicationStage.VISIT_SCHEDULED;
      case IN_PROGRESS, COMPLETED -> ResidentApplicationStage.INSPECTION_IN_PROGRESS;
    };
  }

  private static LocalDateTime max(LocalDateTime... values) {
    return Arrays.stream(values)
        .filter(Objects::nonNull)
        .max(Comparator.naturalOrder())
        .orElse(null);
  }

  private static OffsetDateTime time(LocalDateTime value) {
    return value == null ? null : value.atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime();
  }
}
