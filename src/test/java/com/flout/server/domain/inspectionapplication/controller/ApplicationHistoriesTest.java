package com.flout.server.domain.inspectionapplication.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.construction.repository.ConstructionRepository;
import com.flout.server.domain.construction.service.ConstructionStatusService;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:application-histories;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false",
      "app.s3.cleanup-interval-millis=3600000"
    })
@AutoConfigureMockMvc
class ApplicationHistoriesTest {
  static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  static final LocalDateTime NOW = LocalDateTime.of(2030, 1, 2, 9, 30);
  @Autowired ConstructionStatusService constructionStatus;
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

  @BeforeEach
  void setup() {
    reset(constructions);
    when(clock.getZone()).thenReturn(SEOUL);
    when(clock.instant()).thenReturn(NOW.atZone(SEOUL).toInstant());
    for (var table :
        List.of(
            "construction_photos",
            "constructions",
            "support_application_status_histories",
            "support_applications",
            "support_programs",
            "inspection_result_photos",
            "inspection_vulnerabilities",
            "prevention_recommendations",
            "inspection_results",
            "inspection_status_histories",
            "inspections",
            "application_status_histories",
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
        "INSERT INTO support_applications(application_id,program_id,applicant_user_id,requested_facility,applicant_name,contact,address,occupancy_type,preferred_visit_date,preferred_visit_time,status,submitted_at,landlord_consent_status,support_scope) VALUES(?,?,?,'출입구 물막이판','김주민','010-1234-5678','경기도 부천시 원미구 길주로 210','OWNER','2030-02-01','AFTERNOON','APPROVED',CURRENT_TIMESTAMP,'NOT_REQUIRED','물막이판 설치')",
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

  ResultActions history(String id, String access) throws Exception {
    var request = get("/api/admin/applications/" + id + "/histories");
    if (access != null) request.header("Authorization", "Bearer " + access);
    return mvc.perform(request);
  }

  ResultActions history() throws Exception {
    return history(Long.toString(applicationId), token);
  }

  void now(LocalDateTime time) {
    when(clock.instant()).thenReturn(time.atZone(SEOUL).toInstant());
  }

  void empty() {
    jdbc.update("DELETE FROM constructions");
    jdbc.update("DELETE FROM support_applications");
  }

  long inspection() {
    jdbc.update(
        "INSERT INTO inspections(application_id,status) VALUES(?,'COMPLETED')", applicationId);
    return jdbc.queryForObject("SELECT inspection_id FROM inspections", Long.class);
  }

  void inspectionEvent(
      long id, String previous, String state, String source, Long actor, String time) {
    jdbc.update(
        "INSERT INTO inspection_status_histories(inspection_id,previous_status,status,changed_by_user_id,change_source,changed_at) VALUES(?,?,?,?,?,?)",
        id,
        previous,
        state,
        actor,
        source,
        time);
  }

  void approvalHistory(String state, String time) {
    jdbc.update(
        "INSERT INTO support_application_status_histories(support_application_id,previous_status,status,changed_by_user_id,changed_at) VALUES(?,'SUBMITTED',?,?,?)",
        supportId,
        state,
        adminId,
        time);
  }

  @Test
  void fullTimelineUsesNineActualStagesInNewestFirstOrder() throws Exception {
    jdbc.update("UPDATE inspection_applications SET submitted_at='2030-01-01 10:00:00'");
    long inspection = inspection();
    inspectionEvent(inspection, "RECEIVED", "SCHEDULED", "MANUAL", adminId, "2030-01-02 10:00:00");
    inspectionEvent(inspection, "SCHEDULED", "IN_PROGRESS", "AUTO", null, "2030-01-03 10:00:00");
    inspectionEvent(
        inspection, "IN_PROGRESS", "COMPLETED", "MANUAL", adminId, "2030-01-04 10:00:00");
    jdbc.update(
        "INSERT INTO inspection_results(inspection_id,result_status,published_at,published_by_user_id) VALUES(?,'PUBLISHED','2030-01-04 10:00:00',?)",
        inspection,
        adminId);
    jdbc.update(
        "UPDATE support_applications SET submitted_at='2030-01-05 10:00:00',approved_at='2030-01-06 10:00:00'");
    approvalHistory("APPROVED", "2030-01-06 10:00:00");
    now(LocalDateTime.of(2030, 1, 7, 10, 0));
    mvc.perform(
            post("/api/admin/constructions/" + constructionId + "/schedule")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"constructionDate\":\"2030-01-08\",\"constructionTime\":\"10:00\"}"))
        .andExpect(status().isOk());
    now(LocalDateTime.of(2030, 1, 8, 10, 0));
    assertThat(constructionStatus.startIfDue(constructionId)).isTrue();
    assertThat(constructionStatus.startIfDue(constructionId)).isFalse();
    jdbc.update(
        "INSERT INTO construction_photos(construction_id,object_key,original_filename,file_size,content_type,created_at) VALUES(?,'history/photo','photo.jpg',100,'image/jpeg',CURRENT_TIMESTAMP)",
        constructionId);
    now(LocalDateTime.of(2030, 1, 9, 10, 0));
    mvc.perform(
            post("/api/admin/constructions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"constructionId\":900,\"finalInstallationResult\":\"Installation finished\"}"))
        .andExpect(status().isOk());
    var before = jdbc.queryForList("SELECT * FROM construction_status_histories");
    var node =
        mapper.readTree(
            history()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("APPLICATION_200_7"))
                .andExpect(jsonPath("$.result.totalCount").value(9))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn()
                .getResponse()
                .getContentAsString());
    var events = node.path("result").path("histories");
    var types = new ArrayList<String>();
    for (var item : events) types.add(item.path("eventType").asString());
    assertThat(types)
        .containsExactly(
            "CONSTRUCTION_COMPLETED",
            "CONSTRUCTION_IN_PROGRESS",
            "CONSTRUCTION_SCHEDULED",
            "SUPPORT_APPROVED",
            "SUPPORT_SUBMITTED",
            "INSPECTION_COMPLETED",
            "INSPECTION_IN_PROGRESS",
            "INSPECTION_SCHEDULED",
            "APPLICATION_RECEIVED");
    assertThat(events.get(0).path("actorId").asLong()).isEqualTo(adminId);
    assertThat(events.get(1).path("actorType").asString()).isEqualTo("SYSTEM");
    assertThat(events.get(4).path("statusLabel").asString()).isEqualTo("지원 검토중");
    assertThat(events.get(5).path("actorId").asLong()).isEqualTo(adminId);
    assertThat(events.get(8).path("actorType").asString()).isEqualTo("RESIDENT");
    assertThat(events.get(0).path("occurredAt").asString()).isEqualTo("2030-01-09T10:00:00+09:00");
    history().andExpect(status().isOk()).andExpect(jsonPath("$.result.totalCount").value(9));
    assertThat(jdbc.queryForList("SELECT * FROM construction_status_histories")).isEqualTo(before);
  }

  @Test
  void legacyReceivedHistoryShowsRecordedReceiptBeforeAssignment() throws Exception {
    empty();
    jdbc.update(
        "INSERT INTO application_status_histories(application_id,status,created_at) VALUES(?,'RECEIVED','2030-01-01 10:00:00')",
        applicationId);
    long inspection = inspection();
    inspectionEvent(inspection, "RECEIVED", "SCHEDULED", "MANUAL", adminId, "2030-01-02 10:00:00");
    history()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalCount").value(2))
        .andExpect(jsonPath("$.result.histories[0].eventType").value("INSPECTION_SCHEDULED"))
        .andExpect(jsonPath("$.result.histories[1].eventType").value("APPLICATION_RECEIVED"))
        .andExpect(jsonPath("$.result.histories[1].occurredAt").value("2030-01-01T10:00:00+09:00"))
        .andExpect(jsonPath("$.result.histories[1].description").value("기존 신청에 저장된 접수 상태 기록입니다."));
  }

  @Test
  void noEventsIsSuccessful() throws Exception {
    empty();
    history()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalCount").value(0))
        .andExpect(jsonPath("$.result.histories").isEmpty());
  }

  @Test
  void receiptTimeIsPersistedSubmissionNotCreationOrScheduledTime() throws Exception {
    empty();
    jdbc.update("UPDATE inspection_applications SET submitted_at='2030-01-01 10:00:00'");
    jdbc.update(
        "INSERT INTO application_status_histories(application_id,status,created_at) VALUES(?,'RECEIVED','2030-01-01 09:00:00')",
        applicationId);
    history()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalCount").value(1))
        .andExpect(jsonPath("$.result.histories[0].occurredAt").value("2030-01-01T10:00:00+09:00"));
  }

  @Test
  void draftSupportAndDraftInspectionResultDoNotCreateEvents() throws Exception {
    jdbc.update("UPDATE support_applications SET status='DRAFT',submitted_at=NULL");
    long inspection = inspection();
    jdbc.update(
        "INSERT INTO inspection_results(inspection_id,result_status) VALUES(?,'DRAFT')",
        inspection);
    history().andExpect(status().isOk()).andExpect(jsonPath("$.result.totalCount").value(0));
  }

  @Test
  void rejectionIsARealDecisionAndReceiptRemainsOneEvent() throws Exception {
    jdbc.update(
        "UPDATE support_applications SET status='REJECTED',submitted_at='2030-01-02 10:00:00'");
    approvalHistory("REJECTED", "2030-01-03 10:00:00");
    history()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalCount").value(2))
        .andExpect(jsonPath("$.result.histories[0].eventType").value("SUPPORT_REJECTED"))
        .andExpect(jsonPath("$.result.histories[1].eventType").value("SUPPORT_SUBMITTED"));
  }

  @Test
  void unknownLegacyPublisherDoesNotBecomeAssignedOperator() throws Exception {
    empty();
    long inspection = inspection();
    inspectionEvent(
        inspection, "IN_PROGRESS", "COMPLETED", "MANUAL", adminId, "2030-01-02 10:00:00");
    jdbc.update(
        "INSERT INTO inspection_results(inspection_id,result_status,published_at) VALUES(?,'PUBLISHED','2030-01-03 10:00:00')",
        inspection);
    history()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalCount").value(1))
        .andExpect(jsonPath("$.result.histories[0].actorType").value("UNKNOWN"))
        .andExpect(
            jsonPath("$.result.histories[0].actorId").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  void missingHistoricalStagesAreNotInventedAndNamesArePreserved() throws Exception {
    empty();
    long inspection = inspection();
    inspectionEvent(inspection, "RECEIVED", "SCHEDULED", "MANUAL", adminId, "2030-01-02 10:00:00");
    history()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalCount").value(1))
        .andExpect(jsonPath("$.result.histories[0].eventType").value("INSPECTION_SCHEDULED"))
        .andExpect(
            jsonPath("$.result.histories[0].actorName")
                .value(users.findById(adminId).orElseThrow().getName()));
  }

  @Test
  void constructionHistoryRollsBackWhenScheduleSaveFails() throws Exception {
    doThrow(new IllegalStateException("database failure")).when(constructions).flush();
    mvc.perform(
            post("/api/admin/constructions/" + constructionId + "/schedule")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"constructionDate\":\"2030-01-03\",\"constructionTime\":\"10:00\"}"))
        .andExpect(status().isInternalServerError());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM construction_status_histories", Integer.class))
        .isZero();
    assertThat(jdbc.queryForObject("SELECT status FROM constructions", String.class))
        .isEqualTo("PENDING");
  }

  @Test
  void sameSecondUsesStageOrderAndStableEventIds() throws Exception {
    jdbc.update("UPDATE inspection_applications SET submitted_at='2030-01-01 10:00:00'");
    jdbc.update("UPDATE support_applications SET submitted_at='2030-01-01 10:00:00'");
    approvalHistory("APPROVED", "2030-01-01 10:00:00");
    String first = history().andReturn().getResponse().getContentAsString();
    var a = mapper.readTree(first).path("result").path("histories");
    var b =
        mapper
            .readTree(history().andReturn().getResponse().getContentAsString())
            .path("result")
            .path("histories");
    assertThat(a).isEqualTo(b);
    assertThat(a.get(0).path("eventType").asString()).isEqualTo("SUPPORT_APPROVED");
    assertThat(a.get(2).path("eventType").asString()).isEqualTo("APPLICATION_RECEIVED");
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
  void rejectsInvalidIds(String id) throws Exception {
    history(id, token).andExpect(status().isBadRequest());
  }

  @Test
  void requiresAuthenticationAndJurisdiction() throws Exception {
    history(Long.toString(applicationId), null).andExpect(status().isUnauthorized());
    history(Long.toString(applicationId), "invalid").andExpect(status().isUnauthorized());
    history(Long.toString(applicationId), residentToken).andExpect(status().isForbidden());
    jdbc.update("UPDATE users SET jurisdiction_id=NULL WHERE user_id=?", adminId);
    history().andExpect(status().isForbidden());
  }

  @Test
  void rejectsOtherJurisdictionMissingAndDraftApplications() throws Exception {
    history("999999", token).andExpect(status().isNotFound());
    jdbc.update("UPDATE support_applications SET address='Seoul'");
    history().andExpect(status().isForbidden());
    jdbc.update("UPDATE inspection_applications SET address='Seoul'");
    history().andExpect(status().isForbidden());
    jdbc.update("UPDATE inspection_applications SET status='DRAFT'");
    history().andExpect(status().isNotFound());
  }

  @Test
  void swaggerDocumentsHistoriesRoute() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/applications/{applicationId}/histories'].get.responses['200']")
                .exists())
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/applications/{applicationId}/histories'].get.responses['401']")
                .exists());
  }

