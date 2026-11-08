package com.flout.server.domain.construction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:admin-construction-list;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false",
      "app.fcm.enabled=false",
      "app.s3.cleanup-interval-millis=3600000"
    })
@AutoConfigureMockMvc
class AdminConstructionListTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired ObjectMapper mapper;

  long residentId;
  long adminId;
  String token;
  String residentToken;

  @BeforeEach
  void setup() {
    for (var table :
        List.of(
            "construction_photos",
            "construction_status_histories",
            "constructions",
            "support_application_status_histories",
            "support_applications",
            "support_programs",
            "inspection_applications",
            "risk_assessments",
            "refresh_tokens",
            "users")) jdbc.update("DELETE FROM " + table);

    var resident = users.saveAndFlush(User.resident("resident@list.test", "unused", "김주민"));
    residentId = resident.getId();
    residentToken = jwt.issue(resident).accessToken();
    var admin = users.saveAndFlush(User.resident("admin@list.test", "unused", "박운영자"));
    adminId = admin.getId();
    jdbc.update(
        "UPDATE users SET role='ADMIN',jurisdiction_id=(SELECT jurisdiction_id FROM jurisdictions WHERE name='부천시') WHERE user_id=?",
        adminId);
    token = jwt.issue(users.findById(adminId).orElseThrow()).accessToken();
  }

  ResultActions list(String access, String keyword, int page, int size, String status)
      throws Exception {
    var request =
        get("/api/admin/constructions")
            .param("keyword", keyword)
            .param("page", Integer.toString(page))
            .param("size", Integer.toString(size));
    if (status != null) request.param("status", status);
    if (access != null) request.header("Authorization", "Bearer " + access);
    return mvc.perform(request);
  }

  Seed add(String name, String address, String status, String scheduledAt) {
    var unique = UUID.randomUUID().toString().substring(0, 20);
    jdbc.update(
        "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status) VALUES(?,?,'101호','HIGH','COMPLETED')",
        residentId,
        address);
    long riskId =
        jdbc.queryForObject("SELECT MAX(assessment_id) FROM risk_assessments", Long.class);
    jdbc.update(
        "INSERT INTO inspection_applications(applicant_name,applicant_user_id,assessment_id,receipt_no,address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,status) VALUES(?,?,?,? ,?,37.5,126.76,'010-1234-5678','OWNER','NO',FALSE,'RECEIVED')",
        name,
        residentId,
        riskId,
        unique,
        address);
    long applicationId =
        jdbc.queryForObject("SELECT MAX(application_id) FROM inspection_applications", Long.class);
    jdbc.update(
        "INSERT INTO support_programs(name,created_at) VALUES(?,CURRENT_TIMESTAMP)", unique);
    long programId =
        jdbc.queryForObject("SELECT MAX(program_id) FROM support_programs", Long.class);
    jdbc.update(
        "INSERT INTO support_applications(application_id,program_id,applicant_user_id,requested_facility,applicant_name,contact,address,occupancy_type,preferred_visit_date,preferred_visit_time,status,submitted_at,landlord_consent_status,support_scope) VALUES(?,?,?,'출입구 물막이판',?,'010-1234-5678',?,'OWNER','2030-02-01','MORNING','APPROVED',CURRENT_TIMESTAMP,'NOT_REQUIRED','물막이판 설치')",
        applicationId,
        programId,
        residentId,
        name,
        address);
    long supportId =
        jdbc.queryForObject(
            "SELECT MAX(support_application_id) FROM support_applications", Long.class);
    jdbc.update(
        "INSERT INTO constructions(support_application_id,status,scheduled_at,created_at,updated_at) VALUES(?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        supportId,
        status,
        scheduledAt);
    long constructionId =
        jdbc.queryForObject("SELECT MAX(construction_id) FROM constructions", Long.class);
    return new Seed(constructionId, applicationId);
  }

  @Test
  void returnsDefaultPageWithExpectedFieldsWithoutChangingData() throws Exception {
    var seed = add("김주민", "경기도 부천시 원미구 길주로 210", "SCHEDULED", "2030-02-03 10:30:00");
    var before = jdbc.queryForList("SELECT * FROM constructions");

    mvc.perform(get("/api/admin/constructions").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.result.length()").value(5))
        .andExpect(jsonPath("$.result.page").value(0))
        .andExpect(jsonPath("$.result.size").value(10))
        .andExpect(jsonPath("$.result.totalElements").value(1))
        .andExpect(jsonPath("$.result.totalPages").value(1))
        .andExpect(jsonPath("$.result.constructions.length()").value(1))
        .andExpect(jsonPath("$.result.constructions[0].length()").value(7))
        .andExpect(
            jsonPath("$.result.constructions[0].constructionId").value(seed.constructionId()))
        .andExpect(
            jsonPath("$.result.constructions[0].receiptNo")
                .value(String.format(Locale.ROOT, "%06d", seed.applicationId())))
        .andExpect(jsonPath("$.result.constructions[0].applicantName").value("김주민"))
        .andExpect(jsonPath("$.result.constructions[0].requestedFacility").value("출입구 물막이판"))
        .andExpect(
            jsonPath("$.result.constructions[0].scheduledConstructionDate").value("2030-02-03"))
        .andExpect(
            jsonPath("$.result.constructions[0].scheduledConstructionTime").value("10:30:00"))
        .andExpect(jsonPath("$.result.constructions[0].status").value("SCHEDULED"));

    assertThat(jdbc.queryForList("SELECT * FROM constructions")).isEqualTo(before);
  }

  @Test
  void searchesByApplicantNameAndDisplayedReceiptNumberAndReturnsEmpty() throws Exception {
    var first = add("김검색주민", "부천시 소사구", "PENDING", null);
    add("박다른주민", "부천시 오정구", "COMPLETED", "2030-01-01 09:00:00");

    list(token, "  김검색  ", 0, 10, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalElements").value(1))
        .andExpect(
            jsonPath("$.result.constructions[0].constructionId").value(first.constructionId()));
    list(token, String.format(Locale.ROOT, "%06d", first.applicationId()), 0, 10, null)
        .andExpect(jsonPath("$.result.totalElements").value(1))
        .andExpect(
            jsonPath("$.result.constructions[0].constructionId").value(first.constructionId()));
    list(token, "없는주민", 0, 10, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.constructions").isEmpty())
        .andExpect(jsonPath("$.result.totalElements").value(0))
        .andExpect(jsonPath("$.result.totalPages").value(0));
  }

  @Test
  void scheduledFilterRepresentsPendingAndScheduledTabs() throws Exception {
    add("미배정", "부천시 원미구", "PENDING", null);
    add("일정확정", "부천시 원미구", "SCHEDULED", "2030-01-02 10:00:00");
    add("진행중", "부천시 원미구", "IN_PROGRESS", "2030-01-01 10:00:00");
    add("완료", "부천시 원미구", "COMPLETED", "2030-01-01 09:00:00");

    list(token, "", 0, 10, "SCHEDULED")
        .andExpect(jsonPath("$.result.totalElements").value(2))
        .andExpect(
            jsonPath(
                "$.result.constructions[*].status",
                org.hamcrest.Matchers.containsInAnyOrder("PENDING", "SCHEDULED")));
    list(token, "", 0, 10, "IN_PROGRESS")
        .andExpect(jsonPath("$.result.totalElements").value(1))
        .andExpect(jsonPath("$.result.constructions[0].status").value("IN_PROGRESS"));
    list(token, "", 0, 10, "COMPLETED")
        .andExpect(jsonPath("$.result.totalElements").value(1))
        .andExpect(jsonPath("$.result.constructions[0].status").value("COMPLETED"));
  }

  @Test
  void paginatesInStableConstructionIdDescendingOrder() throws Exception {
    var expected = new ArrayList<Long>();
    for (int i = 0; i < 12; i++)
      expected.add(add("페이지주민" + i, "부천시 원미구", "PENDING", null).constructionId());
    Collections.reverse(expected);

    var actual = new ArrayList<Long>();
    for (int page = 0; page < 3; page++) {
      var response =
          list(token, "페이지주민", page, 5, null)
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.result.page").value(page))
              .andExpect(jsonPath("$.result.totalElements").value(12))
              .andExpect(jsonPath("$.result.totalPages").value(3))
              .andReturn();
      for (var item :
          mapper
              .readTree(response.getResponse().getContentAsString())
              .path("result")
              .path("constructions")) actual.add(item.path("constructionId").asLong());
    }
    assertThat(actual).containsExactlyElementsOf(expected).doesNotHaveDuplicates();
    list(token, "페이지주민", 99, 5, null)
        .andExpect(jsonPath("$.result.constructions").isEmpty())
        .andExpect(jsonPath("$.result.totalElements").value(12));
  }

  @Test
  void limitsRowsAndTotalsToBucheonJurisdiction() throws Exception {
    add("관할내", "경기 부천시 오정구", "PENDING", null);
    for (var address : List.of("서울시 부천시", "부천시청", "부천시민로 1", "경기도 김포시 부천로 1"))
      add("관할외", address, "PENDING", null);

    list(token, "", 0, 10, null)
        .andExpect(jsonPath("$.result.totalElements").value(1))
        .andExpect(jsonPath("$.result.constructions[0].applicantName").value("관할내"));
  }

  @Test
  void validatesParametersAuthenticationAndOperatorJurisdiction() throws Exception {
    for (var values :
        List.of(
            new String[] {"page", "-1"},
            new String[] {"size", "0"},
            new String[] {"size", "101"},
            new String[] {"status", "PENDING"},
            new String[] {"status", "UNKNOWN"},
            new String[] {"keyword", "가".repeat(256)}))
      mvc.perform(
              get("/api/admin/constructions")
                  .header("Authorization", "Bearer " + token)
                  .param(values[0], values[1]))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("COMMON_400_3"));

    list(null, "", 0, 10, null).andExpect(status().isUnauthorized());
    list("invalid", "", 0, 10, null).andExpect(status().isUnauthorized());
    list(residentToken, "", 0, 10, null).andExpect(status().isForbidden());
    jdbc.update("UPDATE users SET jurisdiction_id=NULL WHERE user_id=?", adminId);
    list(token, "", 0, 10, null).andExpect(status().isForbidden());
  }

  @Test
  void swaggerPublishesListSearchContract() throws Exception {
    var response = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
    var operation =
        mapper
            .readTree(response.getResponse().getContentAsString())
            .path("paths")
            .path("/api/admin/constructions")
            .path("get");
    assertThat(operation.path("summary").asString()).isEqualTo("시공 대상 목록 조회·검색");
    var parameters = new ArrayList<String>();
    for (var parameter : operation.path("parameters"))
      parameters.add(parameter.path("name").asString());
    assertThat(parameters).containsExactlyInAnyOrder("keyword", "page", "size", "status");
  }

  record Seed(long constructionId, long applicationId) {}
}
