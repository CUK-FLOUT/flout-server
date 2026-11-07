package com.flout.server.domain.inspectionapplication.controller;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.inspectionapplication.service.AttachmentStorage;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:resident-applications-list;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false",
      "app.construction.auto-start.enabled=false",
      "app.s3.cleanup-interval-millis=3600000"
    })
@AutoConfigureMockMvc
class ResidentApplicationsQueryTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired ObjectMapper mapper;
  @MockitoBean AttachmentStorage storage;
  long residentId, applicationId, supportId;
  final long constructionId = 900;
  String token, otherToken, adminToken;

  @BeforeEach
  void setup() {
    for (var table :
        List.of(
            "inspection_result_photos",
            "inspection_vulnerabilities",
            "prevention_recommendations",
            "inspection_results",
            "inspections",
            "construction_photos",
            "constructions",
            "support_application_status_histories",
            "support_attachments",
            "support_applications",
            "support_programs",
            "inspection_applications",
            "risk_assessments",
            "refresh_tokens",
            "users")) jdbc.update("DELETE FROM " + table);
    var resident =
        users.saveAndFlush(User.resident("resident@construction-query.test", "unused", "김주민"));
    residentId = resident.getId();
    token = jwt.issue(resident).accessToken();
    otherToken =
        jwt.issue(
                users.saveAndFlush(
                    User.resident("other@construction-query.test", "unused", "다른주민")))
            .accessToken();
    var admin = users.saveAndFlush(User.resident("admin@construction-query.test", "unused", "운영자"));
    jdbc.update(
        "UPDATE users SET role='ADMIN',jurisdiction_id=(SELECT jurisdiction_id FROM jurisdictions WHERE name='부천시') WHERE user_id=?",
        admin.getId());
    adminToken = jwt.issue(users.findById(admin.getId()).orElseThrow()).accessToken();
    jdbc.update(
        "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status) VALUES(?,'경기도 부천시 원미구 길주로 210','101호','HIGH','COMPLETED')",
        residentId);
    long risk = jdbc.queryForObject("SELECT assessment_id FROM risk_assessments", Long.class);
    jdbc.update(
        "INSERT INTO inspection_applications(applicant_name,applicant_user_id,assessment_id,receipt_no,address,detail_address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,status) VALUES('김주민',?,?,'RESIDENT-CONST-TEST','경기도 부천시 원미구 길주로 210','101호',37.5,126.76,'010-0000-0000','OWNER','NO',FALSE,'RECEIVED')",
        residentId,
        risk);
    applicationId =
        jdbc.queryForObject("SELECT application_id FROM inspection_applications", Long.class);
    jdbc.update(
        "INSERT INTO support_programs(name,created_at) VALUES('물막이판 지원',CURRENT_TIMESTAMP)");
    long program = jdbc.queryForObject("SELECT program_id FROM support_programs", Long.class);
    jdbc.update(
        "INSERT INTO support_applications(application_id,program_id,applicant_user_id,requested_facility,applicant_name,contact,address,occupancy_type,preferred_visit_date,preferred_visit_time,status,submitted_at,landlord_consent_status,support_scope) VALUES(?,?,?,'출입구 물막이판','김주민','010-0000-0000','경기도 부천시 원미구 길주로 210','OWNER','2030-02-01','AFTERNOON','APPROVED',CURRENT_TIMESTAMP,'NOT_REQUIRED','출입구 물막이판 설치')",
        applicationId,
        program,
        residentId);
    supportId =
        jdbc.queryForObject("SELECT support_application_id FROM support_applications", Long.class);
    jdbc.update(
        "INSERT INTO constructions(construction_id,support_application_id,status,scheduled_at,manager_user_id,created_at,updated_at) VALUES(?,?,'SCHEDULED','2030-01-03 10:00:00',?,'2030-01-02 15:00:00','2030-01-02 15:00:00')",
        constructionId,
        supportId,
        admin.getId());
    jdbc.update("DELETE FROM constructions");
    jdbc.update("DELETE FROM support_applications");
    reset(storage);
    when(storage.readUrl(anyString()))
        .thenAnswer(invocation -> "https://example.com/signed/" + invocation.getArgument(0));
  }

  ResultActions list(String access) throws Exception {
    var request = get("/api/resident/applications");
    if (access != null) request.header("Authorization", "Bearer " + access);
    return mvc.perform(request);
  }

  ResultActions detail() throws Exception {
    return mvc.perform(
        get("/api/resident/applications/" + applicationId)
            .header("Authorization", "Bearer " + token));
  }

  long inspection(String status) {
    jdbc.update(
        "INSERT INTO inspections(application_id,status,scheduled_at,pre_visit_notice) VALUES(?,?,?,?)",
        applicationId,
        status,
        status.equals("RECEIVED") ? null : "2030-01-03 10:00:00",
        "방문 전 준비해주세요.");
    return jdbc.queryForObject(
        "SELECT inspection_id FROM inspections WHERE application_id=?", Long.class, applicationId);
  }

  void result(long inspectionId, String state, String summary) {
    jdbc.update(
        "INSERT INTO inspection_results(inspection_id,result_status,summary,internal_memo,published_at) VALUES(?,?,?,'private memo',?)",
        inspectionId,
        state,
        summary,
        state.equals("PUBLISHED") ? "2030-01-04 11:00:00" : null);
  }

  long support(String state, String submitted) {
    jdbc.update("INSERT INTO support_programs(name,created_at) VALUES('추가 지원',CURRENT_TIMESTAMP)");
    long program = jdbc.queryForObject("SELECT MAX(program_id) FROM support_programs", Long.class);
    jdbc.update(
        "INSERT INTO support_applications(application_id,program_id,applicant_user_id,requested_facility,applicant_name,contact,address,occupancy_type,preferred_visit_date,preferred_visit_time,status,submitted_at,approved_at,landlord_consent_status,support_scope,rejection_reason) VALUES(?,?,?,'물막이판','김주민','010-0000-0000','경기도 부천시 원미구 길주로 210','OWNER','2030-02-01','AFTERNOON',?,?,'2030-01-05 10:00:00','NOT_REQUIRED','승인 범위','미승인 사유')",
        applicationId,
        program,
        residentId,
        state,
        submitted);
    return jdbc.queryForObject(
        "SELECT MAX(support_application_id) FROM support_applications", Long.class);
  }

  void construction(long support, String state) {
    jdbc.update(
        "INSERT INTO constructions(construction_id,support_application_id,status,scheduled_at,created_at,updated_at,completed_at,final_installation_result) VALUES(?,?,?,?,'2030-01-05 10:00:00','2030-01-07 10:00:00','2030-01-07 10:00:00','실제 설치 완료 결과')",
        constructionId,
        support,
        state,
        state.equals("PENDING") ? null : "2030-01-06 10:00:00");
  }

  void assertStage(String stage, boolean closed) throws Exception {
    list(token)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result[0].stage").value(stage))
        .andExpect(jsonPath("$.result[0].closed").value(closed));
    detail()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.progress.stage").value(stage))
        .andExpect(jsonPath("$.result.progress.closed").value(closed));
  }

  @Test
  void listReturnsOnlyOwnersSubmittedApplicationsAndEmptyIsSuccess() throws Exception {
    list(token)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("APPLICATION_200_10"))
        .andExpect(jsonPath("$.result.length()").value(1))
        .andExpect(jsonPath("$.result[0].applicationId").value(applicationId))
        .andExpect(
            jsonPath("$.result[0].receiptNumber")
                .value(String.format(Locale.ROOT, "%06d", applicationId)));
    list(otherToken).andExpect(status().isOk()).andExpect(jsonPath("$.result").isEmpty());
    jdbc.update("UPDATE inspection_applications SET status='DRAFT'");
    list(token).andExpect(status().isOk()).andExpect(jsonPath("$.result").isEmpty());
    detail()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.applicationStatus").value("DRAFT"))
        .andExpect(jsonPath("$.result.progress.stage").value(nullValue()));
  }

  @Test
  void preservesExistingDetailAndAddsNullSafeProgressWithoutChangingData() throws Exception {
    var before = jdbc.queryForList("SELECT * FROM inspection_applications");
    assertStage("INSPECTION_RECEIVED", false);
    detail()
        .andExpect(jsonPath("$.result.applicant.name").value("김주민"))
        .andExpect(jsonPath("$.result.occupancyType").value("OWNER"))
        .andExpect(jsonPath("$.result.attachmentCount").value(0))
        .andExpect(jsonPath("$.result.progress.inspectionId").value(nullValue()))
        .andExpect(jsonPath("$.result.progress.support").value(nullValue()))
        .andExpect(jsonPath("$.result.progress.construction").value(nullValue()))
        .andExpect(jsonPath("$.result.progress.visit").value(nullValue()))
        .andExpect(jsonPath("$.result.progress.inspectionResultSummary").value(nullValue()));
    assertThat(jdbc.queryForList("SELECT * FROM inspection_applications")).isEqualTo(before);
    verifyNoInteractions(storage);
  }

  /** 공개 결과가 없는 점검의 목록·상세 단계와 공개 여부를 검증하며, 완료·미공개 건도 점검 진행으로 표시한다. */
  @ParameterizedTest
  @CsvSource({
    "RECEIVED,INSPECTION_RECEIVED",
    "SCHEDULED,VISIT_SCHEDULED",
    "IN_PROGRESS,INSPECTION_IN_PROGRESS",
    "COMPLETED,INSPECTION_IN_PROGRESS"
  })
  void actualInspectionStateIsSharedWithListWithoutClaimingPublication(String state, String stage)
      throws Exception {
    long id = inspection(state);
    result(id, "DRAFT", "private draft");
    assertStage(stage, false);
    detail()
        .andExpect(jsonPath("$.result.inspectionStatus").value(state))
        .andExpect(jsonPath("$.result.resultPublished").value(false))
        .andExpect(jsonPath("$.result.progress.inspectionResultSummary").value(nullValue()));
    if (!state.equals("RECEIVED"))
      detail()
          .andExpect(
              jsonPath("$.result.progress.visit.scheduledAt").value("2030-01-03T10:00:00+09:00"));
  }

  @Test
  void onlyPublishedResultSummaryIsExposedAndDraftSupportDoesNotMaskIt() throws Exception {
    long id = inspection("COMPLETED");
    result(id, "DRAFT", "private draft");
    result(id, "PUBLISHED", "공개된 전문가 의견");
    support("DRAFT", null);
    assertStage("INSPECTION_COMPLETED", false);
    String body =
        detail()
            .andExpect(jsonPath("$.result.resultPublished").value(true))
            .andExpect(jsonPath("$.result.progress.inspectionResultSummary").value("공개된 전문가 의견"))
            .andExpect(jsonPath("$.result.progress.supportApplicationId").value(nullValue()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body).doesNotContain("private draft", "private memo");
  }

  @ParameterizedTest
  @CsvSource({
    "SUBMITTED,SUPPORT_REVIEW,false",
    "APPROVED,SUPPORT_APPROVED,false",
    "REJECTED,SUPPORT_REJECTED,true"
  })
  void supportStateAndPublicFieldsUseLatestSavedDecision(String state, String stage, boolean closed)
      throws Exception {
    long id = support(state, "2030-01-05 09:00:00");
    assertStage(stage, closed);
    detail()
        .andExpect(jsonPath("$.result.progress.supportApplicationId").value(id))
        .andExpect(jsonPath("$.result.progress.support.status").value(state))
        .andExpect(
            jsonPath("$.result.progress.support.supportScope")
                .value(state.equals("APPROVED") ? equalTo("승인 범위") : nullValue()))
        .andExpect(
            jsonPath("$.result.progress.support.rejectionReason")
                .value(state.equals("REJECTED") ? equalTo("미승인 사유") : nullValue()));
  }

  @ParameterizedTest
  @CsvSource({
    "PENDING,SUPPORT_APPROVED,false",
    "SCHEDULED,CONSTRUCTION_SCHEDULED,false",
    "IN_PROGRESS,CONSTRUCTION_IN_PROGRESS,false",
    "COMPLETED,COMPLETED,true"
  })
  void constructionProgressExposesCompletionOnlyWhenCompleted(
      String state, String stage, boolean closed) throws Exception {
    long id = support("APPROVED", "2030-01-05 09:00:00");
    construction(id, state);
    assertStage(stage, closed);
    detail()
        .andExpect(jsonPath("$.result.progress.constructionId").value(constructionId))
        .andExpect(jsonPath("$.result.progress.construction.status").value(state))
        .andExpect(
            jsonPath("$.result.progress.construction.finalInstallationResult")
                .value(state.equals("COMPLETED") ? equalTo("실제 설치 완료 결과") : nullValue()))
        .andExpect(
            jsonPath("$.result.progress.construction.completedAt")
                .value(
                    state.equals("COMPLETED")
                        ? equalTo("2030-01-07T10:00:00+09:00")
                        : nullValue()));
  }

  @Test
  void newestSubmittedSupportTakesPriorityOverOldConstructionWithDeterministicTieBreak()
      throws Exception {
    long old = support("APPROVED", "2030-01-05 09:00:00");
    construction(old, "COMPLETED");
    long latest = support("SUBMITTED", "2030-01-06 09:00:00");
    support("DRAFT", null);
    assertStage("SUPPORT_REVIEW", false);
    detail()
        .andExpect(jsonPath("$.result.progress.supportApplicationId").value(latest))
        .andExpect(jsonPath("$.result.progress.construction").value(nullValue()));
    long tied = support("REJECTED", "2030-01-06 09:00:00");
    assertStage("SUPPORT_REJECTED", true);
    detail().andExpect(jsonPath("$.result.progress.supportApplicationId").value(tied));
  }

  @Test
  void listOrderingUsesSubmissionThenCreationThenIdWithoutUsingFutureVisitTime() throws Exception {
    jdbc.update(
        "UPDATE inspection_applications SET created_at='2030-01-01 09:00:00',submitted_at='2030-01-02 09:00:00'");
    long risk = jdbc.queryForObject("SELECT assessment_id FROM risk_assessments", Long.class);
    jdbc.update(
        "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status) VALUES(?,'부천시','102호','LOW','COMPLETED')",
        residentId);
    long secondRisk =
        jdbc.queryForObject("SELECT MAX(assessment_id) FROM risk_assessments", Long.class);
    jdbc.update(
        "INSERT INTO inspection_applications(applicant_name,applicant_user_id,assessment_id,receipt_no,address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,status,created_at,submitted_at) VALUES('김주민',?,?,'LIST-SECOND','부천시',37.5,126.7,'010','OWNER','NO',FALSE,'RECEIVED','2030-01-01 08:00:00','2030-01-02 09:00:00')",
        residentId,
        secondRisk);
    long second =
        jdbc.queryForObject("SELECT MAX(application_id) FROM inspection_applications", Long.class);
    list(token)
        .andExpect(jsonPath("$.result[0].applicationId").value(second))
        .andExpect(jsonPath("$.result[1].applicationId").value(applicationId));
    jdbc.update(
        "UPDATE inspection_applications SET submitted_at=NULL,created_at='2030-01-03 10:00:00' WHERE application_id=?",
        applicationId);
    list(token).andExpect(jsonPath("$.result[0].applicationId").value(applicationId));
    inspection("SCHEDULED");
    jdbc.update(
        "UPDATE inspections SET updated_at='2030-01-02 10:00:00',scheduled_at='2099-01-01 10:00:00'");
    list(token).andExpect(jsonPath("$.result[0].updatedAt").value("2030-01-03T10:00:00+09:00"));
    assertThat(risk).isPositive();
  }

  @Test
  void listEnforcesResidentRoleAndDetailRetainsOwnershipGuard() throws Exception {
    list(null).andExpect(status().isUnauthorized());
    list("invalid").andExpect(status().isUnauthorized());
    list(adminToken).andExpect(status().isForbidden());
    mvc.perform(
            get("/api/resident/applications/" + applicationId)
                .header("Authorization", "Bearer " + otherToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("APPLICATION_403_6"));
  }

  @Test
  void swaggerProvidesArrayEnvelopeAndPreservesOldDetailExampleWithProgress() throws Exception {
    var doc =
        mapper.readTree(
            mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    var tagNames = new ArrayList<String>();
    doc.path("tags").forEach(tag -> tagNames.add(tag.path("name").asText()));
    assertThat(tagNames).containsSubsequence("주민-지원신청", "주민-시공 신청", "주민-내신청");
    var paths = doc.path("paths");
    for (var path :
        List.of(
            "/api/resident/constructions/{constructionId}/schedule",
            "/api/resident/constructions/{constructionId}")) {
      assertThat(paths.path(path).path("get").path("tags").path(0).asText()).isEqualTo("주민-시공 신청");
    }
    for (var path :
        List.of("/api/resident/applications", "/api/resident/applications/{applicationId}")) {
      assertThat(paths.path(path).path("get").path("tags").path(0).asText()).isEqualTo("주민-내신청");
    }
    assertThat(paths.path("/api/resident/applications").path("post").path("tags").path(0).asText())
        .isEqualTo("주민-현장점검 신청");
    var content =
        doc.get("paths")
            .get("/api/resident/applications")
            .get("get")
            .get("responses")
            .get("200")
            .get("content")
            .get("application/json");
    assertThat(content.get("schema").get("$ref").asText())
        .contains("SuccessResponseListResidentApplicationSummaryResponse");
    assertThat(content.get("examples").get("empty").get("value").get("result").isArray()).isTrue();
    var detail =
        doc.get("paths")
            .get("/api/resident/applications/{applicationId}")
            .get("get")
            .get("responses")
            .get("200")
            .get("content")
            .get("application/json");
    assertThat(
            detail
                .get("examples")
                .get("residentApplicationDetail")
                .get("value")
                .get("result")
                .has("progress"))
        .isTrue();
    assertThat(
            doc.get("components")
                .get("schemas")
                .get("ResidentApplicationDetailResponse")
                .get("properties")
                .has("progress"))
        .isTrue();
  }
}
