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
      "spring.datasource.url=jdbc:h2:mem:application-support;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false",
      "app.s3.cleanup-interval-millis=3600000"
    })
@AutoConfigureMockMvc
class ApplicationSupportTest {
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

  ResultActions getSupport(String id, String access) throws Exception {
    var request = get("/api/admin/applications/" + id + "/support");
    if (access != null) request.header("Authorization", "Bearer " + access);
    return mvc.perform(request);
  }

  ResultActions getSupport() throws Exception {
    return getSupport(Long.toString(applicationId), token);
  }

  @Test
  void returnsApprovedSupportAndActualConstructionWithoutMutations() throws Exception {
    jdbc.update("UPDATE inspection_applications SET detail_address='Unit 101'");
    jdbc.update("UPDATE support_applications SET support_scope='Approved installation scope'");
    var supportsBefore = jdbc.queryForList("SELECT * FROM support_applications");
    var constructionsBefore = jdbc.queryForList("SELECT * FROM constructions");
    getSupport()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("APPLICATION_200_5"))
        .andExpect(jsonPath("$.result.applicationId").value(applicationId))
        .andExpect(
            jsonPath("$.result.receiptNo").value(String.format(Locale.ROOT, "%06d", applicationId)))
        .andExpect(jsonPath("$.result.applicantName").isNotEmpty())
        .andExpect(jsonPath("$.result.address").isNotEmpty())
        .andExpect(jsonPath("$.result.detailAddress").value("Unit 101"))
        .andExpect(jsonPath("$.result.support.supportApplicationId").value(supportId))
        .andExpect(jsonPath("$.result.support.programId").isNumber())
        .andExpect(jsonPath("$.result.support.programName").isNotEmpty())
        .andExpect(jsonPath("$.result.support.status").value("APPROVED"))
        .andExpect(jsonPath("$.result.support.requestedFacility").isNotEmpty())
        .andExpect(jsonPath("$.result.support.supportScope").value("Approved installation scope"))
        .andExpect(jsonPath("$.result.support.constructionId").value(constructionId))
        .andExpect(jsonPath("$.result.support.constructionStatus").value("PENDING"))
        .andExpect(
            jsonPath("$.result.support.rejectionReason").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(header().string("Cache-Control", "no-store"));
    assertThat(jdbc.queryForList("SELECT * FROM support_applications")).isEqualTo(supportsBefore);
    assertThat(jdbc.queryForList("SELECT * FROM constructions")).isEqualTo(constructionsBefore);
  }

