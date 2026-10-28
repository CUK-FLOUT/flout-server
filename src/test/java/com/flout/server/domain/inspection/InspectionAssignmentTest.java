package com.flout.server.domain.inspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.inspection.entity.*;
import com.flout.server.domain.inspection.repository.*;
import com.flout.server.domain.inspection.service.*;
import com.flout.server.domain.inspectionapplication.dto.request.CreateInspectionApplicationRequest;
import com.flout.server.domain.inspectionapplication.entity.*;
import com.flout.server.domain.inspectionapplication.repository.*;
import com.flout.server.domain.riskassessment.entity.*;
import com.flout.server.domain.riskassessment.repository.RiskAssessmentRepository;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:inspection-assignment;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false"
    })
@AutoConfigureMockMvc
class InspectionAssignmentTest {
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final LocalDateTime NOW = LocalDateTime.of(2030, 1, 2, 14, 0);
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper mapper;
  @Autowired UserRepository users;
  @Autowired RiskAssessmentRepository assessments;
  @Autowired InspectionApplicationRepository applications;
  @Autowired InspectionRepository inspections;
  @Autowired InspectionStatusService service;
  @Autowired InspectionAssignmentService assignmentService;
  @Autowired JwtService jwt;
  @MockitoSpyBean InspectionStatusHistoryRepository histories;
  @MockitoBean Clock inspectionClock;
  User resident;
  Long operatorId;
  Long applicationId;
  Long inspectionId;
  String token;

  // 미배정 접수 건 준비
  @BeforeEach
  void setup() {
    when(inspectionClock.getZone()).thenReturn(SEOUL);
    when(inspectionClock.instant()).thenReturn(NOW.atZone(SEOUL).toInstant());
    for (String table :
        List.of(
            "inspection_status_histories",
            "inspections",
            "application_inflow_locations",
            "application_status_histories",
            "inspection_applications",
            "prevention_guides",
            "risk_assessments",
            "refresh_tokens",
            "user_consents",
            "users")) jdbc.update("DELETE FROM " + table);
    resident = users.saveAndFlush(User.resident("resident@example.com", "hash", "주민"));
    jdbc.update(
        """
        INSERT INTO users (email, password_hash, name, role, jurisdiction_id)
        VALUES ('operator@example.com', 'hash', '김담당', 'ADMIN',
          (SELECT jurisdiction_id FROM jurisdictions WHERE name = '부천시'))
        """);
    var operator = users.findByEmail("operator@example.com").orElseThrow();
    operatorId = operator.getId();
    token = jwt.issue(operator).accessToken();
    var assessment =
        assessments.saveAndFlush(
            RiskAssessment.completedDemo(resident, "경기도 부천시 원미구 길주로 210", "101호", RiskLevel.HIGH));
    var request =
        new CreateInspectionApplicationRequest(
            assessment.getId(),
            null,
            new BigDecimal("37.4874850"),
            new BigDecimal("126.7985672"),
            "010-1234-5678",
            OccupancyType.TENANT,
            FloodExperience.NO,
            false,
            null,
            null,
            null,
            null);
    var application =
        applications.saveAndFlush(
            InspectionApplication.received(resident, assessment, request, "IA-STATUS-TEST"));
    applicationId = application.getId();
    jdbc.update(
        "UPDATE inspection_applications SET status = 'RECEIVED', internal_memo = '유지할 내부 메모' WHERE application_id = ?",
        applicationId);
    inspectionId = inspections.saveAndFlush(Inspection.received(application)).getId();
  }

  private String path() {
    return "/api/admin/inspections/" + inspectionId + "/assignment";
  }

  private Map<String, Object> request() {
    return new java.util.LinkedHashMap<>(
        Map.of(
            "visitDate", "2030-01-03",
            "visitTime", "14:00",
            "preVisitNotice", "  배수구 주변을 정리해주세요.  "));
  }

