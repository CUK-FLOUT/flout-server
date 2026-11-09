package com.flout.server.domain.construction;

import static org.assertj.core.api.Assertions.*;
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
import org.junit.jupiter.params.provider.ValueSource;
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
      "spring.datasource.url=jdbc:h2:mem:resident-construction-query;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false",
      "app.construction.auto-start.enabled=false",
      "app.s3.cleanup-interval-millis=3600000"
    })
@AutoConfigureMockMvc
class ResidentConstructionQueryTest {
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
        "INSERT INTO support_applications(application_id,program_id,applicant_user_id,requested_facility,applicant_name,contact,address,occupancy_type,preferred_visit_date,preferred_visit_time,status,submitted_at,support_scope) VALUES(?,?,?,'출입구 물막이판','김주민','010-0000-0000','경기도 부천시 원미구 길주로 210','OWNER','2030-02-01','AFTERNOON','APPROVED',CURRENT_TIMESTAMP,'출입구 물막이판 설치')",
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
    reset(storage);
    when(storage.readUrl(anyString()))
        .thenAnswer(invocation -> "https://example.com/signed/" + invocation.getArgument(0));
  }

  ResultActions request(String id, String suffix, String access) throws Exception {
    var request = get("/api/resident/constructions/" + id + suffix);
    if (access != null) request.header("Authorization", "Bearer " + access);
    return mvc.perform(request);
  }

  ResultActions own(String suffix) throws Exception {
    return request(Long.toString(constructionId), suffix, token);
  }

  void complete() {
    jdbc.update(
        "UPDATE constructions SET status='COMPLETED',completed_at='2030-01-03 11:30:00',final_installation_result='출입구 물막이판 설치 · 개폐 확인 완료',updated_at='2030-01-03 11:30:00' WHERE construction_id=?",
        constructionId);
  }

  void addConstructionPhoto() {
    jdbc.update(
        "INSERT INTO construction_photos(construction_id,object_key,original_filename,file_size,content_type,created_at) VALUES(?,'completed-photo','설치완료.jpg',200,'image/jpeg','2030-01-03 11:20:00')",
        constructionId);
  }

  @Test
  void scheduleUsesConfirmedTimeAndOriginalReceiptWithoutMutation() throws Exception {
    var before = jdbc.queryForList("SELECT * FROM constructions");
    own("/schedule")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("CONSTRUCTION_200_4"))
        .andExpect(jsonPath("$.result.constructionId").value(constructionId))
        .andExpect(jsonPath("$.result.supportApplicationId").value(supportId))
        .andExpect(jsonPath("$.result.applicationId").value(applicationId))
        .andExpect(
            jsonPath("$.result.receiptNo").value(String.format(Locale.ROOT, "%06d", applicationId)))
        .andExpect(jsonPath("$.result.address").value("경기도 부천시 원미구 길주로 210"))
        .andExpect(jsonPath("$.result.detailAddress").value("101호"))
        .andExpect(jsonPath("$.result.applicantName").value("김주민"))
        .andExpect(jsonPath("$.result.status").value("SCHEDULED"))
        .andExpect(jsonPath("$.result.requestedFacility").value("출입구 물막이판"))
        .andExpect(jsonPath("$.result.supportScope").value("출입구 물막이판 설치"))
        .andExpect(jsonPath("$.result.scheduledConstructionDate").value("2030-01-03"))
        .andExpect(jsonPath("$.result.scheduledConstructionTime").value("10:00:00"));
    assertThat(jdbc.queryForList("SELECT * FROM constructions")).isEqualTo(before);
    assertThat(
            jdbc.queryForObject("SELECT COUNT(*) FROM construction_status_histories", Long.class))
        .isZero();
    verifyNoInteractions(storage);
  }

  @Test
  void pendingScheduleHasNullConfirmedTimeAndDoesNotUsePreferredDate() throws Exception {
    jdbc.update("UPDATE constructions SET status='PENDING',scheduled_at=NULL,manager_user_id=NULL");
    jdbc.update("UPDATE inspection_applications SET detail_address=NULL");
    jdbc.update("UPDATE support_applications SET support_scope=NULL");
    own("/schedule")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.status").value("PENDING"))
        .andExpect(jsonPath("$.result.detailAddress").value(nullValue()))
        .andExpect(jsonPath("$.result.supportScope").value(nullValue()))
        .andExpect(jsonPath("$.result.scheduledConstructionDate").value(nullValue()))
        .andExpect(jsonPath("$.result.scheduledConstructionTime").value(nullValue()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"PENDING", "SCHEDULED", "IN_PROGRESS"})
  void incompleteResultsNeverExposeUploadedPhotosOrStaleCompletionValues(String state)
      throws Exception {
    jdbc.update(
        "UPDATE constructions SET status=?,completed_at='2030-01-03 11:30:00',final_installation_result='unpublished'",
        state);
    addConstructionPhoto();
    own("")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("CONSTRUCTION_200_5"))
        .andExpect(jsonPath("$.result.status").value(state))
        .andExpect(jsonPath("$.result.completedAt").value(nullValue()))
        .andExpect(jsonPath("$.result.finalInstallationResult").value(nullValue()))
        .andExpect(jsonPath("$.result.photos").isEmpty())
        .andExpect(jsonPath("$.result.beforePhotos").isEmpty())
        .andExpect(jsonPath("$.result.workHistory").isEmpty());
    verifyNoInteractions(storage);
  }

  @Test
  void completedReturnsPublicInspectionPhotosAndConstructionPhotosSeparately() throws Exception {
    complete();
    addConstructionPhoto();
    jdbc.update(
        "INSERT INTO inspections(application_id,status) VALUES(?,'COMPLETED')", applicationId);
    long inspectionId = jdbc.queryForObject("SELECT inspection_id FROM inspections", Long.class);
    for (var state : List.of("DRAFT", "PUBLISHED")) {
      jdbc.update(
          "INSERT INTO inspection_results(inspection_id,result_status,summary,internal_memo,published_at) VALUES(?,?,'점검 요약','private memo',?)",
          inspectionId,
          state,
          state.equals("PUBLISHED") ? "2030-01-01 10:00:00" : null);
      long resultId =
          jdbc.queryForObject(
              "SELECT result_id FROM inspection_results WHERE result_status=?", Long.class, state);
      jdbc.update(
          "INSERT INTO inspection_result_photos(result_id,object_key,original_filename,file_size,content_type,created_at) VALUES(?,?,?,100,'image/jpeg','2030-01-01 09:00:00')",
          resultId,
          state.toLowerCase() + "-photo",
          state + ".jpg");
    }
    for (var event :
        List.of(
            new String[] {"COMPLETED", "2030-01-03 11:30:00"},
            new String[] {"SCHEDULED", "2030-01-02 15:00:00"},
            new String[] {"IN_PROGRESS", "2030-01-03 10:00:00"}))
      jdbc.update(
          "INSERT INTO construction_status_histories(construction_id,status,change_source,changed_at,actor_name) VALUES(?,?,'MANUAL',?,'private operator')",
          constructionId,
          event[0],
          event[1]);
    var before = jdbc.queryForList("SELECT * FROM constructions");
    var historyBefore = jdbc.queryForList("SELECT * FROM construction_status_histories");
    String body =
        own("")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.completedAt").value("2030-01-03T11:30:00+09:00"))
            .andExpect(jsonPath("$.result.finalInstallationResult").value("출입구 물막이판 설치 · 개폐 확인 완료"))
            .andExpect(jsonPath("$.result.updatedAt").value("2030-01-03T11:30:00+09:00"))
            .andExpect(jsonPath("$.result.beforePhotos.length()").value(1))
            .andExpect(jsonPath("$.result.beforePhotos[0].fileName").value("PUBLISHED.jpg"))
            .andExpect(
                jsonPath("$.result.beforePhotos[0].url")
                    .value("https://example.com/signed/published-photo"))
            .andExpect(jsonPath("$.result.photos.length()").value(1))
            .andExpect(jsonPath("$.result.photos[0].fileName").value("설치완료.jpg"))
            .andExpect(jsonPath("$.result.photos[0].uploadedAt").value("2030-01-03T11:20:00+09:00"))
            .andExpect(jsonPath("$.result.photos[0].objectKey").doesNotExist())
            .andExpect(jsonPath("$.result.workHistory[0].status").value("SCHEDULED"))
            .andExpect(jsonPath("$.result.workHistory[1].status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.result.workHistory[2].status").value("COMPLETED"))
            .andExpect(
                jsonPath("$.result.workHistory[1].changedAt").value("2030-01-03T10:00:00+09:00"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body)
        .doesNotContain("private memo", "private operator", "objectKey", "managerUserId");
    verify(storage, never()).readUrl("draft-photo");
    assertThat(jdbc.queryForList("SELECT * FROM constructions")).isEqualTo(before);
    assertThat(jdbc.queryForList("SELECT * FROM construction_status_histories"))
        .isEqualTo(historyBefore);
  }

  @Test
  void legacyCompletedRowDoesNotInventCompletionTimeOrMissingPhotos() throws Exception {
    jdbc.update(
        "UPDATE constructions SET status='COMPLETED',completed_at=NULL,final_installation_result=NULL");
    own("")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.completedAt").value(nullValue()))
        .andExpect(jsonPath("$.result.finalInstallationResult").value(nullValue()))
        .andExpect(jsonPath("$.result.photos").isEmpty())
        .andExpect(jsonPath("$.result.beforePhotos").isEmpty())
        .andExpect(jsonPath("$.result.workHistory").isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "/schedule"})
  void onlyOwningResidentCanRead(String suffix) throws Exception {
    request("900", suffix, otherToken)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("COMMON_403_1"));
    request("900", suffix, adminToken).andExpect(status().isForbidden());
    request("900", suffix, null).andExpect(status().isUnauthorized());
    request("900", suffix, "invalid-token").andExpect(status().isUnauthorized());
    verifyNoInteractions(storage);
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
  void invalidConstructionIdsAreRejected(String id) throws Exception {
    for (var suffix : List.of("", "/schedule"))
      request(id, suffix, token)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("COMMON_400_3"));
  }

  @Test
  void unknownAndOtherDomainIdsAreNotSubstituted() throws Exception {
    for (var suffix : List.of("", "/schedule")) {
      request("999999", suffix, token).andExpect(status().isNotFound());
      request(Long.toString(supportId), suffix, token).andExpect(status().isNotFound());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUBMITTED", "REJECTED", "DRAFT"})
  void unapprovedSupportIsNotPublic(String state) throws Exception {
    jdbc.update("UPDATE support_applications SET status=?", state);
    for (var suffix : List.of("", "/schedule")) {
      own(suffix)
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("CONSTRUCTION_409_1"));
      request("900", suffix, otherToken).andExpect(status().isForbidden());
    }
  }

  @Test
  void photoReadFailureReturnsStructuredErrorWithoutLeakingStorageDetails() throws Exception {
    complete();
    addConstructionPhoto();
    when(storage.readUrl(anyString()))
        .thenThrow(new IllegalStateException("secret-storage-key https://private.example.com"));
    String body =
        own("")
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("CONSTRUCTION_500_2"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body).doesNotContain("secret-storage-key", "private.example.com");
  }

  @Test
  void swaggerContainsTypedSchemasAndScreenExamplesForBothEndpoints() throws Exception {
    var doc =
        mapper.readTree(
            mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    String base = "/api/resident/constructions/{constructionId}";
    for (var suffix : List.of("", "/schedule")) {
      var success = doc.get("paths").get(base + suffix).get("get").get("responses").get("200");
      var content = success.get("content").get("application/json");
      assertThat(content.get("schema").get("$ref").asText())
          .contains("SuccessResponseResidentConstruction");
      assertThat(content.get("examples").size()).isEqualTo(2);
      var first =
          content.get("examples").get(suffix.isEmpty() ? "inProgress" : "scheduled").get("value");
      assertThat(first.isObject()).isTrue();
      assertThat(first.get("result").has("constructionId")).isTrue();
    }
    var schemas = doc.get("components").get("schemas");
    assertThat(schemas.has("ResidentConstructionPhoto")).isTrue();
    assertThat(schemas.has("ResidentConstructionWorkHistory")).isTrue();
    assertThat(
            schemas
                .get("ResidentConstructionResultResponse")
                .get("properties")
                .has("finalInstallationResult"))
        .isTrue();
  }
}
