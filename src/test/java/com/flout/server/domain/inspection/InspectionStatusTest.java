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
import java.time.*;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
            null,
            "김주민");
    var application =
        applications.saveAndFlush(
            InspectionApplication.received(resident, assessment, request, "IA-STATUS-TEST"));
    applicationId = application.getId();
    jdbc.update(
        "UPDATE inspection_applications SET status = 'RECEIVED', internal_memo = '유지할 내부 메모' WHERE application_id = ?",
        applicationId);
    inspectionId = inspections.saveAndFlush(Inspection.received(application)).getId();
  }

  private void seed(String status, LocalDateTime visitAt) {
    jdbc.update(
        "UPDATE inspections SET status = ?, scheduled_at = ? WHERE inspection_id = ?",
        status,
        visitAt,
        inspectionId);
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

  /** 수동 상태 변경 API와 Swagger 경로 제거 검증 */
  @Test
  void statusEndpointIsRemoved() throws Exception {
    mvc.perform(
            patch("/api/admin/inspections/" + inspectionId + "/status")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"COMPLETED\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.paths['/api/admin/inspections/{inspectionId}/status']").doesNotExist());
  }
}
