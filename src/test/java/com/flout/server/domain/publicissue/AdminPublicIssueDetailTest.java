package com.flout.server.domain.publicissue;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.inspectionapplication.service.AttachmentStorage;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:admin-public-issue-detail;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false",
      "app.fcm.enabled=false",
      "app.s3.cleanup-interval-millis=3600000"
    })
@AutoConfigureMockMvc
class AdminPublicIssueDetailTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired ObjectMapper mapper;
  @MockitoBean AttachmentStorage storage;

  long residentId;
  long adminId;
  String token;
  String residentToken;

  @BeforeEach
  void setup() {
    for (var table :
        List.of(
            "facility_action_image",
            "facility_action",
            "facility_review",
            "public_facility",
            "public_issues",
            "application_inflow_locations",
            "inspection_results",
            "inspections",
            "inspection_applications",
            "risk_assessments",
            "refresh_tokens",
            "users")) jdbc.update("DELETE FROM " + table);

    var resident =
        users.saveAndFlush(User.resident("resident@public-detail.test", "unused", "김주민"));
    residentId = resident.getId();
    residentToken = jwt.issue(resident).accessToken();
    var admin = users.saveAndFlush(User.resident("admin@public-detail.test", "unused", "박운영자"));
    adminId = admin.getId();
    jdbc.update(
        "UPDATE users SET role='ADMIN',jurisdiction_id=(SELECT jurisdiction_id FROM jurisdictions WHERE name='부천시') WHERE user_id=?",
        adminId);
    token = jwt.issue(users.findById(adminId).orElseThrow()).accessToken();
  }

  ApplicationSeed addApplication(
      String applicantName,
      String address,
      double latitude,
      double longitude,
      String riskLevel,
      String floodExperience,
      boolean existingFacility,
      String requestReason,
      String... inflows) {
    jdbc.update(
        "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status) VALUES(?,?,'101호',?,'COMPLETED')",
        residentId,
        address,
        riskLevel);
    long riskId =
        jdbc.queryForObject("SELECT MAX(assessment_id) FROM risk_assessments", Long.class);
    var receipt = UUID.randomUUID().toString().substring(0, 20);
    jdbc.update(
        "INSERT INTO inspection_applications(applicant_name,applicant_user_id,assessment_id,receipt_no,address,detail_address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,request_reason,status) VALUES(?,?,?,?,?,'101호',?,?,'010-1234-5678','OWNER',?,?,?,'RECEIVED')",
        applicantName,
        residentId,
        riskId,
        receipt,
        address,
        latitude,
        longitude,
        floodExperience,
        existingFacility,
        requestReason);
    long applicationId =
        jdbc.queryForObject("SELECT MAX(application_id) FROM inspection_applications", Long.class);
    for (String inflow : inflows)
      jdbc.update(
          "INSERT INTO application_inflow_locations(application_id,inflow_location) VALUES(?,?)",
          applicationId,
          inflow);
    return new ApplicationSeed(applicationId, receipt);
  }

  IssueSeed addIssue(String address, double latitude, double longitude, String status) {
    var source =
        addApplication(
            "등록주민", address, latitude, longitude, "HIGH", "YES", false, "빗물받이 역류", "DRAIN");
    jdbc.update(
        "INSERT INTO inspections(application_id,status) VALUES(?,'COMPLETED')", source.id());
    long inspectionId =
        jdbc.queryForObject("SELECT MAX(inspection_id) FROM inspections", Long.class);
    jdbc.update(
        "INSERT INTO public_issues(source_inspection_id,address,latitude,longitude,issue_type,description,field_findings,status) VALUES(?,?,?,?, 'BLOCKED_STORM_DRAIN','빗물받이 막힘','낙엽과 토사 퇴적',?)",
        inspectionId,
        address,
        latitude,
        longitude,
        status);
    long issueId =
        jdbc.queryForObject("SELECT MAX(public_issue_id) FROM public_issues", Long.class);
    return new IssueSeed(issueId, inspectionId, source.id(), source.receipt());
  }

  @Test
  void returnsProblemSourceReferencesReviewFacilityActionsAndImages() throws Exception {
    var issue = addIssue("경기도 부천시 원미구 길주로 210", 37.5000000, 126.7600000, "COMPLETED");
    var nearby =
        addApplication(
            "인근주민",
            "경기도 부천시 원미구 길주로 220",
            37.5040000,
            126.7600000,
            "CAUTION",
            "YES",
            true,
            "화장실 하수 역류",
            "BATHROOM",
            "DRAIN");
    addApplication(
        "침수없음", "경기도 부천시 원미구 길주로 230", 37.5000000, 126.7650000, "LOW", "NO", false, null);
    addApplication(
        "먼주민", "경기도 부천시 원미구 먼길 1", 37.5300000, 126.7600000, "HIGH", "YES", false, "거리 밖");
    addApplication("관할외", "서울특별시 구로구 부천로 1", 37.5000000, 126.7600000, "HIGH", "YES", false, "관할 밖");

    jdbc.update(
        "INSERT INTO public_facility(facility_name,facility_type,address,latitude,longitude) VALUES('원미구 빗물받이','STORM_DRAIN','경기도 부천시 원미구 길주로 211',37.5001000,126.7601000)");
    long facilityId =
        jdbc.queryForObject("SELECT MAX(facility_id) FROM public_facility", Long.class);
    jdbc.update(
        "INSERT INTO facility_review(user_id,local_problem_id,facility_id,review_target,inspection_note,review_status,review_reason,reviewed_at) VALUES(?,?,?,'빗물받이','막힘과 배수 상태 확인','COMPLETED','인근 신청과 침수 이력 확인','2026-11-08 10:00:00')",
        adminId,
        issue.id(),
        facilityId);
    long reviewId = jdbc.queryForObject("SELECT MAX(review_id) FROM facility_review", Long.class);
    jdbc.update(
        "INSERT INTO facility_action(review_id,user_id,action_description,action_status,completed_at,created_at) VALUES(?,?,'퇴적물 제거','COMPLETED','2026-11-08 11:00:00','2026-11-08 10:30:00')",
        reviewId,
        adminId);
    long actionId = jdbc.queryForObject("SELECT MAX(action_id) FROM facility_action", Long.class);
    jdbc.update(
        "INSERT INTO facility_action_image(image_url,uploaded_at,action_id) VALUES('public-issues/test/action.jpg','2026-11-08 11:00:00',?)",
        actionId);
    when(storage.readUrl("public-issues/test/action.jpg"))
        .thenReturn("https://example.test/action.jpg");

    mvc.perform(
            get("/api/admin/public-issues/{publicIssueId}", issue.id())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("PUBLIC_ISSUE_200_8"))
        .andExpect(jsonPath("$.result.publicIssueId").value(issue.id()))
        .andExpect(jsonPath("$.result.issueType").value("BLOCKED_STORM_DRAIN"))
        .andExpect(jsonPath("$.result.status").value("COMPLETED"))
        .andExpect(jsonPath("$.result.description").value("빗물받이 막힘"))
        .andExpect(jsonPath("$.result.fieldFindings").value("낙엽과 토사 퇴적"))
        .andExpect(jsonPath("$.result.source.inspectionId").value(issue.inspectionId()))
        .andExpect(jsonPath("$.result.source.applicationId").value(issue.applicationId()))
        .andExpect(jsonPath("$.result.source.receiptNo").value(issue.receipt()))
        .andExpect(jsonPath("$.result.referenceInformation.relatedApplicationCount").value(3))
        .andExpect(jsonPath("$.result.referenceInformation.floodHistoryCount").value(2))
        .andExpect(jsonPath("$.result.referenceInformation.relatedApplications.length()").value(3))
        .andExpect(
            jsonPath("$.result.referenceInformation.relatedApplications[1].applicationId")
                .value(nearby.id()))
        .andExpect(
            jsonPath("$.result.referenceInformation.relatedApplications[1].riskLevel")
                .value("CAUTION"))
        .andExpect(
            jsonPath("$.result.referenceInformation.relatedApplications[1].inflowLocations")
                .value(org.hamcrest.Matchers.contains("BATHROOM", "DRAIN")))
        .andExpect(jsonPath("$.result.review.reviewId").value(reviewId))
        .andExpect(jsonPath("$.result.review.reviewTarget").value("빗물받이"))
        .andExpect(jsonPath("$.result.publicFacility.facilityId").value(facilityId))
        .andExpect(jsonPath("$.result.publicFacility.facilityName").value("원미구 빗물받이"))
        .andExpect(jsonPath("$.result.actions.length()").value(1))
        .andExpect(jsonPath("$.result.actions[0].actionId").value(actionId))
        .andExpect(jsonPath("$.result.actions[0].status").value("COMPLETED"))
        .andExpect(jsonPath("$.result.actions[0].images.length()").value(1))
        .andExpect(
            jsonPath("$.result.actions[0].images[0].imageUrl")
                .value("https://example.test/action.jpg"));
  }

  @Test
  void returnsNullReviewAndFacilityAndEmptyActionsBeforeReview() throws Exception {
    var issue = addIssue("부천시 소사구 경인로 1", 37.4700000, 126.8000000, "RECEIVED");

    mvc.perform(
            get("/api/admin/public-issues/{publicIssueId}", issue.id())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.review").doesNotExist())
        .andExpect(jsonPath("$.result.publicFacility").doesNotExist())
        .andExpect(jsonPath("$.result.actions").isEmpty());
  }

  @Test
  void validatesIdNotFoundJurisdictionAndAuthentication() throws Exception {
    mvc.perform(
            get("/api/admin/public-issues/{publicIssueId}", 0)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_400_3"));
    mvc.perform(
            get("/api/admin/public-issues/{publicIssueId}", 999999)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PUBLIC_ISSUE_404_1"));

    var outside = addIssue("서울특별시 구로구 경인로 1", 37.5000000, 126.7600000, "RECEIVED");
    mvc.perform(
            get("/api/admin/public-issues/{publicIssueId}", outside.id())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/admin/public-issues/{publicIssueId}", outside.id()))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/admin/public-issues/{publicIssueId}", outside.id())
                .header("Authorization", "Bearer " + residentToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void swaggerPublishesDetailContract() throws Exception {
    var response = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
    var operation =
        mapper
            .readTree(response.getResponse().getContentAsString())
            .path("paths")
            .path("/api/admin/public-issues/{publicIssueId}")
            .path("get");
    org.assertj.core.api.Assertions.assertThat(operation.path("summary").asString())
        .isEqualTo("지역 문제·참고 정보 상세 조회");
    org.assertj.core.api.Assertions.assertThat(
            operation.path("parameters").get(0).path("name").asString())
        .isEqualTo("publicIssueId");
  }

  record ApplicationSeed(long id, String receipt) {}

  record IssueSeed(long id, long inspectionId, long applicationId, String receipt) {}
}
