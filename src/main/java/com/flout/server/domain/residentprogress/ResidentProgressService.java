package com.flout.server.domain.residentprogress;

import com.flout.server.domain.riskassessment.repository.RiskAssessmentRepository;
import com.flout.server.global.exception.BaseException;
import io.swagger.v3.oas.annotations.media.Schema;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class ResidentProgressService {
  private final JdbcTemplate jdbc;
  private final FormDraftService drafts;
  private final RiskAssessmentRepository assessments;
  private final ObjectMapper mapper;

  public record Action(
      @Schema(description = "주민이 수행할 다음 행동 유형") String type,
      @Schema(description = "행동에 필요한 대상 ID") Map<String, Object> target) {}

  public record HistoryEvent(
      @Schema(description = "실제로 기록된 진행 단계") String stage,
      @Schema(description = "단계 발생 시각, KST") java.time.OffsetDateTime occurredAt) {}

  public record RelatedIds(
      @Schema(description = "연결된 가이드 ID", nullable = true) Long guideId,
      @Schema(description = "현장점검 신청 ID", nullable = true) Long applicationId,
      @Schema(description = "점검 ID", nullable = true) Long inspectionId,
      @Schema(description = "지원 신청 ID", nullable = true) Long supportApplicationId,
      @Schema(description = "시공 ID", nullable = true) Long constructionId,
      @Schema(description = "작성 중인 현장점검 폼 UUID", nullable = true) String inspectionFormDraftId,
      @Schema(description = "작성 중인 지원 폼 UUID", nullable = true) String supportFormDraftId) {}

  public record Flow(
      @Schema(description = "취약도 분석 ID") Long assessmentId,
      @Schema(description = "신청 종류: RISK_ASSESSMENT, INSPECTION, SUPPORT") String applicationType,
      @Schema(description = "내 신청과 같은 현장점검 신청 ID 기반 6자리 표시 번호. 입력 중인 폼은 null", nullable = true)
          String receiptNumber,
      @Schema(description = "홈 카드 제목") String title,
      @Schema(description = "분석 주소") String address,
      @Schema(description = "상세 주소", nullable = true) String detailAddress,
      @Schema(description = "분석 위험 등급") String riskLevel,
      @Schema(description = "현재 진행 단계") String currentStage,
      @Schema(description = "화면에서 안내할 다음 행동") Action nextAction,
      @Schema(description = "진행 과정의 연결 ID. 없는 ID는 null") RelatedIds relatedIds,
      @Schema(description = "실제로 기록된 단계 이력의 시간순 목록") List<HistoryEvent> stageHistory,
      @Schema(description = "종료된 진행 항목이면 true") boolean closed,
      @Schema(description = "최근 변경 시각, KST", nullable = true) java.time.OffsetDateTime updatedAt) {}

  public record Progress(
      @Schema(description = "행동 필요·진행 중·일정 및 결과 확인·종료 순의 진행 항목") List<Flow> items,
      @Schema(description = "다음 페이지 커서. 마지막 페이지에서는 null", nullable = true) String nextCursor) {}

  public record AssessmentDetail(
      @Schema(description = "취약도 분석 ID") Long assessmentId,
      @Schema(description = "분석 주소") String address,
      @Schema(description = "상세 주소", nullable = true) String detailAddress,
      @Schema(description = "분석 상태") String status,
      @Schema(description = "분석 위험 등급") String riskLevel,
      @Schema(description = "분석 생성 시각, KST") java.time.OffsetDateTime analyzedAt,
      @Schema(description = "연결된 가이드 ID", nullable = true) Long guideId,
      @Schema(description = "연결된 현장점검 신청 ID", nullable = true) Long applicationId,
      @Schema(description = "저장된 분석 결과 JSON. 없으면 null", nullable = true) JsonNode result) {}

  private record Ranked(Flow flow, int rank) {}

  private record Cursor(int rank, java.time.OffsetDateTime updatedAt, long assessmentId) {}

  private static final Comparator<Cursor> ORDER =
      Comparator.comparingInt(Cursor::rank)
          .thenComparing(Cursor::updatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
          .thenComparing(Cursor::assessmentId, Comparator.reverseOrder());

  private Cursor key(Ranked row) {
    return new Cursor(row.rank(), row.flow().updatedAt(), row.flow().assessmentId());
  }

  private Cursor parseCursor(String cursor) {
    try {
      String value = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      String[] parts = value.split("\\|", -1);
      if (parts.length != 3) throw new IllegalArgumentException();
      int rank = Integer.parseInt(parts[0]);
      long id = Long.parseLong(parts[2]);
      if (rank < 0 || rank > 3 || id <= 0) throw new IllegalArgumentException();
      return new Cursor(
          rank, parts[1].isEmpty() ? null : java.time.OffsetDateTime.parse(parts[1]), id);
    } catch (IllegalArgumentException | java.time.DateTimeException ex) {
      throw new BaseException(FormDraftErrorCode.INVALID);
    }
  }

  private String encodeCursor(Cursor cursor) {
    String value =
        cursor.rank()
            + "|"
            + (cursor.updatedAt() == null ? "" : cursor.updatedAt())
            + "|"
            + cursor.assessmentId();
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  @Transactional(readOnly = true)
  public AssessmentDetail assessment(Long userId, Long id) {
    drafts.resident(userId);
    var a =
        assessments.findById(id).orElseThrow(() -> new BaseException(FormDraftErrorCode.NOT_FOUND));
    if (!a.getUser().getId().equals(userId)) throw new BaseException(FormDraftErrorCode.NOT_FOUND);
    var guide = first("SELECT guide_id FROM prevention_guides WHERE assessment_id=?", id);
    var app =
        first(
            "SELECT application_id FROM inspection_applications WHERE assessment_id=? AND applicant_user_id=?",
            id,
            userId);
    return new AssessmentDetail(
        a.getId(),
        a.getAddress(),
        a.getDetailAddress(),
        a.getStatus().name(),
        a.getRiskLevel().name(),
        a.getCreatedAt().atOffset(java.time.ZoneOffset.ofHours(9)),
        num(guide, "guide_id"),
        num(app, "application_id"),
        a.getResultJson() == null ? null : mapper.readTree(a.getResultJson()));
  }

  @Transactional(readOnly = true)
  public Progress progress(Long userId, String cursor, int size) {
    drafts.resident(userId);
    if (size < 1 || size > 100) throw new BaseException(FormDraftErrorCode.INVALID);
    Cursor after = cursor == null ? null : parseCursor(cursor);
    var all = new ArrayList<Ranked>();
    for (var a :
        jdbc.queryForList(
            "SELECT * FROM risk_assessments WHERE user_id=? ORDER BY created_at DESC, assessment_id DESC",
            userId)) {
      all.add(flow(userId, a));
    }
    all.sort(Comparator.comparing(this::key, ORDER));
    var page =
        all.stream()
            .filter(row -> after == null || ORDER.compare(key(row), after) > 0)
            .limit((long) size + 1)
            .toList();
    var items = page.stream().limit(size).map(Ranked::flow).toList();
    return new Progress(items, page.size() > size ? encodeCursor(key(page.get(size - 1))) : null);
  }

  private Ranked flow(Long userId, Map<String, Object> a) {
    Long assessmentId = num(a, "assessment_id");
    var guide = first("SELECT * FROM prevention_guides WHERE assessment_id=?", assessmentId);
    Long guideId = num(guide, "guide_id");
    var app =
        first(
            "SELECT * FROM inspection_applications WHERE assessment_id=? AND applicant_user_id=?",
            assessmentId,
            userId);
    Long applicationId = num(app, "application_id");
    var inspectionDraft =
        first(
            "SELECT * FROM resident_form_drafts WHERE user_id=? AND draft_type='INSPECTION' AND source_id=? AND form_state <> 'COMPLETED' ORDER BY updated_at DESC,draft_id DESC",
            userId,
            assessmentId);
    var inspection =
        applicationId == null
            ? null
            : first("SELECT * FROM inspections WHERE application_id=?", applicationId);
    Long inspectionId = num(inspection, "inspection_id");
    var published =
        inspectionId == null
            ? null
            : first(
                "SELECT * FROM inspection_results WHERE inspection_id=? AND result_status='PUBLISHED'",
                inspectionId);
    var support =
        applicationId == null
            ? null
            : first(
                "SELECT * FROM support_applications WHERE application_id=? ORDER BY support_application_id DESC",
                applicationId);
    Long supportId = num(support, "support_application_id");
    var supportDraft =
        applicationId == null
            ? null
            : first(
                "SELECT * FROM resident_form_drafts WHERE user_id=? AND draft_type='SUPPORT' AND source_id=? AND form_state <> 'COMPLETED' ORDER BY updated_at DESC,draft_id DESC",
                userId,
                applicationId);
    var construction =
        supportId == null
            ? null
            : first("SELECT * FROM constructions WHERE support_application_id=?", supportId);
    Long constructionId = num(construction, "construction_id");
    String inspectionForm = app == null ? str(inspectionDraft, "draft_id") : null,
        supportForm = support == null ? str(supportDraft, "draft_id") : null;
    String stage, action;
    String targetKey;
    Object target;
    int rank;
    boolean closed = false;
    if (construction != null && "COMPLETED".equals(str(construction, "status"))) {
      stage = "CONSTRUCTION_COMPLETED";
      action = "VIEW_COMPLETION";
      targetKey = "constructionId";
      target = constructionId;
      rank = 3;
      closed = true;
    } else if (construction != null && "IN_PROGRESS".equals(str(construction, "status"))) {
      stage = "CONSTRUCTION_IN_PROGRESS";
      action = "VIEW_PROGRESS";
      targetKey = "constructionId";
      target = constructionId;
      rank = 1;
    } else if (construction != null && "SCHEDULED".equals(str(construction, "status"))) {
      stage = "CONSTRUCTION_SCHEDULED";
      action = "VIEW_CONSTRUCTION_SCHEDULE";
      targetKey = "constructionId";
      target = constructionId;
      rank = 2;
    } else if (support != null) {
      targetKey = "supportApplicationId";
      target = supportId;
      switch (str(support, "status")) {
        case "DRAFT" -> {
          stage = "SUPPORT_DRAFT";
          action = "UPLOAD_LANDLORD_CONSENT";
          rank = 0;
        }
        case "SUBMITTED" -> {
          stage = "SUPPORT_REVIEW";
          action = "VIEW_SUPPORT_PROGRESS";
          rank = 1;
        }
        case "APPROVED" -> {
          stage = "SUPPORT_APPROVED";
          action = "VIEW_CONSTRUCTION_SCHEDULE";
          rank = 2;
        }
        case "REJECTED" -> {
          stage = "SUPPORT_REJECTED";
          action = "VIEW_REJECTION";
          rank = 3;
          closed = true;
        }
        default -> throw new IllegalStateException("Unexpected support status");
      }
    } else if (supportDraft != null && published != null && !"DRAFT".equals(str(app, "status"))) {
      stage = "SUPPORT_FORM_EDITING";
      action = "CONTINUE_SUPPORT_FORM";
      targetKey = "draftId";
      target = supportForm;
      rank = 0;
    } else if (published != null) {
      stage = "INSPECTION_RESULT_PUBLISHED";
      action = "CHECK_SUPPORT_OPTIONS";
      targetKey = "applicationId";
      target = applicationId;
      rank = 2;
    } else if (inspection != null && "COMPLETED".equals(str(inspection, "status"))) {
      stage = "INSPECTION_RESULT_PENDING";
      action = "WAIT_FOR_RESULT";
      targetKey = "applicationId";
      target = applicationId;
      rank = 1;
    } else if (inspection != null && "IN_PROGRESS".equals(str(inspection, "status"))) {
      stage = "INSPECTION_IN_PROGRESS";
      action = "VIEW_INSPECTION_PROGRESS";
      targetKey = "applicationId";
      target = applicationId;
      rank = 1;
    } else if (inspection != null && "SCHEDULED".equals(str(inspection, "status"))) {
      stage = "VISIT_SCHEDULED";
      action = "VIEW_VISIT_SCHEDULE";
      targetKey = "applicationId";
      target = applicationId;
      rank = 2;
    } else if (app != null && "DRAFT".equals(str(app, "status"))) {
      stage = "INSPECTION_DRAFT";
      action = "UPLOAD_INSPECTION_PHOTO";
      targetKey = "applicationId";
      target = applicationId;
      rank = 0;
    } else if (app != null) {
      stage = "INSPECTION_RECEIVED";
      action = "WAIT_FOR_VISIT_SCHEDULE";
      targetKey = "applicationId";
      target = applicationId;
      rank = 1;
    } else if (inspectionDraft != null) {
      stage = "INSPECTION_FORM_EDITING";
      action = "CONTINUE_INSPECTION_FORM";
      targetKey = "draftId";
      target = inspectionForm;
      rank = 0;
    } else if (!"COMPLETED".equals(str(a, "status"))) {
      boolean failed = "FAILED".equals(str(a, "status"));
      stage = failed ? "RISK_ASSESSMENT_FAILED" : "RISK_ASSESSMENT_IN_PROGRESS";
      action = failed ? "VIEW_ASSESSMENT" : "WAIT_FOR_ASSESSMENT";
      targetKey = "assessmentId";
      target = assessmentId;
      rank = failed ? 3 : 1;
      closed = failed;
    } else if (guide != null) {
      Integer recorded =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM prevention_guide_items WHERE guide_id=? AND progress_recorded=TRUE",
              Integer.class,
              guideId);
      Integer incomplete =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM prevention_guide_items WHERE guide_id=? AND completed=FALSE",
              Integer.class,
              guideId);
      boolean done =
          "CONFIRMED".equals(str(guide, "resolution"))
              || (recorded != null && recorded > 0 && incomplete != null && incomplete == 0);
      stage =
          done
              ? "GUIDE_COMPLETED"
              : recorded != null && recorded > 0 ? "GUIDE_IN_PROGRESS" : "GUIDE_AVAILABLE";
      action = done ? "CONSIDER_INSPECTION" : "VIEW_GUIDE";
      targetKey = done ? "assessmentId" : "guideId";
      target = done ? assessmentId : guideId;
      rank = "GUIDE_IN_PROGRESS".equals(stage) ? 1 : 2;
    } else {
      stage = "RISK_ASSESSMENT_COMPLETED";
      action = "VIEW_ASSESSMENT";
      targetKey = "assessmentId";
      target = assessmentId;
      rank = 2;
    }
    LocalDateTime updated =
        switch (stage) {
          case "CONSTRUCTION_COMPLETED", "CONSTRUCTION_IN_PROGRESS", "CONSTRUCTION_SCHEDULED" ->
              constructionChangedAt(construction);
          case "SUPPORT_DRAFT" -> time(support, "created_at");
          case "SUPPORT_REVIEW" -> time(support, "submitted_at");
          case "SUPPORT_APPROVED", "SUPPORT_REJECTED" -> time(support, "approved_at");
          case "SUPPORT_FORM_EDITING" -> time(supportDraft, "updated_at");
          case "INSPECTION_RESULT_PUBLISHED" -> time(published, "published_at");
          case "INSPECTION_RESULT_PENDING", "INSPECTION_IN_PROGRESS", "VISIT_SCHEDULED" ->
              inspectionChangedAt(inspection);
          case "INSPECTION_RECEIVED" -> receivedAt(app);
          case "INSPECTION_DRAFT" -> time(app, "created_at");
          case "INSPECTION_FORM_EDITING" -> time(inspectionDraft, "updated_at");
          case "GUIDE_COMPLETED" ->
              firstTime(guide, "completed_at", "progress_updated_at", "generated_at");
          case "GUIDE_IN_PROGRESS" -> firstTime(guide, "progress_updated_at", "generated_at");
          case "GUIDE_AVAILABLE" -> time(guide, "generated_at");
          default -> time(a, "created_at");
        };
    boolean supportStage = supportId != null || "SUPPORT_FORM_EDITING".equals(stage);
    String applicationType =
        supportStage
            ? "SUPPORT"
            : (app != null || inspectionDraft != null ? "INSPECTION" : "RISK_ASSESSMENT");
    String receiptNumber =
        app == null || stage.endsWith("FORM_EDITING")
            ? null
            : String.format(Locale.ROOT, "%06d", applicationId);
    return new Ranked(
        new Flow(
            assessmentId,
            applicationType,
            receiptNumber,
            homeTitle(stage),
            str(a, "address"),
            str(a, "detail_address"),
            str(a, "risk_level"),
            stage,
            new Action(action, Map.of(targetKey, target)),
            new RelatedIds(
                guideId,
                applicationId,
                inspectionId,
                supportId,
                constructionId,
                inspectionForm,
                supportForm),
            stageHistory(a, guide, app, inspection, published, support, construction),
            closed,
            updated == null ? null : updated.atOffset(java.time.ZoneOffset.ofHours(9))),
        rank);
  }

  private String homeTitle(String stage) {
    return switch (stage) {
      case "RISK_ASSESSMENT_IN_PROGRESS" -> "침수 취약도를 분석하고 있어요";
      case "RISK_ASSESSMENT_FAILED" -> "침수 취약도 분석을 완료하지 못했어요";
      case "RISK_ASSESSMENT_COMPLETED" -> "침수 취약도 분석이 완료됐어요";
      case "GUIDE_AVAILABLE" -> "침수 예방 가이드가 준비됐어요";
      case "GUIDE_IN_PROGRESS" -> "침수 예방 가이드를 확인하고 있어요";
      case "GUIDE_COMPLETED" -> "침수 예방 가이드 확인을 완료했어요";
      case "INSPECTION_FORM_EDITING" -> "현장점검 신청서를 작성하고 있어요";
      case "INSPECTION_DRAFT" -> "현장점검 신청 사진을 등록해 주세요";
      case "INSPECTION_RECEIVED" -> "현장점검 신청이 접수됐어요";
      case "VISIT_SCHEDULED" -> "현장점검 일정이 확정됐어요";
      case "INSPECTION_IN_PROGRESS" -> "현장점검이 진행 중이에요";
      case "INSPECTION_RESULT_PENDING" -> "현장점검이 끝나 결과를 준비하고 있어요";
      case "INSPECTION_RESULT_PUBLISHED" -> "현장점검 결과가 공개됐어요";
      case "SUPPORT_FORM_EDITING" -> "지원 신청서를 작성하고 있어요";
      case "SUPPORT_DRAFT" -> "임대인 동의서를 등록해 주세요";
      case "SUPPORT_REVIEW" -> "지원 신청을 검토하고 있어요";
      case "SUPPORT_APPROVED" -> "지원 신청이 승인됐어요";
      case "SUPPORT_REJECTED" -> "지원 신청이 반려됐어요";
      case "CONSTRUCTION_SCHEDULED" -> "시공 일정이 확정됐어요";
      case "CONSTRUCTION_IN_PROGRESS" -> "시공이 진행 중이에요";
      case "CONSTRUCTION_COMPLETED" -> "시공이 완료됐어요";
      default -> throw new IllegalStateException("Unexpected resident progress stage: " + stage);
    };
  }

  private List<HistoryEvent> stageHistory(
      Map<String, Object> assessment,
      Map<String, Object> guide,
      Map<String, Object> app,
      Map<String, Object> inspection,
      Map<String, Object> published,
      Map<String, Object> support,
      Map<String, Object> construction) {
    List<HistoryEvent> events = new ArrayList<>();
    if ("COMPLETED".equals(str(assessment, "status")))
      addEvent(events, "RISK_ASSESSMENT_COMPLETED", time(assessment, "created_at"));
    addEvent(events, "GUIDE_GENERATED", time(guide, "generated_at"));
    addEvent(events, "GUIDE_COMPLETED", time(guide, "completed_at"));
    if (app != null && !"DRAFT".equals(str(app, "status"))) {
      addEvent(events, "INSPECTION_RECEIVED", receivedAt(app));
    }
    if (inspection != null) {
      for (var row :
          jdbc.queryForList(
              "SELECT status,changed_at FROM inspection_status_histories WHERE inspection_id=? ORDER BY changed_at ASC,inspection_status_history_id ASC",
              num(inspection, "inspection_id")))
        addEvent(events, "INSPECTION_" + str(row, "status"), time(row, "changed_at"));
    }
    addEvent(events, "INSPECTION_RESULT_PUBLISHED", time(published, "published_at"));
    if (support != null && !"DRAFT".equals(str(support, "status"))) {
      addEvent(events, "SUPPORT_SUBMITTED", time(support, "submitted_at"));
      for (var row :
          jdbc.queryForList(
              "SELECT status,changed_at FROM support_application_status_histories WHERE support_application_id=? ORDER BY changed_at ASC,support_application_status_history_id ASC",
              num(support, "support_application_id")))
        addEvent(events, "SUPPORT_" + str(row, "status"), time(row, "changed_at"));
    }
    if (construction != null) {
      for (var row :
          jdbc.queryForList(
              "SELECT status,changed_at FROM construction_status_histories WHERE construction_id=? ORDER BY changed_at ASC,construction_status_history_id ASC",
              num(construction, "construction_id")))
        addEvent(events, "CONSTRUCTION_" + str(row, "status"), time(row, "changed_at"));
    }
    events.sort(Comparator.comparing(HistoryEvent::occurredAt).thenComparing(HistoryEvent::stage));
    return List.copyOf(events);
  }

  private LocalDateTime receivedAt(Map<String, Object> app) {
    var received = time(app, "submitted_at");
    if (received != null || app == null) return received;
    var legacy =
        first(
            "SELECT created_at FROM application_status_histories WHERE application_id=? AND status='RECEIVED' ORDER BY created_at ASC",
            num(app, "application_id"));
    return time(legacy, "created_at");
  }

  private LocalDateTime inspectionChangedAt(Map<String, Object> inspection) {
    if (inspection == null) return null;
    var row =
        first(
            "SELECT changed_at FROM inspection_status_histories WHERE inspection_id=? AND status=? ORDER BY changed_at DESC,inspection_status_history_id DESC",
            num(inspection, "inspection_id"),
            str(inspection, "status"));
    return time(row, "changed_at");
  }

  private LocalDateTime constructionChangedAt(Map<String, Object> construction) {
    if (construction == null) return null;
    var row =
        first(
            "SELECT changed_at FROM construction_status_histories WHERE construction_id=? AND status=? ORDER BY changed_at DESC,construction_status_history_id DESC",
            num(construction, "construction_id"),
            str(construction, "status"));
    var changed = time(row, "changed_at");
    return changed == null && "COMPLETED".equals(str(construction, "status"))
        ? time(construction, "completed_at")
        : changed;
  }

  private void addEvent(List<HistoryEvent> events, String stage, LocalDateTime at) {
    if (at != null)
      events.add(new HistoryEvent(stage, at.atOffset(java.time.ZoneOffset.ofHours(9))));
  }

  private Map<String, Object> first(String sql, Object... args) {
    var rows = jdbc.queryForList(sql, args);
    return rows.isEmpty() ? null : rows.get(0);
  }

  private Long num(Map<String, Object> row, String key) {
    return row == null || row.get(key) == null ? null : ((Number) row.get(key)).longValue();
  }

  private String str(Map<String, Object> row, String key) {
    return row == null || row.get(key) == null ? null : row.get(key).toString();
  }

  private LocalDateTime time(Map<String, Object> row, String key) {
    if (row == null || row.get(key) == null) return null;
    Object value = row.get(key);
    return value instanceof java.sql.Timestamp t ? t.toLocalDateTime() : (LocalDateTime) value;
  }

  private LocalDateTime firstTime(Map<String, Object> row, String... keys) {
    for (var key : keys) {
      var value = time(row, key);
      if (value != null) return value;
    }
    return null;
  }
}
