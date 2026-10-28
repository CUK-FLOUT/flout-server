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
                    resident, "경기도 부천시 원미구 길주로 210", "101호", RiskLevel.HIGH))
            .getId();
  }

  private Map<String, Object> request() {
    return Map.of(
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
        List.of("WINDOW", "DRAIN"));
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
    Long inspectionId = result.get("inspectionId").asLong();
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
  void repeatSubmissionAndReceiptNumberRetryCreateExactlyOneInspectionPerApplication()
      throws Exception {
    doReturn("IA-FLOW-000000000001", "IA-FLOW-000000000001", "IA-FLOW-000000000002")
        .when(receiptNumbers)
        .next();
    var first =
        mapper
            .readTree(
                submit()
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("result");
    var second =
        mapper
            .readTree(
                submit()
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("result");
    assertThat(first.get("applicationId").asLong())
        .isNotEqualTo(second.get("applicationId").asLong());
    assertThat(first.get("inspectionId").asLong())
        .isNotEqualTo(second.get("inspectionId").asLong());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM inspection_applications WHERE status = 'RECEIVED'",
                Long.class))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM inspections WHERE status = 'RECEIVED'", Long.class))
        .isEqualTo(2);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM application_status_histories", Long.class))
        .isEqualTo(2);
  }
}
