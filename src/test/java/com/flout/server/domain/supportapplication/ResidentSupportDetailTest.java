package com.flout.server.domain.supportapplication;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
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
      "spring.datasource.url=jdbc:h2:mem:resident-support-detail;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false",
      "app.s3.cleanup-interval-millis=3600000"
    })
@AutoConfigureMockMvc
class ResidentSupportDetailTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired ObjectMapper mapper;
  long supportId, adminId, residentId;
  String token, residentToken;

  @BeforeEach
  void setup() {

    for (var table :
        List.of(
            "support_attachments",
            "support_application_status_histories",
            "support_applications",
            "support_programs",
            "prevention_recommendations",
            "inspection_vulnerabilities",
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
        "INSERT INTO support_applications(application_id,program_id,applicant_user_id,requested_facility,applicant_name,contact,address,occupancy_type,preferred_visit_date,preferred_visit_time,status,submitted_at,landlord_consent_status) VALUES(?,?,?,'출입구 물막이판','테스트주민','010-0000-0000','경기도 부천시 원미구 길주로 210','OWNER',CURRENT_DATE,'ANY','REVIEWING',CURRENT_TIMESTAMP,'NOT_REQUIRED')",
        source,
        program,
        residentId);
    supportId =
        jdbc.queryForObject("SELECT support_application_id FROM support_applications", Long.class);
  }

  ResultActions request(String view, String id, String access) throws Exception {
    var call = get("/api/resident/support-applications/" + id + "/" + view);
    if (access != null) call.header("Authorization", "Bearer " + access);
    return mvc.perform(call);
  }

  ResultActions request(String view) throws Exception {
    return request(view, Long.toString(supportId), residentToken);
  }

  void state(String state) {
    jdbc.update("UPDATE support_applications SET status=?", state);
  }

  @Test
  void reviewReturnsActualSnapshotProgramAndPreferredScheduleWithoutInternalData()
      throws Exception {
    jdbc.update(
        "UPDATE inspection_applications SET applicant_name='원본 변경', address='서울특별시',detail_address='101호',internal_memo='운영자 비공개 메모'");
    jdbc.update(
        "UPDATE support_applications SET applicant_name='김주민',preferred_visit_date='2026-11-20',preferred_visit_time='AFTERNOON',occupancy_type='TENANT',landlord_consent_status='PENDING',submitted_at='2026-11-05 12:34:56'");
    long source =
        jdbc.queryForObject("SELECT application_id FROM inspection_applications", Long.class);
    request("review")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("COMMON_200"))
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.timeStamp").isString())
        .andExpect(jsonPath("$.result.length()").value(14))
        .andExpect(jsonPath("$.result.supportApplicationId").value(supportId))
        .andExpect(jsonPath("$.result.applicationId").value(source))
        .andExpect(jsonPath("$.result.receiptNo").value(String.format(Locale.ROOT, "%06d", source)))
        .andExpect(jsonPath("$.result.applicantName").value("김주민"))
        .andExpect(jsonPath("$.result.address").value("경기도 부천시 원미구 길주로 210"))
        .andExpect(jsonPath("$.result.detailAddress").value("101호"))
        .andExpect(jsonPath("$.result.programName").value("검증용 지원사업"))
        .andExpect(jsonPath("$.result.status").value("REVIEWING"))
        .andExpect(jsonPath("$.result.submittedAt").value("2026-11-05T12:34:56"))
        .andExpect(jsonPath("$.result.requestedFacility").value("출입구 물막이판"))
        .andExpect(jsonPath("$.result.occupancyType").value("TENANT"))
        .andExpect(jsonPath("$.result.preferredConstructionDate").value("2026-11-20"))
        .andExpect(jsonPath("$.result.preferredTimeSlot").value("AFTERNOON"))
        .andExpect(jsonPath("$.result.landlordConsentStatus").value("PENDING"));
  }

  @Test
  void approvalUsesSavedScopeAndDecisionTimeAndDoesNotInventConstructionSchedule()
      throws Exception {
    state("APPROVED");
    jdbc.update(
        "UPDATE support_applications SET support_scope='출입구 차수판 1개 설치',approved_at='2026-11-05 13:15:00',approved_by_user_id=?,landlord_consent_status='CONFIRMED'",
        adminId);
    request("approval")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.length()").value(14))
        .andExpect(jsonPath("$.result.status").value("APPROVED"))
        .andExpect(jsonPath("$.result.approvedAt").value("2026-11-05T13:15:00"))
        .andExpect(jsonPath("$.result.requestedFacility").value("출입구 물막이판"))
        .andExpect(jsonPath("$.result.supportScope").value("출입구 차수판 1개 설치"))
        .andExpect(jsonPath("$.result.landlordConsentStatus").value("VERIFIED"))
        .andExpect(
            jsonPath("$.result.scheduledConstructionDate").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(
            jsonPath("$.result.scheduledConstructionTime")
                .value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  void rejectionReturnsStoredReasonAndActualPublishedResultLink() throws Exception {
    state("REJECTED");
    jdbc.update(
        "UPDATE support_applications SET rejection_reason='사업 지원 조건에 해당하지 않음',approved_at='2026-11-05 14:00:00'");
    jdbc.update(
        "UPDATE inspection_results SET result_status='PUBLISHED',published_at=CURRENT_TIMESTAMP");
    long inspection = jdbc.queryForObject("SELECT inspection_id FROM inspections", Long.class);
    request("rejection")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.length()").value(13))
        .andExpect(jsonPath("$.result.status").value("REJECTED"))
        .andExpect(jsonPath("$.result.rejectionReason").value("사업 지원 조건에 해당하지 않음"))
        .andExpect(jsonPath("$.result.rejectedAt").value("2026-11-05T14:00:00"))
        .andExpect(jsonPath("$.result.inspectionId").value(inspection))
        .andExpect(jsonPath("$.result.inspectionResultPublished").value(true));
  }

  @ParameterizedTest
  @CsvSource({
    "review,SUBMITTED,200",
    "review,REVIEWING,200",
    "review,CONSENT_PENDING,200",
    "review,SCHEDULING,200",
    "review,APPROVED,409",
    "review,REJECTED,409",
    "approval,SUBMITTED,409",
    "approval,REVIEWING,409",
    "approval,CONSENT_PENDING,409",
    "approval,SCHEDULING,409",
    "approval,APPROVED,200",
    "approval,REJECTED,409",
    "rejection,SUBMITTED,409",
    "rejection,REVIEWING,409",
    "rejection,CONSENT_PENDING,409",
    "rejection,SCHEDULING,409",
    "rejection,APPROVED,409",
    "rejection,REJECTED,200"
  })
  void enforcesTheApprovedViewStateMatrix(String view, String value, int http) throws Exception {
    state(value);
    var call = request(view).andExpect(status().is(http));
    if (http == 409) call.andExpect(jsonPath("$.code").value("SUPPORT_DETAIL_409_1"));
  }

  @Test
  void submittedStatusIsReturnedWithoutChangingIt() throws Exception {
    state("SUBMITTED");
    request("review")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.status").value("SUBMITTED"));
    assertThat(jdbc.queryForObject("SELECT status FROM support_applications", String.class))
        .isEqualTo("SUBMITTED");
  }

  @ParameterizedTest
  @CsvSource({"PENDING,PENDING", "CONFIRMED,VERIFIED", "NOT_REQUIRED,NOT_REQUIRED"})
  void mapsConsentWithoutChangingStoredValues(String saved, String display) throws Exception {
    jdbc.update("UPDATE support_applications SET landlord_consent_status=?", saved);
    request("review")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.landlordConsentStatus").value(display));
    assertThat(
            jdbc.queryForObject(
                "SELECT landlord_consent_status FROM support_applications", String.class))
        .isEqualTo(saved);
  }

  @ParameterizedTest
  @ValueSource(strings = {"review", "approval", "rejection"})
  void onlyTheResidentOwnerCanAccessEachView(String view) throws Exception {
    state(
        view.equals("approval") ? "APPROVED" : view.equals("rejection") ? "REJECTED" : "REVIEWING");
    var other = users.saveAndFlush(User.resident("other@example.test", "test-hash", "다른주민"));
    var otherToken = jwt.issue(other).accessToken();
    request(view, Long.toString(supportId), null)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("COMMON_401_1"));
    request(view, Long.toString(supportId), "invalid")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("COMMON_401_3"));
    request(view, Long.toString(supportId), token)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("COMMON_403_1"));
    request(view, Long.toString(supportId), otherToken)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("COMMON_403_1"));
    state("REJECTED");
    request(view, Long.toString(supportId), otherToken).andExpect(status().isForbidden());
    request(view, Long.toString(Long.MAX_VALUE), residentToken)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SUPPORT_404_1"));
    for (String id : List.of("0", "-1", "abc", "1.1", "9223372036854775808")) {
      request(view, id, residentToken)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("COMMON_400_3"));
    }
  }

  @Test
  void completedInspectionIsNotTreatedAsPublished() throws Exception {
    state("REJECTED");
    jdbc.update("UPDATE inspections SET status='COMPLETED'");
    request("rejection")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.inspectionResultPublished").value(false));
  }

  @Test
  void absentInspectionAndMissingOptionalValuesStayNull() throws Exception {
    state("REJECTED");
    jdbc.update("DELETE FROM inspection_results");
    jdbc.update("DELETE FROM inspections");
    request("rejection")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.rejectionReason").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.result.rejectedAt").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.result.inspectionId").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.result.inspectionResultPublished").value(false));
    state("APPROVED");
    request("approval")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.supportScope").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.result.approvedAt").value(org.hamcrest.Matchers.nullValue()));
  }

  @ParameterizedTest
  @CsvSource({"review,REVIEWING", "approval,APPROVED", "rejection,REJECTED"})
  void getDoesNotWriteAndReturnsOnlyScreenData(String view, String value) throws Exception {
    state(value);
    var tables =
        List.of(
            "support_applications",
            "support_application_status_histories",
            "inspection_applications",
            "inspections",
            "inspection_results",
            "support_programs");
    var before = tables.stream().map(t -> jdbc.queryForList("SELECT * FROM " + t)).toList();
    var result =
        request(view).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    var data = mapper.readTree(result).get("result");
    for (String field :
        List.of(
            "contact",
            "internalMemo",
            "approvedBy",
            "approvedByUserId",
            "attachments",
            "fileUrl",
            "rejectionReason")) {
      if (!view.equals("rejection") || !field.equals("rejectionReason"))
        assertThat(data.has(field)).as(field).isFalse();
    }
    assertThat(tables.stream().map(t -> jdbc.queryForList("SELECT * FROM " + t)).toList())
        .isEqualTo(before);
  }

  @Test
  void swaggerDocumentsAllThreeViewsAndKeepsExistingOperatorRoutes() throws Exception {
    var text =
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var paths = mapper.readTree(text).get("paths");
    for (String view : List.of("review", "approval", "rejection")) {
      var op =
          paths.get("/api/resident/support-applications/{supportApplicationId}/" + view).get("get");
      assertThat(
              op.get("responses").get("200").get("content").get("application/json").has("schema"))
          .isTrue();
      var media = op.get("responses").get("200").get("content").get("application/json");
      var example = media.get("examples").get(view).get("value");
      assertThat(example.get("code").asString()).isEqualTo("COMMON_200");
      assertThat(example.get("status").asInt()).isEqualTo(200);
      var sample = example.get("result");
      Class<?> dto =
          switch (view) {
            case "review" ->
                com.flout.server.domain.supportapplication.dto.response
                    .ResidentSupportReviewResponse.class;
            case "approval" ->
                com.flout.server.domain.supportapplication.dto.response
                    .ResidentSupportApprovalResponse.class;
            default ->
                com.flout.server.domain.supportapplication.dto.response
                    .ResidentSupportRejectionResponse.class;
          };
      assertThat(sample.size()).isEqualTo(dto.getRecordComponents().length);
      for (var field : dto.getRecordComponents())
        assertThat(sample.has(field.getName())).as(field.getName()).isTrue();
      assertThat(sample.get("programName").asString()).isEqualTo("침수 예방시설 설치 지원");
      assertThat(sample.get("requestedFacility").asString()).isEqualTo("출입구 물막이판");
      assertThat(sample.get("status").asString())
          .isEqualTo(
              switch (view) {
                case "review" -> "REVIEWING";
                case "approval" -> "APPROVED";
                default -> "REJECTED";
              });
      if (view.equals("approval")) {
        assertThat(sample.get("scheduledConstructionDate").isNull()).isTrue();
        var scheduled = media.get("examples").get("approvalScheduled").get("value").get("result");
        assertThat(scheduled.get("scheduledConstructionDate").asString()).isEqualTo("2026-10-21");
        assertThat(scheduled.get("scheduledConstructionTime").asString()).isEqualTo("10:00:00");
      }
      for (String code : List.of("200", "400", "401", "403", "404", "409"))
        assertThat(op.get("responses").has(code)).as(view + code).isTrue();
    }
    assertThat(paths.has("/api/admin/support-applications")).isTrue();
    assertThat(paths.has("/api/admin/support-applications/{supportApplicationId}")).isTrue();
    assertThat(paths.has("/api/admin/support-applications/status")).isTrue();
  }
}