  @Test
  void publicationOnAlreadyCompletedInspectionStillRecordsActualPublisher() throws Exception {
    empty();
    long inspection = inspection();
    jdbc.update(
        "INSERT INTO inspection_results(inspection_id,result_status) VALUES(?,'DRAFT')",
        inspection);
    long result = jdbc.queryForObject("SELECT result_id FROM inspection_results", Long.class);
    jdbc.update(
        "INSERT INTO inspection_result_photos(result_id,object_key,original_filename,file_size,content_type) VALUES(?,'history/draft-photo','photo.jpg',100,'image/jpeg')",
        result);
    mvc.perform(
            post("/api/admin/inspections/" + inspection + "/result")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"summary\":\"Inspection finished\",\"vulnerabilities\":[{\"type\":\"ENTRANCE\",\"title\":\"Entrance\",\"expertOpinion\":\"Barrier required\"}],\"recommendedActions\":[{\"description\":\"Install barrier\"}],\"followUpType\":\"INDIVIDUAL_HOUSE\"}"))
        .andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject("SELECT published_by_user_id FROM inspection_results", Long.class))
        .isEqualTo(adminId);
    assertThat(
            jdbc.queryForObject("SELECT COUNT(*) FROM inspection_status_histories", Integer.class))
        .isZero();
    history()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalCount").value(1))
        .andExpect(jsonPath("$.result.histories[0].eventType").value("INSPECTION_COMPLETED"))
        .andExpect(jsonPath("$.result.histories[0].actorType").value("ADMIN"))
        .andExpect(jsonPath("$.result.histories[0].actorId").value(adminId));
  }

  @Test
  void includesAllSubmittedProgramsInsteadOfOnlyTheLatestAndExcludesDrafts() throws Exception {
    jdbc.update("UPDATE support_applications SET submitted_at='2030-01-01 10:00:00'");
    jdbc.update(
        "INSERT INTO support_programs(name,created_at) VALUES('Second program',CURRENT_TIMESTAMP)");
    long program = jdbc.queryForObject("SELECT MAX(program_id) FROM support_programs", Long.class);
    jdbc.update(
        "INSERT INTO support_applications(application_id,program_id,applicant_user_id,requested_facility,applicant_name,contact,address,occupancy_type,preferred_visit_date,preferred_visit_time,status,submitted_at,landlord_consent_status) SELECT application_id,?,applicant_user_id,requested_facility,applicant_name,contact,address,occupancy_type,preferred_visit_date,preferred_visit_time,'SUBMITTED','2030-01-02 10:00:00',landlord_consent_status FROM support_applications WHERE support_application_id=?",
        program,
        supportId);
    long second =
        jdbc.queryForObject(
            "SELECT MAX(support_application_id) FROM support_applications", Long.class);
    history()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalCount").value(2))
        .andExpect(jsonPath("$.result.histories[0].supportApplicationId").value(second))
        .andExpect(jsonPath("$.result.histories[1].supportApplicationId").value(supportId));
    jdbc.update(
        "UPDATE support_applications SET status='DRAFT',submitted_at=NULL WHERE support_application_id=?",
        second);
    history()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalCount").value(1))
        .andExpect(jsonPath("$.result.histories[0].supportApplicationId").value(supportId));
  }
}
