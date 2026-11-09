package com.flout.server.domain.inspectionapplication.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.construction.repository.ConstructionRepository;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:application-construction;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false",
      "app.s3.cleanup-interval-millis=3600000"
    })
@AutoConfigureMockMvc
class ApplicationConstructionTest {
  static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  static final LocalDateTime NOW = LocalDateTime.of(2030, 1, 2, 9, 30);
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired ObjectMapper mapper;

  @MockitoBean(name = "inspectionClock")
  Clock clock;

  @MockitoSpyBean ConstructionRepository constructions;
  long supportId, applicationId, adminId;
  long constructionId = 900;
  String token, residentToken;

  /** Seeds an approved support application with a pending construction and a fixed Seoul clock. */
  @BeforeEach
  void setup() {
    reset(constructions);
    when(clock.getZone()).thenReturn(SEOUL);
    when(clock.instant()).thenReturn(NOW.atZone(SEOUL).toInstant());
    for (var table :
        List.of(
            "constructions",
            "support_application_status_histories",
            "support_applications",
            "support_programs",
            "inspection_applications",
            "risk_assessments",
            "refresh_tokens",
            "users")) jdbc.update("DELETE FROM " + table);
    var resident = users.saveAndFlush(User.resident("resident@construction.test", "unused", "김주민"));
    residentToken = jwt.issue(resident).accessToken();
    var admin = users.saveAndFlush(User.resident("admin@construction.test", "unused", "박담당"));
    adminId = admin.getId();
    jdbc.update(
        "UPDATE users SET role='ADMIN',jurisdiction_id=(SELECT jurisdiction_id FROM jurisdictions WHERE name='부천시') WHERE user_id=?",
        adminId);
    token = jwt.issue(users.findById(adminId).orElseThrow()).accessToken();
    jdbc.update(
        "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status) VALUES(?,'경기도 부천시 원미구 길주로 210','101호','HIGH','COMPLETED')",
        resident.getId());
    long risk = jdbc.queryForObject("SELECT assessment_id FROM risk_assessments", Long.class);
    jdbc.update(
        "INSERT INTO inspection_applications(applicant_name,applicant_user_id,assessment_id,receipt_no,address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,status) VALUES('김주민',?,?,'CONSTRUCTION-TEST','경기도 부천시 원미구 길주로 210',37.5,126.76,'010-1234-5678','OWNER','NO',FALSE,'RECEIVED')",
        resident.getId(),
        risk);
    applicationId =
        jdbc.queryForObject("SELECT application_id FROM inspection_applications", Long.class);
    jdbc.update(
        "INSERT INTO support_programs(name,created_at) VALUES('물막이판 지원',CURRENT_TIMESTAMP)");
    long program = jdbc.queryForObject("SELECT program_id FROM support_programs", Long.class);
    jdbc.update(
        "INSERT INTO support_applications(application_id,program_id,applicant_user_id,requested_facility,applicant_name,contact,address,occupancy_type,preferred_visit_date,preferred_visit_time,status,submitted_at,support_scope) VALUES(?,?,?,'출입구 물막이판','김주민','010-1234-5678','경기도 부천시 원미구 길주로 210','OWNER','2030-02-01','AFTERNOON','APPROVED',CURRENT_TIMESTAMP,'물막이판 설치')",
        applicationId,
        program,
        resident.getId());
    supportId =
        jdbc.queryForObject("SELECT support_application_id FROM support_applications", Long.class);
    jdbc.update(
        "INSERT INTO constructions(construction_id,support_application_id,status,created_at,updated_at) VALUES(?,?,'PENDING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        constructionId,
        supportId);
  }

  /** Requests the construction tab with the supplied application ID and optional token. */
  ResultActions getInfo(String id, String access) throws Exception {
    var request = get("/api/admin/applications/" + id + "/construction");
    if (access != null) request.header("Authorization", "Bearer " + access);
    return mvc.perform(request);
  }

  ResultActions getInfo() throws Exception {
    return getInfo(Long.toString(applicationId), token);
  }

