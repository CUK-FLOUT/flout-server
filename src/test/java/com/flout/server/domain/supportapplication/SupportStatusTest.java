package com.flout.server.domain.supportapplication;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
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
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:support-status;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false",
      "app.s3.cleanup-interval-millis=3600000"
    })
@AutoConfigureMockMvc
class SupportStatusTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired ObjectMapper mapper;
  @Autowired JwtEncoder encoder;

  long supportId, adminId, residentId;
  String token, residentToken;
  static final String SCOPE = "출입구 물막이판 설치 지원";

  /** Resets support data so each status-count test controls the rows included in its totals. */
  @BeforeEach
  void setup() {

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
            User.resident("approval-resident@example.test", "unused-test-hash", "테스트주민"));
    residentId = resident.getId();
    residentToken = jwt.issue(resident).accessToken();
    var admin =
        users.saveAndFlush(
            User.resident("approval-admin@example.test", "unused-test-hash", "테스트운영자"));
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

  ResultActions statusRequest(String access) throws Exception {
    var call = get("/api/admin/support-applications/status");
    if (access != null) call.header("Authorization", "Bearer " + access);
    return mvc.perform(call);
  }

  void add(String state, String address, String consent) {
    jdbc.update("INSERT INTO support_programs(name,created_at) VALUES('집계 검증용',CURRENT_TIMESTAMP)");
    var program = jdbc.queryForObject("SELECT MAX(program_id) FROM support_programs", Long.class);
    jdbc.update(
        """
      INSERT INTO support_applications(application_id,program_id,applicant_user_id,requested_facility,applicant_name,contact,address,occupancy_type,preferred_visit_date,preferred_visit_time,status,submitted_at,landlord_consent_status)
      SELECT application_id,?,applicant_user_id,requested_facility,applicant_name,contact,?,occupancy_type,preferred_visit_date,preferred_visit_time,?,submitted_at,?
      FROM support_applications WHERE support_application_id=?
      """,
        program,
        address,
        state,
        consent,
        supportId);
  }

  @Test
  void emptyReturnsZeroAndExactCommonResponse() throws Exception {
    jdbc.update("DELETE FROM support_applications");
    statusRequest(token)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.status").value(200))
        .andExpect(jsonPath("$.code").value("COMMON_200"))
        .andExpect(jsonPath("$.message").value("호출에 성공했습니다."))
        .andExpect(jsonPath("$.timeStamp").isString())
        .andExpect(jsonPath("$.result.length()").value(4))
        .andExpect(jsonPath("$.result.totalCount").value(0))
        .andExpect(jsonPath("$.result.reviewingCount").value(0))
        .andExpect(jsonPath("$.result.rejectedCount").value(0))
        .andExpect(jsonPath("$.result.consentRequiredCount").doesNotExist())
        .andExpect(jsonPath("$.result.approvedCount").value(0));
  }

  @Test
  void aggregatesCurrentStatesOnceAndIncludesOtherStatesInTotal() throws Exception {
    add("SUBMITTED", "경기 부천시 소사구", "PENDING");
    add("SUBMITTED", "부천시 오정구", "NOT_REQUIRED");
    add("APPROVED", "  경기도 부천시 원미구 ", "CONFIRMED");
    add("SUBMITTED", "경기도 부천시 원미구", "CONFIRMED");
    add("REJECTED", "경기도 부천시 원미구", "PENDING");
    add("REJECTED", "서울시 중구", "PENDING");
    add("DRAFT", "부천시 원미구", "PENDING");
    var before = jdbc.queryForList("SELECT * FROM support_applications");
    statusRequest(token)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalCount").value(6))
        .andExpect(jsonPath("$.result.reviewingCount").value(4))
        .andExpect(jsonPath("$.result.rejectedCount").value(1))
        .andExpect(jsonPath("$.result.approvedCount").value(1));
    assertThat(jdbc.queryForList("SELECT * FROM support_applications")).isEqualTo(before);
    mvc.perform(
            get("/api/admin/support-applications/status")
                .header("Authorization", "Bearer " + token)
                .param("keyword", "없는신청")
                .param("page", "99")
                .param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalCount").value(6));
  }

  @Test
  void excludesOtherJurisdictionsAndLookalikeOrEmbeddedAddresses() throws Exception {
    for (var address : List.of("경기도 서울시", "서울시 부천시", "부천시청", "부천시외", "경기도 부천시청", "부천시흥"))
      add("APPROVED", address, "CONFIRMED");
    statusRequest(token)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalCount").value(1))
        .andExpect(jsonPath("$.result.approvedCount").value(0));
  }

  @Test
  void rejectsAnonymousAndResident() throws Exception {
    statusRequest(null)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("COMMON_401_1"));
    statusRequest(residentToken)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("COMMON_403_1"));
    statusRequest("invalid-token")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("COMMON_401_3"));
  }

  @Test
  void rejectsMissingAndUnsupportedJurisdiction() throws Exception {
    jdbc.update("UPDATE users SET jurisdiction_id=NULL WHERE user_id=?", adminId);
    statusRequest(token)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("COMMON_403_1"));
    jdbc.update("INSERT INTO jurisdictions(name) VALUES('타지역')");
    jdbc.update(
        "UPDATE users SET jurisdiction_id=(SELECT jurisdiction_id FROM jurisdictions WHERE name='타지역') WHERE user_id=?",
        adminId);
    statusRequest(token)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("COMMON_403_1"));
  }

  @Test
  void draftIsHiddenFromAdminAndCannotBeApprovedOrRejected() throws Exception {
    jdbc.update(
        "UPDATE support_applications SET status='DRAFT' WHERE support_application_id=?", supportId);
    statusRequest(token)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalCount").value(0));
    mvc.perform(get("/api/admin/support-applications").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalElements").value(0));
    mvc.perform(
            get("/api/admin/support-applications/{id}", supportId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/admin/support-applications/{id}/approve", supportId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"supportScope\":\"물막이판\",\"applicantHousingReviewed\":true,\"expertInspectionReviewed\":true,\"eligibilityDocumentsReviewed\":true}"))
        .andExpect(status().isConflict());
    mvc.perform(
            post("/api/admin/support-applications/{id}/reject", supportId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"사유\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void approvedCannotBeApprovedAgain() throws Exception {
    jdbc.update(
        "UPDATE support_applications SET status='APPROVED' WHERE support_application_id=?",
        supportId);
    mvc.perform(
            post("/api/admin/support-applications/" + supportId + "/approve")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"supportScope\":\"물막이판\",\"applicantHousingReviewed\":true,\"expertInspectionReviewed\":true,\"eligibilityDocumentsReviewed\":true}"))
        .andExpect(status().isConflict());
  }

  @ParameterizedTest
  @ValueSource(strings = {"OWNER", "TENANT", "ETC"})
  void submissionExcludesDraftsFromCounts(String occupancy) throws Exception {
    var source =
        jdbc.queryForObject(
            "SELECT application_id FROM support_applications WHERE support_application_id=?",
            Long.class,
            supportId);
    var program =
        jdbc.queryForObject(
            "SELECT program_id FROM support_applications WHERE support_application_id=?",
            Long.class,
            supportId);
    jdbc.update("DELETE FROM support_applications");
    jdbc.update(
        "UPDATE inspection_applications SET occupancy_type=? WHERE application_id=?",
        occupancy,
        source);
    jdbc.update(
        "UPDATE inspection_results SET result_status='PUBLISHED',published_at=CURRENT_TIMESTAMP");
    var body =
        Map.of(
            "applicationId",
            source,
            "programId",
            program,
            "requestedFacility",
            "출입구 물막이판",
            "preferredVisitDate",
            java.time.LocalDate.now().plusDays(1).toString(),
            "preferredVisitTime",
            "ANY");
    boolean tenant = "TENANT".equals(occupancy);
    mvc.perform(
            post("/api/resident/support-applications")
                .header("Authorization", "Bearer " + residentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.result.status").value(tenant ? "DRAFT" : "SUBMITTED"));
    statusRequest(token)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalCount").value(tenant ? 0 : 1))
        .andExpect(jsonPath("$.result.rejectedCount").value(0))
        .andExpect(jsonPath("$.result.reviewingCount").value(tenant ? 0 : 1));
  }
}
