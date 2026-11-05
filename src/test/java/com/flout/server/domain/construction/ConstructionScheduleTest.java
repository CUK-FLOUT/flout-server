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
      "spring.datasource.url=jdbc:h2:mem:construction-schedule;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false",
      "app.s3.cleanup-interval-millis=3600000"
    })
@AutoConfigureMockMvc
class ConstructionScheduleTest {
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
  }

  String body() {
    return "{\"constructionDate\":\"2030-01-03\",\"constructionTime\":\"10:00\"}";
  }

  ResultActions save(String id, String body, String access) throws Exception {
    var request =
        post("/api/admin/constructions/" + id + "/schedule")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body);
    if (access != null) request.header("Authorization", "Bearer " + access);
    return mvc.perform(request);
  }

  ResultActions save() throws Exception {
    return save(Long.toString(constructionId), body(), token);
  }

  @Test
  void savesConfirmedScheduleAndAssignsAuthenticatedOperatorWithoutChangingSupport()
      throws Exception {
    var supportBefore = jdbc.queryForList("SELECT * FROM support_applications");
    var sourceBefore = jdbc.queryForList("SELECT * FROM inspection_applications");
    save()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("CONSTRUCTION_200_1"))
        .andExpect(jsonPath("$.result.constructionId").value(constructionId))
        .andExpect(jsonPath("$.result.supportApplicationId").value(supportId))
        .andExpect(jsonPath("$.result.applicationId").value(applicationId))
        .andExpect(
            jsonPath("$.result.receiptNo").value(String.format(Locale.ROOT, "%06d", applicationId)))
        .andExpect(jsonPath("$.result.applicantName").value("김주민"))
        .andExpect(jsonPath("$.result.managerId").value(adminId))
        .andExpect(jsonPath("$.result.managerName").value("박담당"))
        .andExpect(jsonPath("$.result.constructionDate").value("2030-01-03"))
        .andExpect(jsonPath("$.result.constructionTime").value("10:00"))
        .andExpect(jsonPath("$.result.status").value("SCHEDULED"))
        .andExpect(jsonPath("$.result.updatedAt").value("2030-01-02T09:30:00+09:00"));
    assertThat(jdbc.queryForObject("SELECT scheduled_at FROM constructions", LocalDateTime.class))
        .isEqualTo(LocalDateTime.of(2030, 1, 3, 10, 0));
    assertThat(jdbc.queryForList("SELECT * FROM support_applications")).isEqualTo(supportBefore);
    assertThat(jdbc.queryForList("SELECT * FROM inspection_applications")).isEqualTo(sourceBefore);
    mvc.perform(
            get("/api/resident/support-applications/" + supportId + "/approval")
                .header("Authorization", "Bearer " + residentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.scheduledConstructionDate").value("2030-01-03"))
        .andExpect(
            jsonPath("$.result.scheduledConstructionTime")
                .value(org.hamcrest.Matchers.startsWith("10:00")));
  }

  @Test
  void rejectsRepeatedSaveAndPreservesOriginalScheduleAndAssignment() throws Exception {
    save().andExpect(status().isOk());
    var before = jdbc.queryForList("SELECT * FROM constructions");
    save()
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONSTRUCTION_409_2"));
    save(
            Long.toString(constructionId),
            body().replace("2030-01-03", "2030-01-04").replace("10:00", "15:30"),
            token)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("이미 배정되었거나 시공이 시작·완료되어 일정을 저장할 수 없습니다."));
    assertThat(jdbc.queryForList("SELECT * FROM constructions")).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(strings = {"manager", "schedule", "state"})
  void rejectsPartiallyAssignedOrScheduledConstruction(String existing) throws Exception {
    switch (existing) {
      case "manager" -> jdbc.update("UPDATE constructions SET manager_user_id=?", adminId);
      case "schedule" -> jdbc.update("UPDATE constructions SET scheduled_at='2030-01-03 10:00:00'");
      default -> jdbc.update("UPDATE constructions SET status='SCHEDULED'");
    }
    var before = jdbc.queryForList("SELECT * FROM constructions");
    save()
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONSTRUCTION_409_2"));
    assertThat(jdbc.queryForList("SELECT * FROM constructions")).isEqualTo(before);
  }

  @Test
  void anotherOperatorCannotOverwriteSavedAssignment() throws Exception {
    save().andExpect(status().isOk());
    var before = jdbc.queryForList("SELECT * FROM constructions");
    var other = users.saveAndFlush(User.resident("other@construction.test", "unused", "다른운영자"));
    jdbc.update(
        "UPDATE users SET role='ADMIN',jurisdiction_id=(SELECT jurisdiction_id FROM jurisdictions WHERE name='부천시') WHERE user_id=?",
        other.getId());
    var otherToken = jwt.issue(users.findById(other.getId()).orElseThrow()).accessToken();
    save(Long.toString(constructionId), body().replace("10:00", "11:00"), otherToken)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONSTRUCTION_409_2"));
    assertThat(jdbc.queryForList("SELECT * FROM constructions")).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUBMITTED", "REVIEWING", "CONSENT_PENDING", "SCHEDULING", "REJECTED"})
  void rejectsSupportThatIsNotApproved(String state) throws Exception {
    jdbc.update("UPDATE support_applications SET status=?", state);
    save()
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONSTRUCTION_409_1"));
    assertThat(jdbc.queryForObject("SELECT scheduled_at FROM constructions", Object.class))
        .isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"IN_PROGRESS", "COMPLETED"})
  void rejectsWorkAlreadyStartedOrCompleted(String state) throws Exception {
    jdbc.update("UPDATE constructions SET status=?", state);
    save()
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONSTRUCTION_409_2"));
  }

  @Test
  void rejectsPastCombinedTimeInSeoulAndAcceptsTheCurrentMinute() throws Exception {
    save(
            Long.toString(constructionId),
            "{\"constructionDate\":\"2030-01-02\",\"constructionTime\":\"09:29\"}",
            token)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CONSTRUCTION_400_1"));
    save(
            Long.toString(constructionId),
            "{\"constructionDate\":\"2030-01-02\",\"constructionTime\":\"09:30\"}",
            token)
        .andExpect(status().isOk());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"constructionDate\":null,\"constructionTime\":\"10:00\"}",
        "{\"constructionDate\":\"2030-02-30\",\"constructionTime\":\"10:00\"}",
        "{\"constructionDate\":\"2030-1-3\",\"constructionTime\":\"10:00\"}",
        "{\"constructionDate\":\"2030-01-03\",\"constructionTime\":\"24:00\"}",
        "{\"constructionDate\":\"2030-01-03\",\"constructionTime\":\"10:60\"}",
        "{\"constructionDate\":\"2030-01-03\",\"constructionTime\":\"10:00:30\"}",
        "{\"constructionDate\":20300103,\"constructionTime\":1000}",
        "{"
      })
  void rejectsMissingAndMalformedDatesAndTimes(String body) throws Exception {
    save(Long.toString(constructionId), body, token).andExpect(status().isBadRequest());
    assertThat(jdbc.queryForObject("SELECT status FROM constructions", String.class))
        .isEqualTo("PENDING");
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
  void rejectsInvalidConstructionIds(String id) throws Exception {
    save(id, body(), token).andExpect(status().isBadRequest());
  }

  @Test
  void supportIdCannotBeUsedInPlaceOfConstructionId() throws Exception {
    save(Long.toString(supportId), body(), token)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CONSTRUCTION_404_1"));
  }

  @Test
  void rejectsUnauthenticatedInvalidTokenAndResident() throws Exception {
    save(Long.toString(constructionId), body(), null).andExpect(status().isUnauthorized());
    save(Long.toString(constructionId), body(), "invalid").andExpect(status().isUnauthorized());
    save(Long.toString(constructionId), body(), residentToken).andExpect(status().isForbidden());
  }

  @Test
  void rejectsOperatorWithoutBucheonJurisdiction() throws Exception {
    jdbc.update("UPDATE users SET jurisdiction_id=NULL WHERE user_id=?", adminId);
    save().andExpect(status().isForbidden());
  }

  @ParameterizedTest
  @ValueSource(strings = {"서울특별시", "부천시민로 1", "경기도 김포시 부천로 1"})
  void rejectsOtherJurisdictionOrPartialCityName(String address) throws Exception {
    jdbc.update("UPDATE support_applications SET address=?", address);
    save()
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CONSTRUCTION_403_1"));
  }

  @Test
  void rollbackKeepsScheduleAndAssignmentUnchangedOnPersistenceFailure() throws Exception {
    var before = jdbc.queryForList("SELECT * FROM constructions");
    doThrow(new IllegalStateException("simulated persistence failure")).when(constructions).flush();
    save().andExpect(status().isInternalServerError());
    assertThat(jdbc.queryForList("SELECT * FROM constructions")).isEqualTo(before);
  }

  @Test
  void concurrentSavesAllowOnlyOneInitialAssignment() throws Exception {
    var pool = Executors.newFixedThreadPool(2);
    var start = new CountDownLatch(1);
    try {
      var first =
          pool.submit(
              () -> {
                start.await();
                return save().andReturn().getResponse().getStatus();
              });
      var second =
          pool.submit(
              () -> {
                start.await();
                return save(Long.toString(constructionId), body().replace("10:00", "11:00"), token)
                    .andReturn()
                    .getResponse()
                    .getStatus();
              });
      start.countDown();
      assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
      assertThat(jdbc.queryForObject("SELECT scheduled_at FROM constructions", LocalDateTime.class))
          .isIn(LocalDateTime.of(2030, 1, 3, 10, 0), LocalDateTime.of(2030, 1, 3, 11, 0));
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM constructions", Integer.class))
          .isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void swaggerDocumentsScheduleAndAuth() throws Exception {
    var response =
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse();
    var document = mapper.readTree(response.getContentAsString());
    var op =
        document.get("paths").get("/api/admin/constructions/{constructionId}/schedule").get("post");
    assertThat(op.get("summary").asString()).isEqualTo("시공 배정·일정 저장");
    for (var code : List.of("200", "400", "401", "403", "404", "409"))
      assertThat(op.get("responses").has(code)).as("Swagger HTTP %s 응답", code).isTrue();
    assertThat(
            document
                .get("components")
                .get("schemas")
                .get("SaveConstructionScheduleRequest")
                .get("required")
                .toString())
        .contains("constructionDate", "constructionTime");
  }
}
