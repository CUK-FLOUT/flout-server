package com.flout.server.domain.supportapplication;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.supportapplication.repository.SupportApplicationStatusHistoryRepository;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import java.time.Instant;
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
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:support-rejection;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false",
      "app.s3.cleanup-interval-millis=3600000"
    })
@AutoConfigureMockMvc
class SupportRejectionTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired ObjectMapper mapper;
  @Autowired JwtEncoder encoder;
  @MockitoSpyBean SupportApplicationStatusHistoryRepository histories;
  long supportId, adminId, residentId;
  String token, residentToken;
  static final String SCOPE = "출입구 물막이판 설치 지원";

  @BeforeEach
  void setup() {
    reset(histories);
    for (var table :
        List.of(
            "support_attachments",
            "support_application_status_histories",
            "support_applications",
            "support_programs",
            "inspection_results",
            "inspection_status_histories",
            "inspections",
            "application_status_histories",
            "inspection_applications",
            "risk_assessments",
            "refresh_tokens",
            "users")) jdbc.update("DELETE FROM " + table);
    var resident =
        users.saveAndFlush(
            User.resident("rejection-resident@example.test", "unused-test-hash", "테스트주민"));
    residentId = resident.getId();
    residentToken = jwt.issue(resident).accessToken();
    var admin =
        users.saveAndFlush(
            User.resident("rejection-admin@example.test", "unused-test-hash", "테스트운영자"));
    adminId = admin.getId();
    jdbc.update(
        "UPDATE users SET role='ADMIN',jurisdiction_id=(SELECT jurisdiction_id FROM jurisdictions WHERE name='부천시') WHERE user_id=?",
        adminId);
    token = jwt.issue(users.findById(adminId).orElseThrow()).accessToken();
    jdbc.update(
        "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status) VALUES(?,'경기도 부천시 원미구 길주로 210','fixture','HIGH','COMPLETED')",
        residentId);
    var risk = jdbc.queryForObject("SELECT assessment_id FROM risk_assessments", Long.class);
    jdbc.update(
        "INSERT INTO inspection_applications(applicant_name,applicant_user_id,assessment_id,receipt_no,address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,status) VALUES('테스트주민',?,?,'APPROVAL-TEST','경기도 부천시 원미구 길주로 210',37.5,126.76,'010-0000-0000','OWNER','NO',FALSE,'RECEIVED')",
        residentId,
        risk);
    var source =
        jdbc.queryForObject("SELECT application_id FROM inspection_applications", Long.class);
    jdbc.update("INSERT INTO inspections(application_id,status) VALUES(?,'IN_PROGRESS')", source);
    var inspection = jdbc.queryForObject("SELECT inspection_id FROM inspections", Long.class);
    jdbc.update(
        "INSERT INTO inspection_results(inspection_id,result_status,summary) VALUES(?,'DRAFT','preserved')",
        inspection);
    jdbc.update(
        "INSERT INTO application_status_histories(application_id,status) VALUES(?,'RECEIVED')",
        source);
    jdbc.update(
        "INSERT INTO support_programs(name,created_at) VALUES('검증용 지원사업',CURRENT_TIMESTAMP)");
    var program = jdbc.queryForObject("SELECT program_id FROM support_programs", Long.class);
    jdbc.update(
        "INSERT INTO support_applications(application_id,program_id,applicant_user_id,requested_facility,applicant_name,contact,address,occupancy_type,preferred_visit_date,preferred_visit_time,status,submitted_at,landlord_consent_status) VALUES(?,?,?,'출입구 물막이판','테스트주민','010-0000-0000','경기도 부천시 원미구 길주로 210','OWNER',CURRENT_DATE,'ANY','SUBMITTED',CURRENT_TIMESTAMP,'NOT_REQUIRED')",
        source,
        program,
        residentId);
    supportId =
        jdbc.queryForObject("SELECT support_application_id FROM support_applications", Long.class);
  }

  Map<String, Object> body() {
    var result = new LinkedHashMap<String, Object>();
    result.put("reason", "자격 미달");
    return result;
  }

  Map<String, Object> approvalBody() {
    return Map.of(
        "supportScope",
        SCOPE,
        "applicantHousingReviewed",
        true,
        "expertInspectionReviewed",
        true,
        "eligibilityDocumentsReviewed",
        true);
  }

  ResultActions send(long id, Map<String, Object> request, String access) throws Exception {
    return sendTo("reject", id, request, access);
  }

  ResultActions sendTo(String action, long id, Map<String, Object> request, String access)
      throws Exception {
    var call =
        post("/api/admin/support-applications/" + id + "/" + action)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(request));
    if (access != null) call.header("Authorization", "Bearer " + access);
    return mvc.perform(call);
  }

  List<Object> snapshot() {
    return List.of(
        jdbc.queryForList("SELECT * FROM inspection_applications"),
        jdbc.queryForList("SELECT * FROM inspections"),
        jdbc.queryForList("SELECT * FROM inspection_results"),
        jdbc.queryForList("SELECT * FROM application_status_histories"),
        jdbc.queryForList("SELECT * FROM inspection_status_histories"),
        jdbc.queryForList(
            "SELECT requested_facility,applicant_name,contact,address,occupancy_type,landlord_consent_status,preferred_visit_date,preferred_visit_time FROM support_applications"));
  }

  void unchanged() {
    assertThat(jdbc.queryForObject("SELECT status FROM support_applications", String.class))
        .isEqualTo("SUBMITTED");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM support_application_status_histories", Integer.class))
        .isZero();
    var row =
        jdbc.queryForMap(
            "SELECT rejection_reason,support_scope,approved_at,approved_by_user_id FROM support_applications");
    assertThat(row.values()).containsOnlyNulls();
  }

  @ParameterizedTest
  @org.junit.jupiter.params.provider.CsvSource({
    "OWNER,NOT_REQUIRED", "OWNER,PENDING", "OWNER,CONFIRMED",
    "TENANT,NOT_REQUIRED", "TENANT,PENDING", "TENANT,CONFIRMED",
    "ETC,NOT_REQUIRED", "ETC,PENDING", "ETC,CONFIRMED"
  })
  void rejectsAnyOccupancyWithoutApprovalPrerequisites(String occupancy, String consent)
      throws Exception {
    jdbc.update(
        "UPDATE support_applications SET occupancy_type=?,landlord_consent_status=?",
        occupancy,
        consent);
    var before = snapshot();
    var request = body();
    request.put("reason", "  자격 미달  ");
    send(supportId, request, token)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(200))
        .andExpect(jsonPath("$.code").value("SUPPORT_200_2"))
        .andExpect(jsonPath("$.result.supportApplicationId").value(supportId))
        .andExpect(jsonPath("$.result.status").value("REJECTED"))
        .andExpect(jsonPath("$.result.reason").value("자격 미달"))
        .andExpect(jsonPath("$.result.processedByUserId").value(adminId))
        .andExpect(
            jsonPath("$.result.processedAt").value(org.hamcrest.Matchers.endsWith("+09:00")));
    var row = jdbc.queryForMap("SELECT * FROM support_applications");
    var history = jdbc.queryForMap("SELECT * FROM support_application_status_histories");
    assertThat(row.get("STATUS")).isEqualTo("REJECTED");
    assertThat(row.get("REJECTION_REASON")).isEqualTo("자격 미달");
    assertThat(row.get("APPROVED_BY_USER_ID")).isEqualTo(adminId);
    assertThat(row.get("APPROVED_AT")).isNotNull();
    assertThat(row.get("SUPPORT_SCOPE")).isNull();
    assertThat(history.get("PREVIOUS_STATUS")).isEqualTo("SUBMITTED");
    assertThat(history.get("STATUS")).isEqualTo("REJECTED");
    assertThat(history.get("REJECTION_REASON")).isEqualTo("자격 미달");
    assertThat(history.get("CHANGED_BY_USER_ID")).isEqualTo(adminId);
    assertThat(history.get("CHANGED_AT")).isEqualTo(row.get("APPROVED_AT"));
    assertThat(history.get("SUPPORT_SCOPE")).isNull();
    assertThat(snapshot()).isEqualTo(before);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM support_attachments", Integer.class))
        .isZero();
  }

  @Test
  void rejectsMissingNullBlankLongAndNonStringReason() throws Exception {
    send(supportId, Map.of(), token).andExpect(status().isBadRequest());
    var request = body();
    request.put("reason", null);
    send(supportId, request, token).andExpect(status().isBadRequest());
    for (Object value :
        List.of(
            "",
            " \t\n ",
            "가".repeat(501),
            123,
            true,
            List.of("자격 미달"),
            Map.of("reason", "자격 미달"))) {
      request.put("reason", value);
      send(supportId, request, token).andExpect(status().isBadRequest());
    }
    unchanged();
  }

  @Test
  void acceptsExactly500Characters() throws Exception {
    var request = body();
    request.put("reason", "가".repeat(500));
    send(supportId, request, token).andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject("SELECT rejection_reason FROM support_applications", String.class))
        .hasSize(500);
    assertThat(
            jdbc.queryForObject(
                "SELECT rejection_reason FROM support_application_status_histories", String.class))
        .hasSize(500);
  }

  @Test
  void rejectsAlreadyApprovedAndKeepsItsMetadata() throws Exception {
    sendTo("approve", supportId, approvalBody(), token).andExpect(status().isOk());
    var before = jdbc.queryForList("SELECT * FROM support_applications");
    var history = jdbc.queryForList("SELECT * FROM support_application_status_histories");
    send(supportId, body(), token).andExpect(status().isConflict());
    assertThat(jdbc.queryForList("SELECT * FROM support_applications")).isEqualTo(before);
    assertThat(jdbc.queryForList("SELECT * FROM support_application_status_histories"))
        .isEqualTo(history);
  }

  @Test
  void repeatedRejectionAndApprovalAfterRejectionAre409() throws Exception {
    send(supportId, body(), token).andExpect(status().isOk());
    var before = jdbc.queryForList("SELECT * FROM support_applications");
    var request = body();
    request.put("reason", "다른 사유");
    send(supportId, request, token).andExpect(status().isConflict());
    sendTo("approve", supportId, approvalBody(), token).andExpect(status().isConflict());
    assertThat(jdbc.queryForList("SELECT * FROM support_applications")).isEqualTo(before);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM support_application_status_histories", Integer.class))
        .isEqualTo(1);
  }

  @RepeatedTest(3)
  void concurrentApprovalAndRejectionCommitOnlyOneDecision() throws Exception {
    race("approve");
  }

  @Test
  void concurrentRejectionsCommitOnlyOnce() throws Exception {
    race("reject");
  }

  void race(String otherAction) throws Exception {
    var pool = Executors.newFixedThreadPool(2);
    var start = new CountDownLatch(1);
    try {
      var a =
          pool.submit(
              () -> {
                start.await();
                return send(supportId, body(), token).andReturn().getResponse().getStatus();
              });
      var b =
          pool.submit(
              () -> {
                start.await();
                return sendTo(
                        otherAction,
                        supportId,
                        otherAction.equals("approve") ? approvalBody() : body(),
                        token)
                    .andReturn()
                    .getResponse()
                    .getStatus();
              });
      start.countDown();
      assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
      var row = jdbc.queryForMap("SELECT * FROM support_applications");
      var history = jdbc.queryForMap("SELECT * FROM support_application_status_histories");
      assertThat(history.get("STATUS")).isEqualTo(row.get("STATUS"));
      assertThat(history.get("REJECTION_REASON")).isEqualTo(row.get("REJECTION_REASON"));
      assertThat(history.get("SUPPORT_SCOPE")).isEqualTo(row.get("SUPPORT_SCOPE"));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void rejectsMissingInvalidIdsAndAuthentication() throws Exception {
    send(supportId, body(), null).andExpect(status().isUnauthorized());
    send(supportId, body(), "invalid-test-token").andExpect(status().isUnauthorized());
    send(supportId, body(), residentToken).andExpect(status().isForbidden());
    send(Long.MAX_VALUE, body(), token).andExpect(status().isNotFound());
    send(0, body(), token).andExpect(status().isBadRequest());
    send(-1, body(), token).andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/admin/support-applications/invalid/reject")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body())))
        .andExpect(status().isBadRequest());
    unchanged();
  }

  @Test
  void rejectsExpiredAdminToken() throws Exception {
    var claims =
        JwtClaimsSet.builder()
            .issuer("flout")
            .subject(Long.toString(adminId))
            .claim("role", "ADMIN")
            .issuedAt(Instant.now().minusSeconds(120))
            .expiresAt(Instant.now().minusSeconds(60))
            .build();
    var expired =
        encoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    send(supportId, body(), expired).andExpect(status().isUnauthorized());
    unchanged();
  }

  @Test
  void rejectsOperatorWithoutJurisdiction() throws Exception {
    jdbc.update("UPDATE users SET jurisdiction_id=NULL WHERE user_id=?", adminId);
    send(supportId, body(), token).andExpect(status().isForbidden());
    unchanged();
  }

  @ParameterizedTest
  @ValueSource(strings = {"서울특별시 구로구", "경기도 부천시민로 1", "경기도 김포시 부천로 1"})
  void rejectsAddressOutsideBucheon(String address) throws Exception {
    jdbc.update("UPDATE support_applications SET address=?", address);
    send(supportId, body(), token).andExpect(status().isForbidden());
    unchanged();
  }

  @Test
  void rollsBackStateAndMetadataWhenHistoryFails() throws Exception {
    var before = snapshot();
    doAnswer(
            invocation -> {
              invocation.callRealMethod();
              throw new IllegalStateException("simulated failure after history flush");
            })
        .when(histories)
        .saveAndFlush(any());
    send(supportId, body(), token).andExpect(status().isInternalServerError());
    unchanged();
    assertThat(snapshot()).isEqualTo(before);
  }

  @Test
  void documentsRejectionAndSwaggerUi() throws Exception {
    String operation =
        "$.paths['/api/admin/support-applications/{supportApplicationId}/reject'].post";
    var result =
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.security[0].bearerAuth").exists())
            .andExpect(
                jsonPath(
                        operation
                            + ".requestBody.content['application/json'].examples.rejection.value.reason")
                    .value("자격 미달"))
            .andExpect(
                jsonPath("$.components.schemas.RejectSupportApplicationRequest.required")
                    .value(org.hamcrest.Matchers.contains("reason")))
            .andExpect(
                jsonPath(
                        "$.components.schemas.RejectSupportApplicationRequest.properties.reason.maxLength")
                    .value(500))
            .andExpect(
                jsonPath(
                        operation
                            + ".responses['200'].content['application/json'].examples.rejected.value.result.status")
                    .value("REJECTED"))
            .andExpect(
                jsonPath(
                        operation
                            + ".responses['400'].content['application/json'].examples.CONSENT_NOT_CONFIRMED")
                    .doesNotExist());
    for (var code : List.of("200", "400", "401", "403", "404", "409"))
      result.andExpect(
          jsonPath(operation + ".responses['" + code + "'].content['application/json']").exists());
    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    mvc.perform(get("/v3/api-docs/swagger-config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").value("/v3/api-docs"));
  }
}