  private org.springframework.test.web.servlet.ResultActions save(Map<String, Object> body)
      throws Exception {
    return mvc.perform(
        post(path())
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(body)));
  }

  private void schedule() {
    service.change(operatorId, inspectionId, InspectionStatus.SCHEDULED);
  }

  @Test
  void savesAssignmentOnlyAfterStatusWasPersistedAndPreservesApplication() throws Exception {
    save(request())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INSPECTION_409_3"));
    schedule();
    save(request())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("INSPECTION_200_2"))
        .andExpect(
            jsonPath("$.result.receiptNo")
                .value(String.format(java.util.Locale.ROOT, "%06d", applicationId)))
        .andExpect(jsonPath("$.result.applicantName").value("주민"))
        .andExpect(jsonPath("$.result.inspectorId").value(operatorId))
        .andExpect(jsonPath("$.result.inspectorName").value("김담당"))
        .andExpect(jsonPath("$.result.visitDate").value("2030-01-03"))
        .andExpect(jsonPath("$.result.visitTime").value("14:00"))
        .andExpect(jsonPath("$.result.preVisitNotice").value("배수구 주변을 정리해주세요."))
        .andExpect(jsonPath("$.result.status").value("SCHEDULED"))
        .andExpect(jsonPath("$.result.updatedAt").value("2030-01-02T14:00:00"));
    var row = jdbc.queryForMap("SELECT * FROM inspections WHERE inspection_id = ?", inspectionId);
    assertThat(((Number) row.get("inspector_user_id")).longValue()).isEqualTo(operatorId);
    assertThat(((Timestamp) row.get("scheduled_at")).toLocalDateTime())
        .isEqualTo(LocalDateTime.of(2030, 1, 3, 14, 0));
    assertThat(histories.count()).isEqualTo(1);
    var application = applications.findById(applicationId).orElseThrow();
    assertThat(application.getInternalMemo()).isEqualTo("유지할 내부 메모");
    assertThat(application.getStatus()).isEqualTo(InspectionApplicationStatus.RECEIVED);
  }

  // 본인 배정과 배정 기록 유지
  @Test
  void ignoresOtherInspectorIdAndRecordsAuthenticatedOperatorAndAssignmentTimestamp()
      throws Exception {
    jdbc.update(
        """
        INSERT INTO users (email, password_hash, name, role, jurisdiction_id)
        VALUES ('other-inspector@example.com', 'hash', '다른담당', 'ADMIN',
          (SELECT jurisdiction_id FROM jurisdictions WHERE name = '부천시'))
        """);
    Long inspectorId = users.findByEmail("other-inspector@example.com").orElseThrow().getId();
    schedule();
    var body = request();
    body.put("inspectorId", inspectorId);
    save(body)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.inspectorId").value(operatorId))
        .andExpect(jsonPath("$.result.inspectorName").value("김담당"));
    var row = jdbc.queryForMap("SELECT * FROM inspections WHERE inspection_id = ?", inspectionId);
    assertThat(((Number) row.get("inspector_user_id")).longValue()).isEqualTo(operatorId);
    assertThat(((Number) row.get("scheduled_by_user_id")).longValue()).isEqualTo(operatorId);
    assertThat(((Timestamp) row.get("assigned_at")).toLocalDateTime()).isEqualTo(NOW);
    when(inspectionClock.instant())
        .thenReturn(LocalDateTime.of(2030, 1, 3, 14, 0).atZone(SEOUL).toInstant());
    assertThat(service.startIfDue(inspectionId)).isTrue();
    var after = jdbc.queryForMap("SELECT * FROM inspections WHERE inspection_id = ?", inspectionId);
    assertThat(after.get("scheduled_by_user_id")).isEqualTo(row.get("scheduled_by_user_id"));
    assertThat(after.get("assigned_at")).isEqualTo(row.get("assigned_at"));
    assertThat(after.get("updated_at")).isNotEqualTo(row.get("updated_at"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"IN_PROGRESS", "COMPLETED"})
  void rejectsAssignmentsOutsideScheduled(String state) throws Exception {
    jdbc.update("UPDATE inspections SET status = ? WHERE inspection_id = ?", state, inspectionId);
    save(request())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INSPECTION_409_3"));
    assertThat(inspections.findById(inspectionId).orElseThrow().getScheduledAt()).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"visitDate", "visitTime", "preVisitNotice"})
  void requiresDateTimeAndNotice(String field) throws Exception {
    schedule();
    var body = request();
    body.remove(field);
    save(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_400_2"));
    body.put(field, null);
    save(body).andExpect(status().isBadRequest());
    assertThat(inspections.findById(inspectionId).orElseThrow().getScheduledAt()).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void rejectsBlankNotice(String notice) throws Exception {
    var body = request();
    body.put("preVisitNotice", notice);
    save(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("주민 방문 전 안내를 입력해주세요."));
  }

  @Test
  void rejectsOversizedNoticeAndMalformedDateTime() throws Exception {
    var body = request();
    body.put("preVisitNotice", "가".repeat(2001));
    save(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("주민 방문 전 안내는 2000자 이하여야 합니다."));
    body = request();
    body.put("visitDate", "2030-02-30");
    save(body).andExpect(status().isBadRequest());
    body = request();
    body.put("visitTime", "25:00");
    save(body).andExpect(status().isBadRequest());
  }

  @Test
  void rejectsPastTimeUsingSeoulClockAndAcceptsCurrentTime() throws Exception {
    schedule();
    var body = request();
    body.put("visitDate", "2030-01-02");
    body.put("visitTime", "13:59");
    save(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INSPECTION_400_2"));
    body.put("visitTime", "14:00");
    save(body).andExpect(status().isOk());
    assertThat(service.startIfDue(inspectionId)).isTrue();
    save(request()).andExpect(status().isConflict());
  }

  @Test
  void ignoresSuppliedResidentIdAndUsesAuthenticatedOperator() throws Exception {
    schedule();
    var body = request();
    body.put("inspectorId", resident.getId());
    save(body)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.inspectorId").value(operatorId));
    assertThat(
            jdbc.queryForObject(
                "SELECT inspector_user_id FROM inspections WHERE inspection_id = ?",
                Long.class,
                inspectionId))
        .isEqualTo(operatorId);
  }

  // ID 오류와 점검 없음 구분
  @Test
  void checksMissingInspectionAndPositiveIds() throws Exception {
    schedule();
    for (String id : List.of("0", "abc", "9223372036854775807")) {
      mvc.perform(
              post("/api/admin/inspections/" + id + "/assignment")
                  .header("Authorization", "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(mapper.writeValueAsString(request())))
          .andExpect(status().is(id.equals("9223372036854775807") ? 404 : 400));
    }
  }

  @Test
  void preventsCrossJurisdictionWritesAndResidentAccess() throws Exception {
    schedule();
    jdbc.update(
        "UPDATE inspection_applications SET address = '서울특별시 중구 세종대로 110' WHERE application_id = ?",
        applicationId);
    save(request())
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("INSPECTION_403_1"));
    mvc.perform(
            post(path())
                .header("Authorization", "Bearer " + jwt.issue(resident).accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request())))
        .andExpect(status().isForbidden());
    mvc.perform(
            post(path())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void firstAssignmentSetsAutomaticStartTime() throws Exception {
    schedule();
    jdbc.update(
        "UPDATE inspections SET scheduled_at = ? WHERE inspection_id = ?",
        NOW.minusMinutes(1),
        inspectionId);
    save(request()).andExpect(status().isOk());
    assertThat(service.startIfDue(inspectionId)).isFalse();
    when(inspectionClock.instant())
        .thenReturn(LocalDateTime.of(2030, 1, 3, 14, 0).atZone(SEOUL).toInstant());
    assertThat(service.startIfDue(inspectionId)).isTrue();
    assertThat(inspections.findById(inspectionId).orElseThrow().getStatus())
        .isEqualTo(InspectionStatus.IN_PROGRESS);
    assertThat(histories.count()).isEqualTo(2);
  }

  @ParameterizedTest
  @ValueSource(strings = {"SCHEDULED", "IN_PROGRESS", "COMPLETED"})
  void rejectsSecondAssignmentAndPreservesOriginalValues(String state) throws Exception {
    schedule();
    save(request()).andExpect(status().isOk());
    jdbc.update("UPDATE inspections SET status = ? WHERE inspection_id = ?", state, inspectionId);
    var before =
        jdbc.queryForMap("SELECT * FROM inspections WHERE inspection_id = ?", inspectionId);
    var changed = request();
    changed.put("inspectorId", resident.getId());
    changed.put("visitDate", "2030-01-04");
    changed.put("preVisitNotice", "바뀌면 안 되는 안내");
    save(changed)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INSPECTION_409_4"))
        .andExpect(jsonPath("$.message").value("이미 배정된 점검입니다."));
    assertThat(jdbc.queryForMap("SELECT * FROM inspections WHERE inspection_id = ?", inspectionId))
        .isEqualTo(before);
    assertThat(histories.count()).isEqualTo(1);
  }

  // 동시 요청의 중복 배정 방지
  @Test
  void concurrentAssignmentsSucceedOnlyOnce() throws Exception {
    schedule();
    var gate = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      java.util.concurrent.Callable<Integer> attempt =
          () -> {
            gate.await();
            return save(request()).andReturn().getResponse().getStatus();
          };
      var first = executor.submit(attempt);
      var second = executor.submit(attempt);
      gate.countDown();
      assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
      save(request())
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("INSPECTION_409_4"));
      assertThat(histories.count()).isEqualTo(1);
    }
  }

  @Test
  void documentsAssignmentUnderInspectionManagement() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.components.schemas.SaveInspectionAssignmentRequest.properties.inspectorId")
                .doesNotExist())
        .andExpect(
            jsonPath("$.paths['/api/admin/inspections/{inspectionId}/assignment'].post.tags[0]")
                .value("운영자-현장점검 관리"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/inspections/{inspectionId}/assignment'].post.responses['409'].content['application/json'].examples.scheduledRequired.value.code")
                .value("INSPECTION_409_3"));
  }

  // 배정 저장과 자동 전환 간 경합
  @Test
  void serializesAssignmentWithAutomaticStart() throws Exception {
    schedule();
    jdbc.update(
        "UPDATE inspections SET scheduled_at = ? WHERE inspection_id = ?", NOW, inspectionId);
    var gate = new CountDownLatch(1);
    var assignment =
        new com.flout.server.domain.inspection.dto.request.SaveInspectionAssignmentRequest(
            LocalDate.of(2030, 1, 3), LocalTime.of(14, 0), "방문 안내");
    try (var executor = Executors.newFixedThreadPool(2)) {
      Future<Boolean> saveResult =
          executor.submit(
              () -> {
                gate.await();
                try {
                  assignmentService.save(operatorId, inspectionId, assignment);
                  return true;
                } catch (com.flout.server.global.exception.BaseException exception) {
                  return false;
                }
              });
      Future<Boolean> startResult =
          executor.submit(
              () -> {
                gate.await();
                return service.startIfDue(inspectionId);
              });
      gate.countDown();
      boolean saved = saveResult.get(15, TimeUnit.SECONDS);
      boolean started = startResult.get(15, TimeUnit.SECONDS);
      assertThat(saved).isNotEqualTo(started);
      var inspection = inspections.findById(inspectionId).orElseThrow();
      assertThat(inspection.getStatus())
          .isEqualTo(saved ? InspectionStatus.SCHEDULED : InspectionStatus.IN_PROGRESS);
      assertThat(inspection.getScheduledAt())
          .isEqualTo(saved ? LocalDateTime.of(2030, 1, 3, 14, 0) : NOW);
      assertThat(histories.count()).isEqualTo(saved ? 1 : 2);
    }
  }
}
