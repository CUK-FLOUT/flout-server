package com.flout.server.domain.supportapplication;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.inspectionapplication.service.AttachmentStorage;
import com.flout.server.domain.supportapplication.repository.SupportApplicationRepository;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:landlord-consent;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false",
      "app.s3.cleanup-interval-millis=3600000"
    })
@AutoConfigureMockMvc
class LandlordConsentTest {
  @MockitoBean AttachmentStorage storage;
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired ObjectMapper mapper;
  @Autowired JwtEncoder encoder;
  @MockitoSpyBean SupportApplicationRepository applications;
  long supportId, adminId, residentId;
  String token, residentToken;
  static final String SCOPE = "출입구 물막이판 설치 지원";

  @BeforeEach
  void setup() {
    reset(applications);
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
            User.resident("consent-resident@example.test", "unused-test-hash", "테스트주민"));
    residentId = resident.getId();
    residentToken = jwt.issue(resident).accessToken();
    var admin =
        users.saveAndFlush(
            User.resident("consent-admin@example.test", "unused-test-hash", "테스트운영자"));
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
        "INSERT INTO support_applications(application_id,program_id,applicant_user_id,requested_facility,applicant_name,contact,address,occupancy_type,preferred_visit_date,preferred_visit_time,status,submitted_at,landlord_consent_status) VALUES(?,?,?,'출입구 물막이판','테스트주민','010-0000-0000','경기도 부천시 원미구 길주로 210','TENANT',CURRENT_DATE,'ANY','SUBMITTED',CURRENT_TIMESTAMP,'PENDING')",
        source,
        program,
        residentId);
    supportId =
        jdbc.queryForObject("SELECT support_application_id FROM support_applications", Long.class);
  }

  @Test
  void uploadRemainsPendingUntilOperatorConfirmsAndThenApproves() throws Exception {
    byte[] bytes;
    try (var pdf = new PDDocument();
        var out = new ByteArrayOutputStream()) {
      pdf.addPage(new PDPage());
      pdf.save(out);
      bytes = out.toByteArray();
    }
    mvc.perform(
            multipart("/api/resident/support-applications/" + supportId + "/attachments")
                .header("Authorization", "Bearer " + residentToken)
                .file(new MockMultipartFile("file", "consent.pdf", "application/pdf", bytes)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.result.attachmentType").value("LANDLORD_CONSENT"));
    assertThat(
            jdbc.queryForObject(
                "SELECT landlord_consent_status FROM support_applications", String.class))
        .isEqualTo("PENDING");
    confirm().andExpect(status().isOk());
    decide("approve", approvalBody()).andExpect(status().isOk());
    verify(storage).upload(anyString(), any(byte[].class), eq("application/pdf"));
  }

  String path(long id) {
    return "/api/admin/support-applications/" + id + "/landlord-consent";
  }

  ResultActions send(long id, String body, String access) throws Exception {
    var request = patch(path(id)).contentType(MediaType.APPLICATION_JSON).content(body);
    if (access != null) request.header("Authorization", "Bearer " + access);
    return mvc.perform(request);
  }

  ResultActions confirm() throws Exception {
    return send(supportId, "{\"status\":\"CONFIRMED\"}", token);
  }

  void document() {
    jdbc.update(
        "INSERT INTO support_attachments(support_application_id,attachment_type,object_key,original_filename,file_size,content_type,submitted_at) VALUES(?,'LANDLORD_CONSENT','isolated-fixture/no-s3-upload','consent.pdf',1,'application/pdf',CURRENT_TIMESTAMP)",
        supportId);
  }

  Object relatedSnapshot() {
    return List.of(
        jdbc.queryForList("SELECT * FROM inspection_applications"),
        jdbc.queryForList("SELECT * FROM inspections"),
        jdbc.queryForList("SELECT * FROM inspection_results"),
        jdbc.queryForList("SELECT * FROM application_status_histories"),
        jdbc.queryForList("SELECT * FROM inspection_status_histories"),
        jdbc.queryForList("SELECT * FROM support_application_status_histories"),
        jdbc.queryForList("SELECT * FROM support_attachments"));
  }

  Object supportSnapshot() {
    return jdbc.queryForList("SELECT * FROM support_applications");
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

  ResultActions decide(String action, Map<String, Object> body) throws Exception {
    return mvc.perform(
        post("/api/admin/support-applications/" + supportId + "/" + action)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(body)));
  }

  @Test
  void confirmsOnlyConsentAndPreservesAllOtherData() throws Exception {
    document();
    var before = new LinkedHashMap<>(jdbc.queryForMap("SELECT * FROM support_applications"));
    var related = relatedSnapshot();
    confirm()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("SUPPORT_200_3"))
        .andExpect(jsonPath("$.result.supportApplicationId").value(supportId))
        .andExpect(jsonPath("$.result.landlordConsentStatus").value("CONFIRMED"));
    before.put("LANDLORD_CONSENT_STATUS", "CONFIRMED");
    assertThat(jdbc.queryForMap("SELECT * FROM support_applications")).isEqualTo(before);
    assertThat(relatedSnapshot()).isEqualTo(related);
  }

  @Test
  void requiresSubmittedDocument() throws Exception {
    var before = supportSnapshot();
    confirm()
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("LANDLORD_CONSENT_400_2"));
    assertThat(supportSnapshot()).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(strings = {"OWNER", "ETC"})
  void rejectsNonTenantEvenWithDocument(String occupancy) throws Exception {
    document();
    jdbc.update("UPDATE support_applications SET occupancy_type=?", occupancy);
    var before = supportSnapshot();
    confirm()
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("LANDLORD_CONSENT_400_1"));
    assertThat(supportSnapshot()).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(strings = {"CONFIRMED", "NOT_REQUIRED"})
  void rejectsAnyCurrentStatusExceptPending(String consent) throws Exception {
    document();
    jdbc.update("UPDATE support_applications SET landlord_consent_status=?", consent);
    var before = supportSnapshot();
    confirm()
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("LANDLORD_CONSENT_409_1"));
    assertThat(supportSnapshot()).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(strings = {"APPROVED", "REJECTED"})
  void rejectsDecidedApplications(String state) throws Exception {
    document();
    jdbc.update("UPDATE support_applications SET status=?", state);
    var before = supportSnapshot();
    confirm()
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SUPPORT_APPROVAL_409_1"));
    assertThat(supportSnapshot()).isEqualTo(before);
  }

  @Test
  void rejectsRepeatAfterSuccessfulConfirmation() throws Exception {
    document();
    confirm().andExpect(status().isOk());
    var before = supportSnapshot();
    confirm().andExpect(status().isConflict());
    assertThat(supportSnapshot()).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"status\":null}",
        "{\"status\":\"PENDING\"}",
        "{\"status\":\"NOT_REQUIRED\"}",
        "{\"status\":\"invalid\"}",
        "{\"status\":2}",
        "{\"status\":true}",
        "{\"status\":[]}",
        "{\"status\":{}}",
        "{\"status\":\"confirmed\"}",
        "{\"status\":\" CONFIRMED \"}"
      })
  void rejectsInvalidTargetStatusWithoutChangingAnything(String body) throws Exception {
    document();
    var before = supportSnapshot();
    send(supportId, body, token).andExpect(status().isBadRequest());
    assertThat(supportSnapshot()).isEqualTo(before);
  }

  @Test
  void rejectsMissingBodyAndMalformedJson() throws Exception {
    for (var body : List.of("", "{"))
      send(supportId, body, token).andExpect(status().isBadRequest());
  }

  @Test
  void enforcesAuthenticationRoleAndApplicationExistence() throws Exception {
    var body = "{\"status\":\"CONFIRMED\"}";
    send(supportId, body, null).andExpect(status().isUnauthorized());
    send(supportId, body, "invalid-test-token").andExpect(status().isUnauthorized());
    send(supportId, body, residentToken).andExpect(status().isForbidden());
    send(Long.MAX_VALUE, body, token)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SUPPORT_APPROVAL_404_1"));
    send(0, body, token).andExpect(status().isBadRequest());
    send(-1, body, token).andExpect(status().isBadRequest());
    mvc.perform(
            patch("/api/admin/support-applications/invalid/landlord-consent")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejectsExpiredToken() throws Exception {
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
    send(supportId, "{\"status\":\"CONFIRMED\"}", expired).andExpect(status().isUnauthorized());
  }

  @Test
  void invalidatesPreviouslyIssuedAdminJwtAfterDatabaseRoleChange() throws Exception {
    jdbc.update("UPDATE users SET role='RESIDENT' WHERE user_id=?", adminId);
    confirm().andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsMissingJurisdiction() throws Exception {
    jdbc.update("UPDATE users SET jurisdiction_id=NULL WHERE user_id=?", adminId);
    confirm().andExpect(status().isForbidden());
  }

  @ParameterizedTest
  @ValueSource(strings = {"서울특별시 구로구", "경기도 부천시민로 1", "경기도 김포시 부천로 1"})
  void rejectsApplicationOutsideJurisdiction(String address) throws Exception {
    jdbc.update("UPDATE support_applications SET address=?", address);
    confirm()
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("SUPPORT_APPROVAL_403_1"));
  }

  @Test
  void enablesExistingTenantApprovalWithoutBypassingChecklist() throws Exception {
    document();
    decide("approve", approvalBody()).andExpect(status().isBadRequest());
    confirm().andExpect(status().isOk());
    var incomplete = new LinkedHashMap<>(approvalBody());
    incomplete.put("eligibilityDocumentsReviewed", false);
    decide("approve", incomplete).andExpect(status().isBadRequest());
    decide("approve", approvalBody()).andExpect(status().isOk());
    assertThat(jdbc.queryForObject("SELECT status FROM support_applications", String.class))
        .isEqualTo("APPROVED");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM support_application_status_histories", Integer.class))
        .isEqualTo(1);
    confirm().andExpect(status().isConflict());
  }

  @Test
  void rejectionStillWorksWithoutDocumentOrConfirmation() throws Exception {
    decide("reject", Map.of("reason", "자격 미달")).andExpect(status().isOk());
    confirm().andExpect(status().isConflict());
    assertThat(
            jdbc.queryForObject(
                "SELECT landlord_consent_status FROM support_applications", String.class))
        .isEqualTo("PENDING");
  }

  @Test
  void rollsBackIfPersistenceFailsAfterFlush() throws Exception {
    document();
    var before = supportSnapshot();
    doAnswer(
            invocation -> {
              invocation.callRealMethod();
              throw new IllegalStateException("simulated failure after consent flush");
            })
        .when(applications)
        .saveAndFlush(any());
    confirm().andExpect(status().isInternalServerError());
    assertThat(supportSnapshot()).isEqualTo(before);
  }

  @RepeatedTest(3)
  void concurrentConfirmationsCommitOnlyOnce() throws Exception {
    document();
    assertThat(race(() -> confirm().andReturn().getResponse().getStatus()))
        .containsExactlyInAnyOrder(200, 409);
    assertThat(
            jdbc.queryForObject(
                "SELECT landlord_consent_status FROM support_applications", String.class))
        .isEqualTo("CONFIRMED");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM support_application_status_histories", Integer.class))
        .isZero();
  }

  @RepeatedTest(3)
  void concurrentConfirmationAndRejectionStayConsistent() throws Exception {
    document();
    var statuses =
        race(
            () ->
                decide("reject", Map.of("reason", "자격 미달")).andReturn().getResponse().getStatus());
    assertThat(statuses.get(1)).isEqualTo(200);
    assertThat(statuses.get(0)).isIn(200, 409);
    assertThat(jdbc.queryForObject("SELECT status FROM support_applications", String.class))
        .isEqualTo("REJECTED");
    assertThat(
            jdbc.queryForObject(
                "SELECT landlord_consent_status FROM support_applications", String.class))
        .isEqualTo(statuses.get(0) == 200 ? "CONFIRMED" : "PENDING");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM support_application_status_histories", Integer.class))
        .isEqualTo(1);
  }

  @RepeatedTest(3)
  void concurrentConfirmationAndApprovalStayConsistent() throws Exception {
    document();
    var statuses =
        race(() -> decide("approve", approvalBody()).andReturn().getResponse().getStatus());
    assertThat(statuses.get(0)).isEqualTo(200);
    assertThat(statuses.get(1)).isIn(200, 400);
    assertThat(
            jdbc.queryForObject(
                "SELECT landlord_consent_status FROM support_applications", String.class))
        .isEqualTo("CONFIRMED");
    assertThat(jdbc.queryForObject("SELECT status FROM support_applications", String.class))
        .isEqualTo(statuses.get(1) == 200 ? "APPROVED" : "SUBMITTED");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM support_application_status_histories", Integer.class))
        .isEqualTo(statuses.get(1) == 200 ? 1 : 0);
  }

  List<Integer> race(Callable<Integer> other) throws Exception {
    var pool = Executors.newFixedThreadPool(2);
    var start = new CountDownLatch(1);
    try {
      var first =
          pool.submit(
              () -> {
                start.await();
                return confirm().andReturn().getResponse().getStatus();
              });
      var second =
          pool.submit(
              () -> {
                start.await();
                return other.call();
              });
      start.countDown();
      return List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void documentsPatchAndSwaggerUi() throws Exception {
    String operation =
        "$.paths['/api/admin/support-applications/{supportApplicationId}/landlord-consent'].patch";
    var docs =
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.security[0].bearerAuth").exists())
            .andExpect(
                jsonPath(operation + ".description")
                    .value(org.hamcrest.Matchers.containsString("PENDING")))
            .andExpect(jsonPath(operation + ".parameters[0].name").value("supportApplicationId"))
            .andExpect(jsonPath(operation + ".parameters[0].required").value(true))
            .andExpect(
                jsonPath(
                        operation
                            + ".requestBody.content['application/json'].examples.confirmation.value.status")
                    .value("CONFIRMED"))
            .andExpect(
                jsonPath("$.components.schemas.ConfirmLandlordConsentRequest.required")
                    .value(org.hamcrest.Matchers.contains("status")))
            .andExpect(
                jsonPath(
                        "$.components.schemas.ConfirmLandlordConsentRequest.properties.status.enum")
                    .value(org.hamcrest.Matchers.contains("CONFIRMED")))
            .andExpect(
                jsonPath(
                        operation
                            + ".responses['200'].content['application/json'].examples.consentConfirmed.value.result.landlordConsentStatus")
                    .value("CONFIRMED"));
    for (var code : List.of("200", "400", "401", "403", "404", "409"))
      docs.andExpect(
          jsonPath(operation + ".responses['" + code + "'].content['application/json']").exists());
    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    mvc.perform(get("/v3/api-docs/swagger-config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").value("/v3/api-docs"));
  }
}