  @Test
  void noSupportAndOnlyDraftAreSuccessfulEmptyStates() throws Exception {
    jdbc.update("UPDATE support_applications SET status='DRAFT',submitted_at=NULL");
    getSupport()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.support").value(org.hamcrest.Matchers.nullValue()));
    jdbc.update("DELETE FROM constructions");
    jdbc.update("DELETE FROM support_applications");
    getSupport()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.applicationId").value(applicationId))
        .andExpect(jsonPath("$.result.support").value(org.hamcrest.Matchers.nullValue()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUBMITTED"})
  void returnsReviewStateWithoutStaleApprovalOrConstruction(String state) throws Exception {
    jdbc.update("UPDATE support_applications SET status=?,rejection_reason='old rejection'", state);
    getSupport()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.support.status").value(state))
        .andExpect(
            jsonPath("$.result.support.supportScope").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(
            jsonPath("$.result.support.rejectionReason").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(
            jsonPath("$.result.support.constructionId").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  void returnsPersistedRejectionWithoutApprovalScopeOrConstruction() throws Exception {
    jdbc.update(
        "UPDATE support_applications SET status='REJECTED',rejection_reason='Eligibility requirements not met'");
    getSupport()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.support.status").value("REJECTED"))
        .andExpect(
            jsonPath("$.result.support.rejectionReason").value("Eligibility requirements not met"))
        .andExpect(
            jsonPath("$.result.support.supportScope").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(
            jsonPath("$.result.support.constructionId").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(
            jsonPath("$.result.support.constructionStatus")
                .value(org.hamcrest.Matchers.nullValue()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"PENDING", "SCHEDULED", "IN_PROGRESS", "COMPLETED"})
  void returnsActualConstructionStatus(String state) throws Exception {
    jdbc.update("UPDATE constructions SET status=?", state);
    getSupport()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.support.constructionStatus").value(state));
  }

  @Test
  void approvedWithoutConstructionDoesNotInventALink() throws Exception {
    jdbc.update("DELETE FROM constructions");
    getSupport()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.support.status").value("APPROVED"))
        .andExpect(
            jsonPath("$.result.support.constructionId").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(
            jsonPath("$.result.support.constructionStatus")
                .value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  void tenantConsentRequirementAndDocumentPresenceAreSeparate() throws Exception {
    jdbc.update(
        "UPDATE support_applications SET occupancy_type='TENANT',landlord_consent_status='PENDING'");
    getSupport()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.support.landlordConsentRequired").value(true))
        .andExpect(jsonPath("$.result.support.landlordConsentDocumentSubmitted").value(false));
    jdbc.update(
        "INSERT INTO support_attachments(support_application_id,attachment_type,object_key,original_filename,file_size,content_type,submitted_at) VALUES(?,'LANDLORD_CONSENT','consent-test','consent.pdf',100,'application/pdf',CURRENT_TIMESTAMP)",
        supportId);
    getSupport()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.support.landlordConsentDocumentSubmitted").value(true));
    jdbc.update("DELETE FROM support_attachments");
  }

  @ParameterizedTest
  @ValueSource(strings = {"OWNER", "ETC"})
  void ownerAndOtherDoNotRequireConsent(String occupancy) throws Exception {
    jdbc.update(
        "UPDATE support_applications SET occupancy_type=?,landlord_consent_status='PENDING'",
        occupancy);
    getSupport()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.support.landlordConsentRequired").value(false));
  }

  long anotherSupport(String state, String submitted) {
    jdbc.update(
        "INSERT INTO support_programs(name,created_at) VALUES('Another program',CURRENT_TIMESTAMP)");
    long program = jdbc.queryForObject("SELECT MAX(program_id) FROM support_programs", Long.class);
    jdbc.update(
        "INSERT INTO support_applications(application_id,program_id,applicant_user_id,requested_facility,applicant_name,contact,address,occupancy_type,preferred_visit_date,preferred_visit_time,status,submitted_at,landlord_consent_status) SELECT application_id,?,applicant_user_id,requested_facility,applicant_name,contact,address,occupancy_type,preferred_visit_date,preferred_visit_time,?,?,landlord_consent_status FROM support_applications WHERE support_application_id=?",
        program,
        state,
        submitted,
        supportId);
    return jdbc.queryForObject(
        "SELECT MAX(support_application_id) FROM support_applications", Long.class);
  }

  @Test
  void picksLatestSubmittedSupportAndIgnoresNewerDraft() throws Exception {
    jdbc.update("UPDATE support_applications SET submitted_at='2030-01-01 10:00:00'");
    long latest = anotherSupport("SUBMITTED", "2030-01-02 10:00:00");
    anotherSupport("DRAFT", null);
    getSupport()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.support.supportApplicationId").value(latest));
    long tie = anotherSupport("REJECTED", "2030-01-02 10:00:00");
    getSupport()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.support.supportApplicationId").value(tie));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
  void rejectsInvalidIds(String id) throws Exception {
    getSupport(id, token)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_400_3"));
  }

  @Test
  void unknownAndDraftInspectionApplicationsAreNotVisible() throws Exception {
    getSupport("999999", token)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("APPLICATION_404_2"));
    jdbc.update("UPDATE inspection_applications SET status='DRAFT'");
    getSupport().andExpect(status().isNotFound());
  }

  @Test
  void rejectsUnauthenticatedResidentAndOperatorWithoutJurisdiction() throws Exception {
    getSupport(Long.toString(applicationId), null).andExpect(status().isUnauthorized());
    getSupport(Long.toString(applicationId), "invalid").andExpect(status().isUnauthorized());
    getSupport(Long.toString(applicationId), residentToken).andExpect(status().isForbidden());
    jdbc.update("UPDATE users SET jurisdiction_id=NULL WHERE user_id=?", adminId);
    getSupport()
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("APPLICATION_403_4"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"Seoul", "Bucheon-road", "Gimpo"})
  void rejectsSourceOutsideJurisdiction(String address) throws Exception {
    jdbc.update("UPDATE inspection_applications SET address=?", address);
    getSupport()
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("APPLICATION_403_4"));
  }

  @Test
  void rejectsSupportSnapshotOutsideJurisdiction() throws Exception {
    jdbc.update("UPDATE support_applications SET address='Seoul'");
    getSupport()
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("APPLICATION_403_4"));
  }

  @Test
  void swaggerDocumentsSupportTabAndResponses() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.paths['/api/admin/applications/{applicationId}/support'].get.tags[0]")
                .value("운영자-GIS 신청관리"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/applications/{applicationId}/support'].get.responses['200']")
                .exists())
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/applications/{applicationId}/support'].get.responses['401']")
                .exists());
  }
}
