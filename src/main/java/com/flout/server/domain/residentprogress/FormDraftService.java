package com.flout.server.domain.residentprogress;

import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.exception.BaseException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class FormDraftService {
  private final JdbcTemplate jdbc;
  private final com.flout.server.domain.supportapplication.service.SupportResultPublicationValidator
      publicationValidator;
  private final UserRepository users;
  private final ObjectMapper mapper;
  private final org.springframework.transaction.PlatformTransactionManager transactionManager;
  private final com.flout.server.domain.inspectionapplication.repository
          .InspectionApplicationRepository
      applications;
  private final com.flout.server.domain.inspectionapplication.repository
          .ApplicationInflowLocationRepository
      inflowLocations;

  @io.swagger.v3.oas.annotations.media.Schema(
      description = "입력 중인 폼 전체를 저장합니다. 최초 저장의 version은 null이고 갱신할 때 마지막 응답의 version을 사용합니다.")
  public record SaveRequest(
      @io.swagger.v3.oas.annotations.media.Schema(description = "현장점검 폼의 본인 취약도 분석 ID")
          Long assessmentId,
      @io.swagger.v3.oas.annotations.media.Schema(description = "지원 폼의 본인 현장점검 신청 ID")
          Long applicationId,
      @io.swagger.v3.oas.annotations.media.Schema(description = "동시 수정 방지 버전. 최초 저장 시 null")
          Long version,
      @io.swagger.v3.oas.annotations.media.Schema(description = "신청서 입력 필드. 입력하지 않은 값은 생략 또는 null")
          JsonNode fields) {}

  public record Draft(
      String draftId,
      Long assessmentId,
      Long applicationId,
      String formState,
      Long supportApplicationId,
      long version,
      JsonNode fields,
      java.time.OffsetDateTime updatedAt) {}

  public void resident(Long userId) {
    var user =
        users.findById(userId).orElseThrow(() -> new BaseException(FormDraftErrorCode.NOT_FOUND));
    if (user.getRole() != UserRole.RESIDENT) throw new BaseException(FormDraftErrorCode.NOT_FOUND);
  }

  /** Called inside the creation transaction; the row lock serializes retries. */
  public <T> T priorSubmission(
      Long userId, String type, UUID id, Long source, Object request, Class<T> responseType) {
    if (id == null) return null;
    one(userId, type, id.toString(), true);
    var rows =
        jdbc.queryForList("SELECT * FROM resident_form_drafts WHERE draft_id=?", id.toString());
    var row = rows.get(0);
    if (((Number) row.get("source_id")).longValue() != source)
      throw new BaseException(FormDraftErrorCode.CONFLICT);
    if (!row.get("form_state").equals("EDITING")) {
      if (!Objects.equals(row.get("submission_hash"), hash(request))
          || row.get("submission_response") == null)
        throw new BaseException(FormDraftErrorCode.CONFLICT);
      return restoreResponse((String) row.get("submission_response"), responseType);
    }
    return null;
  }

  /** Restores only a committed, owned submission; an unsent form never bypasses validation. */
  @Transactional(readOnly = true)
  public <T> T recoverExpiredSubmission(
      Long userId, String type, UUID id, Long source, Object request, Class<T> responseType) {
    var user = users.findById(userId).orElse(null);
    if (user == null || user.getRole() != UserRole.RESIDENT) return null;
    var rows =
        jdbc.queryForList(
            "SELECT source_id,form_state,submission_hash,submission_response FROM resident_form_drafts WHERE draft_id=? AND user_id=? AND draft_type=?",
            id.toString(),
            userId,
            type);
    if (rows.isEmpty()) return null;
    var row = rows.get(0);
    if (!("LINKED".equals(row.get("form_state")) || "COMPLETED".equals(row.get("form_state")))
        || row.get("submission_response") == null) return null;
    if (!Objects.equals(((Number) row.get("source_id")).longValue(), source)
        || !Objects.equals(row.get("submission_hash"), hash(request)))
      throw new BaseException(FormDraftErrorCode.CONFLICT);
    return restoreResponse((String) row.get("submission_response"), responseType);
  }

  private <T> T restoreResponse(String json, Class<T> responseType) {
    T response = mapper.readValue(json, responseType);
    if (response
        instanceof
        com.flout.server.domain.supportapplication.dto.response.CreateSupportApplicationResponse
            support) {
      var offset = java.time.ZoneOffset.ofHours(9);
      response =
          responseType.cast(
              new com.flout.server.domain.supportapplication.dto.response
                  .CreateSupportApplicationResponse(
                  support.supportApplicationId(),
                  support.applicationId(),
                  support.programId(),
                  support.status(),
                  support.submittedAt() == null
                      ? null
                      : support.submittedAt().withOffsetSameInstant(offset),
                  support.receiptNumber(),
                  support.applicantName(),
                  support.programName(),
                  support.createdAt() == null
                      ? null
                      : support.createdAt().withOffsetSameInstant(offset)));
    }
    return response;
  }

  public void link(
      Long userId,
      String type,
      UUID id,
      Long source,
      Long linkedId,
      Object request,
      Object response,
      boolean completed) {
    if (id == null) return;
    int count =
        jdbc.update(
            "UPDATE resident_form_drafts SET form_state=?,linked_id=?,submission_hash=?,submission_response=?,updated_at=CURRENT_TIMESTAMP WHERE draft_id=? AND user_id=? AND draft_type=? AND source_id=? AND form_state='EDITING'",
            completed ? "COMPLETED" : "LINKED",
            linkedId,
            hash(request),
            mapper.writeValueAsString(response),
            id.toString(),
            userId,
            type,
            source);
    if (count != 1) throw new BaseException(FormDraftErrorCode.CONFLICT);
  }

  public void complete(String type, Long linkedId) {
    jdbc.update(
        "UPDATE resident_form_drafts SET form_state='COMPLETED',updated_at=CURRENT_TIMESTAMP WHERE draft_type=? AND linked_id=? AND form_state='LINKED'",
        type,
        linkedId);
  }

  /** Best-effort association for legacy clients; never change the outcome of their submission. */
  public void scheduleUniqueLink(
      Long userId, String type, Long source, Long linkedId, Object request, Object response) {
    if (!org.springframework.transaction.support.TransactionSynchronizationManager
        .isSynchronizationActive()) return;
    org.springframework.transaction.support.TransactionSynchronizationManager
        .registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
              @Override
              public void afterCommit() {
                try {
                  var tx =
                      new org.springframework.transaction.support.TransactionTemplate(
                          transactionManager);
                  tx.setPropagationBehavior(
                      org.springframework.transaction.TransactionDefinition
                          .PROPAGATION_REQUIRES_NEW);
                  tx.setIsolationLevel(
                      org.springframework.transaction.TransactionDefinition
                          .ISOLATION_READ_COMMITTED);
                  tx.executeWithoutResult(
                      status -> associateUnique(userId, type, source, linkedId, request, response));
                } catch (RuntimeException ex) {
                  org.slf4j.LoggerFactory.getLogger(FormDraftService.class)
                      .warn(
                          "Unable to associate an unambiguous form draft after submission: {}",
                          ex.getClass().getSimpleName());
                }
              }
            });
  }

  private void associateUnique(
      Long userId, String type, Long source, Long linkedId, Object request, Object response) {
    String table = type.equals("INSPECTION") ? "risk_assessments" : "inspection_applications";
    String key = type.equals("INSPECTION") ? "assessment_id" : "application_id";
    String owner = type.equals("INSPECTION") ? "user_id" : "applicant_user_id";
    if (jdbc.queryForList(
            "SELECT "
                + key
                + " FROM "
                + table
                + " WHERE "
                + key
                + "=? AND "
                + owner
                + "=? FOR UPDATE",
            source,
            userId)
        .isEmpty()) return;
    String actualTable =
        type.equals("INSPECTION") ? "inspection_applications" : "support_applications";
    String actualKey = type.equals("INSPECTION") ? "application_id" : "support_application_id";
    var actual =
        jdbc.queryForList(
            "SELECT status FROM " + actualTable + " WHERE " + actualKey + "=? FOR UPDATE",
            linkedId);
    if (actual.isEmpty()) return;
    var candidates =
        jdbc.queryForList(
            "SELECT draft_id,form_state,fields_json FROM resident_form_drafts WHERE user_id=? AND draft_type=? AND source_id=? AND form_state IN ('EDITING','LINKED') ORDER BY draft_id FOR UPDATE",
            userId,
            type,
            source);
    if (candidates.size() != 1 || !"EDITING".equals(candidates.get(0).get("form_state"))) return;
    var candidate = candidates.get(0);
    JsonNode stored = mapper.readTree((String) candidate.get("fields_json"));
    JsonNode submitted = mapper.valueToTree(request);
    var fields = stored.properties().iterator();
    while (fields.hasNext()) {
      var field = fields.next();
      if (field.getValue().isNull()) continue;
      var submittedValue = submitted.get(field.getKey());
      if (submittedValue == null) return;
      if (submittedValue.isNumber() && field.getValue().isNumber()) {
        if (submittedValue.decimalValue().compareTo(field.getValue().decimalValue()) != 0) return;
      } else if (!submittedValue.equals(field.getValue())) return;
    }
    UUID id = UUID.fromString((String) candidate.get("draft_id"));
    link(userId, type, id, source, linkedId, request, response, false);
    if (type.equals("INSPECTION")) {
      var application = applications.findById(linkedId).orElseThrow();
      syncInspection(application, inflowLocations.findLocationsByApplicationId(linkedId));
    }
    if (!"DRAFT".equals(actual.get(0).get("status").toString())) complete(type, linkedId);
  }

  /** Synchronize linked forms with the authoritative inspection application after a patch. */
  public void syncInspection(
      com.flout.server.domain.inspectionapplication.entity.InspectionApplication app,
      java.util.List<com.flout.server.domain.inspectionapplication.entity.InflowLocation>
          locations) {
    var rows =
        jdbc.queryForList(
            "SELECT draft_id,fields_json FROM resident_form_drafts WHERE draft_type='INSPECTION' AND linked_id=? AND form_state='LINKED' FOR UPDATE",
            app.getId());
    for (var row : rows) {
      var fields =
          (tools.jackson.databind.node.ObjectNode) mapper.readTree((String) row.get("fields_json"));
      fields.put("assessmentId", app.getAssessment().getId());
      if (app.getGuideId() == null) fields.putNull("guideId");
      else fields.put("guideId", app.getGuideId());
      fields.put("applicantName", app.getApplicantName());
      fields.put("address", app.getAddress());
      fields.put("detailAddress", app.getDetailAddress());
      fields.put("latitude", app.getLatitude());
      fields.put("longitude", app.getLongitude());
      fields.put("contact", app.getContact());
      fields.put("occupancyType", app.getOccupancyType().name());
      fields.put("floodExperience", app.getFloodExperience().name());
      fields.put("existingFacility", app.isExistingFacility());
      fields.put("requestReason", app.getRequestReason());
      fields.put(
          "preferredVisitDate",
          app.getPreferredVisitDate() == null ? null : app.getPreferredVisitDate().toString());
      fields.put(
          "preferredTimeSlot",
          app.getPreferredTimeSlot() == null ? null : app.getPreferredTimeSlot().name());
      fields.set("inflowLocations", mapper.valueToTree(locations));
      jdbc.update(
          "UPDATE resident_form_drafts SET fields_json=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE draft_id=?",
          mapper.writeValueAsString(fields),
          row.get("draft_id"));
    }
  }

  private String hash(Object request) {
    try {
      byte[] bytes = mapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8);
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Serialize inspection creation with new form creation for the same assessment. */
  public void lockInspectionSource(Long assessmentId, Long userId) {
    if (jdbc.queryForList(
            "SELECT assessment_id FROM risk_assessments WHERE assessment_id=? AND user_id=? FOR UPDATE",
            assessmentId,
            userId)
        .isEmpty()) throw new BaseException(FormDraftErrorCode.NOT_FOUND);
  }

  /** Serialize support submission with form saves using the same lock order. */
  public void lockSupportSource(Long applicationId) {
    jdbc.queryForList(
        "SELECT application_id FROM inspection_applications WHERE application_id=? FOR UPDATE",
        applicationId);
  }

  @Transactional
  public Draft save(Long userId, String type, UUID id, SaveRequest request) {
    resident(userId);
    Long source = type.equals("INSPECTION") ? request.assessmentId() : request.applicationId();
    if (source == null || source <= 0 || request.fields() == null || !request.fields().isObject())
      throw new BaseException(FormDraftErrorCode.INVALID);
    if ((type.equals("INSPECTION") && request.applicationId() != null)
        || (type.equals("SUPPORT") && request.assessmentId() != null))
      throw new BaseException(FormDraftErrorCode.INVALID);
    String table = type.equals("INSPECTION") ? "risk_assessments" : "inspection_applications";
    String key = type.equals("INSPECTION") ? "assessment_id" : "application_id";
    String owner = type.equals("INSPECTION") ? "user_id" : "applicant_user_id";
    var owned =
        jdbc.queryForList(
            "SELECT "
                + key
                + " FROM "
                + table
                + " WHERE "
                + key
                + "=? AND "
                + owner
                + "=? FOR UPDATE",
            source,
            userId);
    if (owned.isEmpty()) throw new BaseException(FormDraftErrorCode.NOT_FOUND);
    String json = mapper.writeValueAsString(request.fields());
    if (json.length() > 16000) throw new BaseException(FormDraftErrorCode.INVALID);
    var rows =
        jdbc.queryForList(
            "SELECT * FROM resident_form_drafts WHERE draft_id=? FOR UPDATE", id.toString());
    if (rows.isEmpty()) {
      if (request.version() != null) throw new BaseException(FormDraftErrorCode.CONFLICT);
      if (type.equals("INSPECTION")) {
        var existing =
            jdbc.queryForList(
                "SELECT status FROM inspection_applications WHERE assessment_id=? FOR UPDATE",
                source);
        if (!existing.isEmpty()) {
          throw new BaseException(
              "DRAFT".equals(existing.get(0).get("status").toString())
                  ? FormDraftErrorCode.INSPECTION_DRAFT_EXISTS
                  : FormDraftErrorCode.INSPECTION_EXISTS);
        }
      } else {
        var existing =
            jdbc.queryForList(
                "SELECT status FROM support_applications WHERE application_id=? FOR UPDATE",
                source);
        if (!existing.isEmpty()) {
          throw new BaseException(
              "DRAFT".equals(existing.get(0).get("status").toString())
                  ? FormDraftErrorCode.SUPPORT_DRAFT_EXISTS
                  : FormDraftErrorCode.SUPPORT_EXISTS);
        }
        publicationValidator.requirePublished(source);
      }
      if (!jdbc.queryForList(
              "SELECT draft_id FROM resident_form_drafts WHERE user_id=? AND draft_type=? AND source_id=? AND form_state='EDITING' FOR UPDATE",
              userId,
              type,
              source)
          .isEmpty()) {
        throw new BaseException(FormDraftErrorCode.EDITING_EXISTS);
      }
      try {
        jdbc.update(
            "INSERT INTO resident_form_drafts(draft_id,user_id,draft_type,source_id,form_state,fields_json,version,created_at,updated_at) VALUES(?,?,?,?,'EDITING',?,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
            id.toString(),
            userId,
            type,
            source,
            json);
      } catch (DataIntegrityViolationException e) {
        throw new BaseException(FormDraftErrorCode.CONFLICT);
      }
    } else {
      var row = rows.get(0);
      if (((Number) row.get("user_id")).longValue() != userId
          || !row.get("draft_type").equals(type))
        throw new BaseException(FormDraftErrorCode.NOT_FOUND);
      if (request.version() == null
          && ((Number) row.get("source_id")).longValue() == source
          && "EDITING".equals(row.get("form_state"))
          && ((Number) row.get("version")).longValue() == 1
          && Objects.equals(row.get("fields_json"), json))
        return one(userId, type, id.toString(), false);
      if (((Number) row.get("source_id")).longValue() != source
          || !row.get("form_state").equals("EDITING")
          || request.version() == null
          || ((Number) row.get("version")).longValue() != request.version())
        throw new BaseException(FormDraftErrorCode.CONFLICT);
      jdbc.update(
          "UPDATE resident_form_drafts SET fields_json=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE draft_id=?",
          json,
          id.toString());
    }
    return one(userId, type, id.toString(), false);
  }

  @Transactional(readOnly = true)
  public List<Draft> list(Long userId, String type) {
    resident(userId);
    var result =
        jdbc.query(
            """
        SELECT d.* FROM resident_form_drafts d
        WHERE d.user_id=? AND d.draft_type=? AND d.form_state <> 'COMPLETED'
        ORDER BY d.updated_at DESC,d.draft_id DESC
        """,
            (rs, n) -> map(rs),
            userId,
            type);
    boolean inspection = type.equals("INSPECTION");
    var existing =
        new HashSet<>(
            jdbc.queryForList(
                inspection
                    ? "SELECT assessment_id FROM inspection_applications WHERE applicant_user_id=?"
                    : "SELECT application_id FROM support_applications WHERE applicant_user_id=?",
                Long.class,
                userId));
    var seen = new HashSet<Long>();
    return result.stream()
        .filter(
            draft ->
                !("EDITING".equals(draft.formState())
                    && existing.contains(
                        inspection ? draft.assessmentId() : draft.applicationId())))
        .filter(draft -> seen.add(inspection ? draft.assessmentId() : draft.applicationId()))
        .toList();
  }

  private Draft one(Long userId, String type, String id, boolean lock) {
    return jdbc
        .query(
            "SELECT * FROM resident_form_drafts WHERE draft_id=? AND user_id=? AND draft_type=?"
                + (lock ? " FOR UPDATE" : ""),
            (rs, n) -> map(rs),
            id,
            userId,
            type)
        .stream()
        .findFirst()
        .orElseThrow(() -> new BaseException(FormDraftErrorCode.NOT_FOUND));
  }

  private Draft map(java.sql.ResultSet rs) throws java.sql.SQLException {
    String type = rs.getString("draft_type");
    Long linked = (Long) rs.getObject("linked_id");
    return new Draft(
        rs.getString("draft_id"),
        type.equals("INSPECTION") ? rs.getLong("source_id") : null,
        type.equals("SUPPORT") ? Long.valueOf(rs.getLong("source_id")) : linked,
        rs.getString("form_state"),
        type.equals("SUPPORT") ? linked : null,
        rs.getLong("version"),
        mapper.readTree(rs.getString("fields_json")),
        rs.getTimestamp("updated_at").toLocalDateTime().atOffset(java.time.ZoneOffset.ofHours(9)));
  }
}
