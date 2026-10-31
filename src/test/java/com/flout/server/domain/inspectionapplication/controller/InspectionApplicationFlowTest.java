package com.flout.server.domain.inspectionapplication.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.inspection.repository.InspectionRepository;
import com.flout.server.domain.inspectionapplication.service.ReceiptNumberGenerator;
import com.flout.server.domain.riskassessment.entity.*;
import com.flout.server.domain.riskassessment.repository.RiskAssessmentRepository;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:application-flow;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false"
    })
@AutoConfigureMockMvc
class InspectionApplicationFlowTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired RiskAssessmentRepository assessments;
  @Autowired JwtService jwt;
  @Autowired ObjectMapper mapper;
  @MockitoSpyBean InspectionRepository inspections;
  @MockitoSpyBean ReceiptNumberGenerator receiptNumbers;
  Long assessmentId;
  String token;

  // 주민과 분석 결과 준비
  @BeforeEach
  void setup() {
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
    var resident = users.saveAndFlush(User.resident("flow@example.com", "hash", "주민"));
    token = jwt.issue(resident).accessToken();
    assessmentId =
        assessments
            .saveAndFlush(
                RiskAssessment.completedDemo(
                    resident, "경기도 부천시 원미구 길주로 211", "202호", RiskLevel.HIGH))
            .getId();
  }

  private Map<String, Object> request() {
    var body =
        new java.util.HashMap<String, Object>(
            Map.of(
                "assessmentId",
                assessmentId,
                "latitude",
                37.487485,
                "longitude",
                126.7985672,
                "contact",
                "010-1234-5678",
                "occupancyType",
                "OWNER",
                "floodExperience",
                "YES",
                "existingFacility",
                false,
                "preferredVisitDate",
                LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1).toString(),
                "preferredTimeSlot",
                "MORNING",
                "inflowLocations",
                List.of("WINDOW", "DRAIN")));
    body.put("address", "경기도 부천시 원미구 길주로 211");
    body.put("detailAddress", "202호");
    return body;
  }

  private org.springframework.test.web.servlet.ResultActions submit() throws Exception {
    return mvc.perform(
        post("/api/resident/applications")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(request())));
  }

  @Test
  void submissionImmediatelyReceivesApplicationAndCreatesUsableReceivedInspection()
      throws Exception {
    var response =
        submit()
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.result.status").value("RECEIVED"))
            .andExpect(jsonPath("$.result.inspectionId").isNumber())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var result = mapper.readTree(response).get("result");
    Long applicationId = result.get("applicationId").asLong();
    assertThat(result.get("receiptNo").asText())
        .isEqualTo(String.format(java.util.Locale.ROOT, "%06d", applicationId));
    Long inspectionId = result.get("inspectionId").asLong();
    var application =
        jdbc.queryForMap(
            "SELECT address, detail_address FROM inspection_applications WHERE application_id = ?",
            applicationId);
    assertThat(application.get("address")).isEqualTo("경기도 부천시 원미구 길주로 211");
    assertThat(application.get("detail_address")).isEqualTo("202호");
    var row = jdbc.queryForMap("SELECT * FROM inspections WHERE inspection_id = ?", inspectionId);
    assertThat(((Number) row.get("application_id")).longValue()).isEqualTo(applicationId);
    assertThat(row.get("status")).isEqualTo("RECEIVED");
    assertThat(row.get("scheduled_at")).isNull();
    assertThat(row.get("inspector_user_id")).isNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM application_status_histories WHERE application_id = ?",
                String.class,
                applicationId))
        .isEqualTo("RECEIVED");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM application_inflow_locations", Long.class))
        .isEqualTo(2);
    jdbc.update(
        """
        INSERT INTO users (email, password_hash, name, role, jurisdiction_id)
        VALUES ('flow-admin@example.com', 'hash', '운영자', 'ADMIN',
          (SELECT jurisdiction_id FROM jurisdictions WHERE name = '부천시'))
        """);
    var admin = users.findByEmail("flow-admin@example.com").orElseThrow();
    mvc.perform(
            patch("/api/admin/inspections/" + inspectionId + "/status")
                .header("Authorization", "Bearer " + jwt.issue(admin).accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SCHEDULED\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void rejectsOverlongReasonAndNullInflowLocation() throws Exception {
    var body = request();
    body.put("requestReason", "가".repeat(2001));
    mvc.perform(
            post("/api/resident/applications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
    body.remove("requestReason");
    body.put("inflowLocations", java.util.Collections.singletonList(null));
    mvc.perform(
            post("/api/resident/applications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
  }

  // 점검 생성 실패 시 신청 롤백
  @Test
  void inspectionFailureRollsBackApplicationLocationsAndHistory() throws Exception {
    doThrow(new IllegalStateException("simulated inspection persistence failure"))
        .when(inspections)
        .saveAndFlush(any());
    submit().andExpect(status().isInternalServerError());
    for (String table :
        List.of(
            "inspection_applications",
            "application_inflow_locations",
            "application_status_histories",
            "inspections")) {
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class)).isZero();
    }
  }

  @Test
  void duplicateSubmissionReturnsConflict() throws Exception {
    submit().andExpect(status().isCreated());
    submit().andExpect(status().isConflict());
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inspection_applications", Long.class))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inspections", Long.class)).isEqualTo(1);
  }

  @Test
  void concurrentSubmissionsCreateOnlyOneApplication() throws Exception {
    var checked = new java.util.concurrent.CyclicBarrier(2);
    doAnswer(
            invocation -> {
              checked.await(10, java.util.concurrent.TimeUnit.SECONDS);
              return invocation.callRealMethod();
            })
        .when(receiptNumbers)
        .next();
    var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
    var ready = new java.util.concurrent.CountDownLatch(2);
    var start = new java.util.concurrent.CountDownLatch(1);
    try {
      java.util.concurrent.Callable<Integer> task =
          () -> {
            ready.countDown();
            start.await();
            var result = submit().andReturn();
            var response = result.getResponse();
            if (response.getStatus() == 500) {
              throw new AssertionError(
                  "Unexpected response: " + response.getContentAsString(),
                  result.getResolvedException());
            }
            return response.getStatus();
          };
      var first = executor.submit(task);
      var second = executor.submit(task);
      assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(
              List.of(
                  first.get(30, java.util.concurrent.TimeUnit.SECONDS),
                  second.get(30, java.util.concurrent.TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 409);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inspection_applications", Long.class))
          .isEqualTo(1);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inspections", Long.class)).isEqualTo(1);
    } finally {
      executor.shutdownNow();
    }
  }
}
