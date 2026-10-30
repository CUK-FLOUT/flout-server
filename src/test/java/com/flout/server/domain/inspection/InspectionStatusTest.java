package com.flout.server.domain.inspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import org.junit.jupiter.params.provider.CsvSource;
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
      "spring.datasource.url=jdbc:h2:mem:inspection-status;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false"
    })
@AutoConfigureMockMvc
class InspectionStatusTest {
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
  @Autowired JwtService jwt;
  @MockitoSpyBean InspectionStatusHistoryRepository histories;
  @MockitoBean Clock inspectionClock;
  User resident;
  Long operatorId;
  Long applicationId;
  Long inspectionId;
  String token;

  // 초기 접수 점검 준비
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
            assessment.getAddress(),
            assessment.getDetailAddress(),
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
    return "/api/admin/inspections/" + inspectionId + "/status";
  }

  private String body(String status) {
    return mapper.writeValueAsString(Map.of("status", status));
  }

  private void seed(String status, LocalDateTime visitAt) {
    jdbc.update(
        "UPDATE inspections SET status = ?, scheduled_at = ? WHERE inspection_id = ?",
        status,
        visitAt,
        inspectionId);
  }

  private void change(String target) throws Exception {
    mvc.perform(
            patch(path())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(target)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("INSPECTION_200_1"))
        .andExpect(
            jsonPath("$.result.receiptNo")
                .value(String.format(java.util.Locale.ROOT, "%06d", applicationId)))
        .andExpect(jsonPath("$.result.status").value(target));
  }

  // 배정 전 예정 상태 변경
  @Test
  void canScheduleWithoutAssignmentOrVisitTime() throws Exception {
    change("SCHEDULED");
    assertThat(inspections.findById(inspectionId).orElseThrow().getScheduledAt()).isNull();
    assertThat(histories.count()).isEqualTo(1);
  }

  // 상태 변경 후 신청 정보 유지
  @Test
  void recordsForwardTransitionsAndPreservesApplication() throws Exception {
    change("SCHEDULED");
    jdbc.update(
        "UPDATE inspections SET scheduled_at = ? WHERE inspection_id = ?", NOW, inspectionId);
    change("IN_PROGRESS");
    change("COMPLETED");
    var rows =
        jdbc.queryForList(
            "SELECT previous_status, status, changed_by_user_id, change_source, changed_at FROM inspection_status_histories ORDER BY inspection_status_history_id");
    assertThat(rows).hasSize(3);
    assertThat(rows)
        .extracting(row -> row.get("previous_status"))
        .containsExactly("RECEIVED", "SCHEDULED", "IN_PROGRESS");
    assertThat(rows)
        .extracting(row -> row.get("status"))
        .containsExactly("SCHEDULED", "IN_PROGRESS", "COMPLETED");
    for (var row : rows) {
      assertThat(((Number) row.get("changed_by_user_id")).longValue()).isEqualTo(operatorId);
      assertThat(row.get("change_source")).isEqualTo("MANUAL");
      assertThat(((Timestamp) row.get("changed_at")).toLocalDateTime()).isEqualTo(NOW);
    }
    var application = applications.findById(applicationId).orElseThrow();
    assertThat(application.getStatus()).isEqualTo(InspectionApplicationStatus.RECEIVED);
    assertThat(application.getInternalMemo()).isEqualTo("유지할 내부 메모");
    assertThat(application.getReceiptNo()).isEqualTo("IA-STATUS-TEST");
  }

  @Test
  void repeatedSameStateDoesNotChangeTimestampOrDuplicateHistory() throws Exception {
    change("SCHEDULED");
    var updatedAt = inspections.findById(inspectionId).orElseThrow().getUpdatedAt();
    when(inspectionClock.instant()).thenReturn(NOW.plusMinutes(1).atZone(SEOUL).toInstant());
    change("SCHEDULED");
    assertThat(histories.count()).isEqualTo(1);
    assertThat(inspections.findById(inspectionId).orElseThrow().getUpdatedAt())
        .isEqualTo(updatedAt);
    seed("COMPLETED", NOW);
    change("COMPLETED");
    assertThat(histories.count()).isEqualTo(1);
  }

  @ParameterizedTest
  @CsvSource({
    "RECEIVED,IN_PROGRESS",
    "RECEIVED,COMPLETED",
    "SCHEDULED,COMPLETED",
    "IN_PROGRESS,SCHEDULED",
    "COMPLETED,SCHEDULED",
    "COMPLETED,IN_PROGRESS"
  })
  void rejectsSkippingAndReverseTransitions(String previous, String target) throws Exception {
    seed(previous, NOW);
    mvc.perform(
            patch(path())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(target)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INSPECTION_409_1"));
    assertThat(histories.count()).isZero();
    assertThat(inspections.findById(inspectionId).orElseThrow().getStatus().name())
        .isEqualTo(previous);
  }

  // 방문 시각 전 수동 진행 차단
  @Test
  void cannotStartBeforeVisitTimeOrWithoutIt() throws Exception {
    for (LocalDateTime visitAt : new LocalDateTime[] {null, NOW.plusSeconds(1)}) {
      seed("SCHEDULED", visitAt);
      mvc.perform(
              patch(path())
                  .header("Authorization", "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body("IN_PROGRESS")))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("INSPECTION_409_2"));
    }
    assertThat(histories.count()).isZero();
    seed("SCHEDULED", NOW);
    change("IN_PROGRESS");
  }

  // 방문 시각 이후 자동 전환과 이력 저장
  @Test
  void automaticallyStartsAtVisitTimeAndCatchesUpOverdueVisits() {
    var scheduler = new InspectionAutoStartScheduler(inspections, service, inspectionClock);
    seed("SCHEDULED", NOW.plusSeconds(1));
    scheduler.startDueInspections();
    assertThat(histories.count()).isZero();
    when(inspectionClock.instant()).thenReturn(NOW.plusSeconds(1).atZone(SEOUL).toInstant());
    scheduler.startDueInspections();
    scheduler.startDueInspections();
    assertThat(inspections.findById(inspectionId).orElseThrow().getStatus())
        .isEqualTo(InspectionStatus.IN_PROGRESS);
    assertThat(histories.count()).isEqualTo(1);
    var history = histories.findAll().getFirst();
    assertThat(history.getChangeSource()).isEqualTo(InspectionStatusChangeSource.AUTO);
    assertThat(history.getChangedBy()).isNull();
    assertThat(history.getChangedAt()).isEqualTo(NOW.plusSeconds(1));
    histories.deleteAll();
    seed("SCHEDULED", NOW.minusDays(1));
    scheduler.startDueInspections();
    assertThat(histories.count()).isEqualTo(1);
    assertThat(inspections.findById(inspectionId).orElseThrow().getStatus())
        .isEqualTo(InspectionStatus.IN_PROGRESS);
  }

  // 자동 전환 대상 제한
  @Test
  void automaticStartSkipsMissingTimeAndOtherStates() {
    var scheduler = new InspectionAutoStartScheduler(inspections, service, inspectionClock);
    seed("SCHEDULED", null);
    scheduler.startDueInspections();
    for (String state : List.of("RECEIVED", "IN_PROGRESS", "COMPLETED")) {
      seed(state, NOW.minusDays(1));
      scheduler.startDueInspections();
      assertThat(inspections.findById(inspectionId).orElseThrow().getStatus().name())
          .isEqualTo(state);
    }
    assertThat(histories.count()).isZero();
  }

  // 수동·자동 전환 간 중복 이력 방지
  @Test
  void manualAndAutomaticStartRaceCreatesOneHistory() throws Exception {
    seed("SCHEDULED", NOW);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var manual =
          executor.submit(
              () -> {
                start.await();
                return mvc.perform(
                        patch(path())
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("IN_PROGRESS")))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              });
      var automatic =
          executor.submit(
              () -> {
                start.await();
                return service.startIfDue(inspectionId);
              });
      start.countDown();
      assertThat(manual.get(20, TimeUnit.SECONDS)).isEqualTo(200);
      automatic.get(20, TimeUnit.SECONDS);
    }
    assertThat(histories.count()).isEqualTo(1);
    assertThat(inspections.findById(inspectionId).orElseThrow().getStatus())
        .isEqualTo(InspectionStatus.IN_PROGRESS);
  }

  @Test
  void historyFailureRollsBackAndAutomaticStartRetries() {
    seed("SCHEDULED", NOW);
    var scheduler = new InspectionAutoStartScheduler(inspections, service, inspectionClock);
    doThrow(new IllegalStateException("simulated history failure"))
        .when(histories)
        .saveAndFlush(any());
    scheduler.startDueInspections();
    assertThat(inspections.findById(inspectionId).orElseThrow().getStatus())
        .isEqualTo(InspectionStatus.SCHEDULED);
    assertThat(histories.count()).isZero();
    reset(histories);
    scheduler.startDueInspections();
    assertThat(inspections.findById(inspectionId).orElseThrow().getStatus())
        .isEqualTo(InspectionStatus.IN_PROGRESS);
    assertThat(histories.count()).isEqualTo(1);
  }

  @Test
  void manualHistoryFailureRollsBackStatus() throws Exception {
    doThrow(new IllegalStateException("simulated history failure"))
        .when(histories)
        .saveAndFlush(any());
    mvc.perform(
            patch(path())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("SCHEDULED")))
        .andExpect(status().isInternalServerError());
    assertThat(inspections.findById(inspectionId).orElseThrow().getStatus())
        .isEqualTo(InspectionStatus.RECEIVED);
    assertThat(histories.count()).isZero();
  }

  // 폐기한 상태와 잘못된 ID 거절
  @Test
  void validatesRequestAndInspectionId() throws Exception {
    for (String invalid :
        List.of(
            "{}",
            "{\"status\":null}",
            body("RECEIVED"),
            body("WAITING"),
            body("SUBMITTED"),
            body("UNKNOWN"),
            body("scheduled"),
            "{")) {
      mvc.perform(
              patch(path())
                  .header("Authorization", "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(invalid))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("COMMON_400_2"));
    }
    mvc.perform(
            patch(path())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
    for (String id : List.of("0", "-1", "invalid")) {
      mvc.perform(
              patch("/api/admin/inspections/" + id + "/status")
                  .header("Authorization", "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body("SCHEDULED")))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("COMMON_400_3"));
    }
    mvc.perform(
            patch("/api/admin/inspections/" + Long.MAX_VALUE + "/status")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("SCHEDULED")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INSPECTION_404_1"));
  }

  @Test
  void requiresAdminAuthentication() throws Exception {
    mvc.perform(patch(path()).contentType(MediaType.APPLICATION_JSON).content(body("SCHEDULED")))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            patch(path())
                .header("Authorization", "Bearer invalid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("SCHEDULED")))
        .andExpect(status().isUnauthorized());
    String residentToken = jwt.issue(resident).accessToken();
    mvc.perform(
            patch(path())
                .header("Authorization", "Bearer " + residentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("SCHEDULED")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("COMMON_403_1"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"서울특별시 동작구 흑석로 84", "부천시민로 1", "서울특별시 부천시 길주로 210"})
  void rejectsOutsideJurisdiction(String address) throws Exception {
    jdbc.update(
        "UPDATE inspection_applications SET address = ? WHERE application_id = ?",
        address,
        applicationId);
    mvc.perform(
            patch(path())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("SCHEDULED")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("INSPECTION_403_1"));
    assertThat(histories.count()).isZero();
  }

  @Test
  void rejectsUnassignedJurisdiction() throws Exception {
    jdbc.update("UPDATE users SET jurisdiction_id = NULL WHERE user_id = ?", operatorId);
    mvc.perform(
            patch(path())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("SCHEDULED")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("INSPECTION_403_1"));
  }

  @Test
  void swaggerDocumentsStatusEndpointAndTimeRestriction() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.paths['/api/admin/inspections/{inspectionId}/status'].patch.tags[0]")
                .value("운영자-현장점검 관리"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/inspections/{inspectionId}/status'].patch.responses['404'].description")
                .value("점검을 찾을 수 없습니다."))
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/inspections/{inspectionId}/status'].patch.responses['409'].content['application/json'].examples.visitTimeNotReached.value.code")
                .value("INSPECTION_409_2"));
  }
}
