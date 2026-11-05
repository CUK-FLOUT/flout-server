package com.flout.server.domain.construction;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:construction-completion;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false",
      "app.s3.cleanup-interval-millis=3600000"
    })
@AutoConfigureMockMvc
class ConstructionCompletionTest {
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
    jdbc.update(
        "UPDATE constructions SET status='SCHEDULED',scheduled_at='2030-01-01 10:00:00',manager_user_id=?",
        adminId);
    jdbc.update(
        "INSERT INTO construction_photos(construction_id,object_key,original_filename,file_size,content_type,created_at) VALUES(?,'construction-test-photo','완료.jpg',100,'image/jpeg',CURRENT_TIMESTAMP)",
        constructionId);
  }

  String body() {
    return "{\"constructionId\":900,\"finalInstallationResult\":\"출입구 물막이판 설치 완료\"}";
  }

  ResultActions complete(String body, String access) throws Exception {
    var request =
        post("/api/admin/constructions").contentType(MediaType.APPLICATION_JSON).content(body);
    if (access != null) request.header("Authorization", "Bearer " + access);
    return mvc.perform(request);
  }

  @Test
  void completesAndPersistsPublicResultWithoutChangingSupport() throws Exception {
    var before = jdbc.queryForList("SELECT * FROM support_applications");
    complete(body(), token)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("CONSTRUCTION_200_3"))
        .andExpect(jsonPath("$.result.constructionId").value(900))
        .andExpect(jsonPath("$.result.supportApplicationId").value(supportId))
        .andExpect(
            jsonPath("$.result.receiptNo").value(String.format(Locale.ROOT, "%06d", applicationId)))
        .andExpect(jsonPath("$.result.applicantName").value("김주민"))
        .andExpect(jsonPath("$.result.status").value("COMPLETED"))
        .andExpect(jsonPath("$.result.finalInstallationResult").value("출입구 물막이판 설치 완료"))
        .andExpect(jsonPath("$.result.completedAt").value("2030-01-02T09:30:00+09:00"));
    assertThat(
            jdbc.queryForObject(
                "SELECT final_installation_result FROM constructions", String.class))
        .isEqualTo("출입구 물막이판 설치 완료");
    assertThat(jdbc.queryForList("SELECT * FROM support_applications")).isEqualTo(before);
    complete(body().replace("설치 완료", "변경"), token)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONSTRUCTION_409_4"));
  }

  @Test
  void rejectsNoPhotosAndPhotosOnlyOnAnotherConstruction() throws Exception {
    jdbc.update("DELETE FROM construction_photos");
    complete(body(), token)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CONSTRUCTION_400_4"));
    assertThat(jdbc.queryForObject("SELECT status FROM constructions", String.class))
        .isEqualTo("SCHEDULED");
    assertThat(jdbc.queryForObject("SELECT completed_at FROM constructions", Object.class))
        .isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{",
        "{\"constructionId\":900}",
        "{\"constructionId\":0,\"finalInstallationResult\":\"완료\"}",
        "{\"constructionId\":900,\"finalInstallationResult\":null}",
        "{\"constructionId\":900,\"finalInstallationResult\":\"  \"}"
      })
  void rejectsMissingAndInvalidRequiredFields(String body) throws Exception {
    complete(body, token).andExpect(status().isBadRequest());
  }

  @Test
  void rejectsOversizedResult() throws Exception {
    complete(body().replace("출입구 물막이판 설치 완료", "가".repeat(4001)), token)
        .andExpect(status().isBadRequest());
  }

  @Test
  void acceptsMultipleStoredPhotosAndInProgress() throws Exception {
    jdbc.update("UPDATE constructions SET status='IN_PROGRESS'");
    jdbc.update(
        "INSERT INTO construction_photos(construction_id,object_key,original_filename,file_size,content_type,created_at) VALUES(?,'second','추가.png',100,'image/png',CURRENT_TIMESTAMP)",
        constructionId);
    complete(body(), token).andExpect(status().isOk());
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM construction_photos", Integer.class))
        .isEqualTo(2);
  }

  @Test
  void requiresSavedScheduleAndApprovedSupport() throws Exception {
    jdbc.update("UPDATE constructions SET status='PENDING',scheduled_at=NULL");
    complete(body(), token)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONSTRUCTION_409_5"));
    jdbc.update("UPDATE support_applications SET status='REJECTED'");
    complete(body(), token)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONSTRUCTION_409_1"));
  }

  @Test
  void validatesAuthenticationJurisdictionAndExistence() throws Exception {
    complete(body(), null).andExpect(status().isUnauthorized());
    complete(body(), "invalid").andExpect(status().isUnauthorized());
    complete(body(), residentToken).andExpect(status().isForbidden());
    complete(body().replace("900", "999999"), token).andExpect(status().isNotFound());
    jdbc.update("UPDATE support_applications SET address='서울특별시 강남구'");
    complete(body(), token)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CONSTRUCTION_403_1"));
  }

  @Test
  void rollsBackResultAndCompletionWhenPersistenceFails() throws Exception {
    doThrow(new IllegalStateException("database failure")).when(constructions).flush();
    complete(body(), token).andExpect(status().isInternalServerError());
    assertThat(jdbc.queryForObject("SELECT completed_at FROM constructions", Object.class))
        .isNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT final_installation_result FROM constructions", String.class))
        .isNull();
    assertThat(jdbc.queryForObject("SELECT status FROM constructions", String.class))
        .isEqualTo("SCHEDULED");
  }

  @Test
  void concurrentCompletionSucceedsOnlyOnce() throws Exception {
    var executor = Executors.newFixedThreadPool(2);
    try {
      var ready = new CountDownLatch(2);
      var start = new CountDownLatch(1);
      Callable<Integer> call =
          () -> {
            ready.countDown();
            start.await(10, TimeUnit.SECONDS);
            return complete(body(), token).andReturn().getResponse().getStatus();
          };
      var first = executor.submit(call);
      var second = executor.submit(call);
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void swaggerExposesCompletionOperationAndRequiredFields() throws Exception {
    var root =
        mapper.readTree(
            mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString());
    var op = root.path("paths").path("/api/admin/constructions").path("post");
    assertThat(op.path("requestBody").path("required").asBoolean()).isTrue();
    for (var code : List.of("200", "400", "401", "403", "404", "409"))
      assertThat(op.path("responses").has(code)).isTrue();
    assertThat(
            root.path("components")
                .path("schemas")
                .path("CompleteConstructionRequest")
                .path("required")
                .toString())
        .contains("constructionId", "finalInstallationResult");
  }
}