  @Test
  void pendingLinkKeepsDesiredScheduleAndDoesNotInventConfirmedDates() throws Exception {
    getInfo()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("APPLICATION_200_6"))
        .andExpect(jsonPath("$.result.applicationId").value(applicationId))
        .andExpect(
            jsonPath("$.result.receiptNo").value(String.format(Locale.ROOT, "%06d", applicationId)))
        .andExpect(jsonPath("$.result.supportApplicationId").value(supportId))
        .andExpect(jsonPath("$.result.supportStatus").value("APPROVED"))
        .andExpect(jsonPath("$.result.construction.constructionId").value(constructionId))
        .andExpect(jsonPath("$.result.construction.status").value("PENDING"))
        .andExpect(jsonPath("$.result.construction.requestedFacility").isNotEmpty())
        .andExpect(jsonPath("$.result.construction.supportScope").isNotEmpty())
        .andExpect(jsonPath("$.result.construction.preferredConstructionDate").value("2030-02-01"))
        .andExpect(jsonPath("$.result.construction.preferredTimeSlot").value("AFTERNOON"))
        .andExpect(
            jsonPath("$.result.construction.constructionDate")
                .value(org.hamcrest.Matchers.nullValue()))
        .andExpect(
            jsonPath("$.result.construction.constructionTime")
                .value(org.hamcrest.Matchers.nullValue()))
        .andExpect(
            jsonPath("$.result.construction.managerId").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(
            jsonPath("$.result.construction.completedAt").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(
            jsonPath("$.result.construction.finalInstallationResult")
                .value(org.hamcrest.Matchers.nullValue()))
        .andExpect(header().string("Cache-Control", "no-store"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"SCHEDULED", "IN_PROGRESS"})
  void returnsPersistedStateAndConfirmedScheduleWithoutUpdatingIt(String state) throws Exception {
    jdbc.update(
        "UPDATE constructions SET status=?,scheduled_at='2030-01-03 10:15:00',manager_user_id=?",
        state,
        adminId);
    var before = jdbc.queryForList("SELECT * FROM constructions");
    getInfo()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.construction.status").value(state))
        .andExpect(jsonPath("$.result.construction.constructionDate").value("2030-01-03"))
        .andExpect(jsonPath("$.result.construction.constructionTime").value("10:15"))
        .andExpect(jsonPath("$.result.construction.preferredConstructionDate").value("2030-02-01"))
        .andExpect(jsonPath("$.result.construction.managerId").value(adminId))
        .andExpect(jsonPath("$.result.construction.managerName").isNotEmpty())
        .andExpect(
            jsonPath("$.result.construction.completedAt").value(org.hamcrest.Matchers.nullValue()));
    assertThat(jdbc.queryForList("SELECT * FROM constructions")).isEqualTo(before);
  }

  @Test
  void completedScreenGetsActualCompletionTimeAndFinalResult() throws Exception {
    jdbc.update(
        "UPDATE constructions SET status='COMPLETED',scheduled_at='2030-01-03 10:00:00',manager_user_id=?,completed_at='2030-01-04 11:30:00',final_installation_result='Installed barrier and checked operation'",
        adminId);
    jdbc.update("UPDATE inspection_applications SET detail_address='Unit 101'");
    var supportBefore = jdbc.queryForList("SELECT * FROM support_applications");
    var constructionBefore = jdbc.queryForList("SELECT * FROM constructions");
    getInfo()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.applicantName").isNotEmpty())
        .andExpect(jsonPath("$.result.address").isNotEmpty())
        .andExpect(jsonPath("$.result.detailAddress").value("Unit 101"))
        .andExpect(jsonPath("$.result.construction.status").value("COMPLETED"))
        .andExpect(jsonPath("$.result.construction.constructionDate").value("2030-01-03"))
        .andExpect(jsonPath("$.result.construction.completedAt").value("2030-01-04T11:30:00+09:00"))
        .andExpect(
            jsonPath("$.result.construction.finalInstallationResult")
                .value("Installed barrier and checked operation"));
    assertThat(jdbc.queryForList("SELECT * FROM support_applications")).isEqualTo(supportBefore);
    assertThat(jdbc.queryForList("SELECT * FROM constructions")).isEqualTo(constructionBefore);
  }

  @Test
  void incompleteConstructionDoesNotExposeStaleCompletionFields() throws Exception {
    jdbc.update(
        "UPDATE constructions SET completed_at='2030-01-04 11:30:00',final_installation_result='stale result'");
    getInfo()
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.result.construction.completedAt").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(
            jsonPath("$.result.construction.finalInstallationResult")
                .value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  void legacyCompletedRowWithoutCompletionTimeDoesNotUseScheduleAsCompletionTime()
      throws Exception {
    jdbc.update("UPDATE constructions SET status='COMPLETED',scheduled_at='2030-01-03 10:00:00'");
    getInfo()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.construction.status").value("COMPLETED"))
        .andExpect(
            jsonPath("$.result.construction.completedAt").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  void approvedWithoutConstructionAndUnsubmittedDraftAreDifferentEmptyStates() throws Exception {
    jdbc.update("DELETE FROM constructions");
    getInfo()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.supportStatus").value("APPROVED"))
        .andExpect(jsonPath("$.result.supportApplicationId").value(supportId))
        .andExpect(jsonPath("$.result.construction").value(org.hamcrest.Matchers.nullValue()));
    jdbc.update("UPDATE support_applications SET status='DRAFT',submitted_at=NULL");
    getInfo()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.supportStatus").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(
            jsonPath("$.result.supportApplicationId").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.result.construction").value(org.hamcrest.Matchers.nullValue()));
    jdbc.update("DELETE FROM support_applications");
    getInfo()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.construction").value(org.hamcrest.Matchers.nullValue()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUBMITTED", "REJECTED"})
  void unapprovedSupportDoesNotExposeAStaleConstruction(String state) throws Exception {
    jdbc.update("UPDATE support_applications SET status=?", state);
    getInfo()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.supportStatus").value(state))
        .andExpect(jsonPath("$.result.construction").value(org.hamcrest.Matchers.nullValue()));
  }

  long anotherSupport(String state, String submitted) {
    jdbc.update(
        "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status) SELECT applicant_user_id,address,'fixture','HIGH','COMPLETED' FROM inspection_applications WHERE application_id=?",
        applicationId);
    long risk = jdbc.queryForObject("SELECT MAX(assessment_id) FROM risk_assessments", Long.class);
    jdbc.update(
        "INSERT INTO inspection_applications(applicant_name,applicant_user_id,assessment_id,receipt_no,address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,status) SELECT applicant_name,applicant_user_id,?, ?,address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,status FROM inspection_applications WHERE application_id=?",
        risk,
        "CONSTRUCTION-" + risk,
        applicationId);
    long source =
        jdbc.queryForObject("SELECT MAX(application_id) FROM inspection_applications", Long.class);
    jdbc.update(
        "INSERT INTO support_programs(name,created_at) VALUES('Another program',CURRENT_TIMESTAMP)");
    long program = jdbc.queryForObject("SELECT MAX(program_id) FROM support_programs", Long.class);
    jdbc.update(
        "INSERT INTO support_applications(application_id,program_id,applicant_user_id,requested_facility,applicant_name,contact,address,occupancy_type,preferred_visit_date,preferred_visit_time,status,submitted_at) SELECT ?,?,applicant_user_id,requested_facility,applicant_name,contact,address,occupancy_type,preferred_visit_date,preferred_visit_time,?,? FROM support_applications WHERE support_application_id=?",
        source,
        program,
        state,
        submitted,
        supportId);
    return jdbc.queryForObject(
        "SELECT MAX(support_application_id) FROM support_applications", Long.class);
  }

  @Test
  void selectsOnlySupportForRequestedApplicationAndIgnoresDraft() throws Exception {
    jdbc.update(
        "UPDATE support_applications SET status='SUBMITTED',submitted_at='2030-01-01 10:00:00' WHERE support_application_id=?",
        supportId);
    anotherSupport("SUBMITTED", "2030-01-02 10:00:00");
    anotherSupport("DRAFT", null);
    getInfo()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.supportApplicationId").value(supportId))
        .andExpect(jsonPath("$.result.supportStatus").value("SUBMITTED"))
        .andExpect(jsonPath("$.result.construction").value(org.hamcrest.Matchers.nullValue()));
    anotherSupport("REJECTED", "2030-01-02 10:00:00");
    getInfo()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.supportApplicationId").value(supportId));
    jdbc.update(
        "UPDATE support_applications SET status='DRAFT',submitted_at=NULL WHERE support_application_id=?",
        supportId);
    getInfo()
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.result.supportApplicationId").value(org.hamcrest.Matchers.nullValue()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
  void rejectsInvalidIds(String id) throws Exception {
    getInfo(id, token)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_400_3"));
  }

  @Test
  void rejectsUnknownOrDraftSourceAndConstructionIdUsedAsApplicationId() throws Exception {
    getInfo(Long.toString(constructionId), token).andExpect(status().isNotFound());
    getInfo("999999", token)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("APPLICATION_404_2"));
    jdbc.update("UPDATE inspection_applications SET status='DRAFT'");
    getInfo().andExpect(status().isNotFound());
  }

  @Test
  void requiresAuthenticatedOperatorWithJurisdiction() throws Exception {
    getInfo(Long.toString(applicationId), null).andExpect(status().isUnauthorized());
    getInfo(Long.toString(applicationId), "invalid").andExpect(status().isUnauthorized());
    getInfo(Long.toString(applicationId), residentToken).andExpect(status().isForbidden());
    jdbc.update("UPDATE users SET jurisdiction_id=NULL WHERE user_id=?", adminId);
    getInfo()
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("APPLICATION_403_4"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"Seoul", "Bucheon-road", "Gimpo"})
  void rejectsSourceOutsideJurisdiction(String address) throws Exception {
    jdbc.update("UPDATE inspection_applications SET address=?", address);
    getInfo().andExpect(status().isForbidden());
  }

  @Test
  void rejectsSupportSnapshotOutsideJurisdiction() throws Exception {
    jdbc.update("UPDATE support_applications SET address='Seoul'");
    getInfo().andExpect(status().isForbidden());
  }

  @Test
  void swaggerDocumentsConstructionTabAndAuth() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.paths['/api/admin/applications/{applicationId}/construction'].get.tags[0]")
                .value("운영자-GIS 신청관리"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/applications/{applicationId}/construction'].get.responses['200']")
                .exists())
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/applications/{applicationId}/construction'].get.responses['401']")
                .exists());
  }
}
