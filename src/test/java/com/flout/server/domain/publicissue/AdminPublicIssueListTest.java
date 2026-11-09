package com.flout.server.domain.publicissue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:admin-public-issue-list;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false",
      "app.fcm.enabled=false",
      "app.s3.cleanup-interval-millis=3600000"
    })
@AutoConfigureMockMvc
class AdminPublicIssueListTest {
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
            "facility_action_image",
            "facility_action",
            "facility_review",
            "public_facility",
            "public_issues",
            "inspection_results",
            "inspections",
            "inspection_applications",
            "risk_assessments",
            "refresh_tokens",
            "users")) jdbc.update("DELETE FROM " + table);

    var resident = users.saveAndFlush(User.resident("resident@public-list.test", "unused", "김주민"));
    residentId = resident.getId();
    residentToken = jwt.issue(resident).accessToken();
    var admin = users.saveAndFlush(User.resident("admin@public-list.test", "unused", "박운영자"));
    adminId = admin.getId();
    jdbc.update(
        "UPDATE users SET role='ADMIN',jurisdiction_id=(SELECT jurisdiction_id FROM jurisdictions WHERE name='부천시') WHERE user_id=?",
        adminId);
    token = jwt.issue(users.findById(adminId).orElseThrow()).accessToken();
  }

  ResultActions list(
      String access,
      String district,
      String status,
      String issueType,
      String keyword,
      int page,
      int size)
      throws Exception {
    var request =
        get("/api/admin/public-issues")
            .param("district", district)
            .param("keyword", keyword)
            .param("page", Integer.toString(page))
            .param("size", Integer.toString(size));
    if (status != null) request.param("status", status);
    if (issueType != null) request.param("issueType", issueType);
    if (access != null) request.header("Authorization", "Bearer " + access);
    return mvc.perform(request);
  }

  long addApplication(String address, double latitude, double longitude, String floodExperience) {
    jdbc.update(
        "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status) VALUES(?,?,'101호','HIGH','COMPLETED')",
        residentId,
        address);
    long riskId =
        jdbc.queryForObject("SELECT MAX(assessment_id) FROM risk_assessments", Long.class);
    var receipt = UUID.randomUUID().toString().substring(0, 20);
    jdbc.update(
        "INSERT INTO inspection_applications(applicant_name,applicant_user_id,assessment_id,receipt_no,address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,status) VALUES('김주민',?,?,?, ?,?,?,'010-1234-5678','OWNER',?,FALSE,'RECEIVED')",
        residentId,
        riskId,
        receipt,
        address,
        latitude,
        longitude,
        floodExperience);
    return jdbc.queryForObject(
        "SELECT MAX(application_id) FROM inspection_applications", Long.class);
  }

  long addIssue(
      String address,
      double latitude,
      double longitude,
      String issueType,
      String description,
      String findings,
      String status) {
    long applicationId = addApplication(address, latitude, longitude, "YES");
    jdbc.update(
        "INSERT INTO inspections(application_id,status) VALUES(?,'COMPLETED')", applicationId);
    long inspectionId =
        jdbc.queryForObject("SELECT MAX(inspection_id) FROM inspections", Long.class);
    jdbc.update(
        "INSERT INTO public_issues(source_inspection_id,address,latitude,longitude,issue_type,description,field_findings,status) VALUES(?,?,?,?,?,?,?,?)",
        inspectionId,
        address,
        latitude,
        longitude,
        issueType,
        description,
        findings,
        status);
    return jdbc.queryForObject("SELECT MAX(public_issue_id) FROM public_issues", Long.class);
  }

  @Test
  void returnsScreenFieldsAndOneKilometerRelatedCountsWithoutChangingData() throws Exception {
    long issueId =
        addIssue(
            "경기도 부천시 원미구 길주로 210",
            37.5000000,
            126.7600000,
            "REPEATED_FLOODING",
            "중앙 배수로 반복 침수",
            "우천 시 물 고임",
            "UNDER_REVIEW");
    addApplication("경기도 부천시 원미구 길주로 220", 37.5040000, 126.7600000, "NO");
    addApplication("경기도 부천시 원미구 길주로 230", 37.5000000, 126.7650000, "YES");
    addApplication("경기도 부천시 원미구 먼길 1", 37.5300000, 126.7600000, "YES");
    addApplication("서울특별시 구로구 부천로 1", 37.5000000, 126.7600000, "YES");
    var before = jdbc.queryForList("SELECT * FROM public_issues");

    list(token, "", null, null, "", 0, 10)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("PUBLIC_ISSUE_200_5"))
        .andExpect(jsonPath("$.result.length()").value(5))
        .andExpect(jsonPath("$.result.publicIssues.length()").value(1))
        .andExpect(jsonPath("$.result.publicIssues[0].length()").value(5))
        .andExpect(jsonPath("$.result.publicIssues[0].publicIssueId").value(issueId))
        .andExpect(jsonPath("$.result.publicIssues[0].issueType").value("REPEATED_FLOODING"))
        .andExpect(jsonPath("$.result.publicIssues[0].relatedApplicationCount").value(3))
        .andExpect(jsonPath("$.result.publicIssues[0].floodHistoryCount").value(2))
        .andExpect(jsonPath("$.result.publicIssues[0].status").value("UNDER_REVIEW"));

    assertThat(jdbc.queryForList("SELECT * FROM public_issues")).isEqualTo(before);
  }

  @Test
  void combinesDistrictStatusTypeAndContentSearchFilters() throws Exception {
    long matched =
        addIssue(
            "부천시 소사구 중앙로 1",
            37.4700000,
            126.8000000,
            "BLOCKED_STORM_DRAIN",
            "빗물받이 막힘",
            "낙엽으로 배수 불량",
            "RECEIVED");
    addIssue(
        "부천시 원미구 중앙로 2",
        37.5000000,
        126.7700000,
        "BLOCKED_STORM_DRAIN",
        "빗물받이 막힘",
        "낙엽으로 배수 불량",
        "RECEIVED");
    addIssue(
        "부천시 소사구 중앙로 3", 37.4700000, 126.8100000, "SEWAGE_BACKFLOW", "하수 역류", "현장 확인", "COMPLETED");

    list(token, "소사구", "RECEIVED", "BLOCKED_STORM_DRAIN", "  배수 불량  ", 0, 10)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalElements").value(1))
        .andExpect(jsonPath("$.result.publicIssues[0].publicIssueId").value(matched));
    list(token, "", null, null, "하수 역류", 0, 10)
        .andExpect(jsonPath("$.result.totalElements").value(1))
        .andExpect(jsonPath("$.result.publicIssues[0].issueType").value("SEWAGE_BACKFLOW"));
    list(token, "", null, null, "없는 문제", 0, 10)
        .andExpect(jsonPath("$.result.publicIssues").isEmpty())
        .andExpect(jsonPath("$.result.totalElements").value(0))
        .andExpect(jsonPath("$.result.totalPages").value(0));
  }

  @Test
  void paginatesInStableIssueIdDescendingOrderAndLimitsJurisdiction() throws Exception {
    var expected = new ArrayList<Long>();
    for (int index = 0; index < 12; index++)
      expected.add(
          addIssue(
              "부천시 오정구 페이지로 " + index,
              37.5200000,
              126.7800000,
              "POOR_ROAD_DRAINAGE",
              "페이지 문제 " + index,
              "페이지 현장",
              "RECEIVED"));
    addIssue(
        "서울특별시 구로구 페이지로",
        37.5000000,
        126.7600000,
        "POOR_ROAD_DRAINAGE",
        "페이지 관할 외",
        "페이지 현장",
        "RECEIVED");
    Collections.reverse(expected);

    var actual = new ArrayList<Long>();
    for (int page = 0; page < 3; page++) {
      var response =
          list(token, "오정구", null, null, "페이지", page, 5)
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.result.page").value(page))
              .andExpect(jsonPath("$.result.totalElements").value(12))
              .andExpect(jsonPath("$.result.totalPages").value(3))
              .andReturn();
      for (var item :
          mapper
              .readTree(response.getResponse().getContentAsString())
              .path("result")
              .path("publicIssues")) actual.add(item.path("publicIssueId").asLong());
    }
    assertThat(actual).containsExactlyElementsOf(expected).doesNotHaveDuplicates();
  }

  @Test
  void validatesParametersAuthenticationAndOperatorJurisdiction() throws Exception {
    for (var values :
        List.of(
            new String[] {"district", "중구"},
            new String[] {"status", "UNKNOWN"},
            new String[] {"issueType", "UNKNOWN"},
            new String[] {"page", "-1"},
            new String[] {"size", "0"},
            new String[] {"size", "101"},
            new String[] {"keyword", "가".repeat(256)}))
      mvc.perform(
              get("/api/admin/public-issues")
                  .header("Authorization", "Bearer " + token)
                  .param(values[0], values[1]))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("COMMON_400_3"));

    list(null, "", null, null, "", 0, 10).andExpect(status().isUnauthorized());
    list("invalid", "", null, null, "", 0, 10).andExpect(status().isUnauthorized());
    list(residentToken, "", null, null, "", 0, 10).andExpect(status().isForbidden());
    jdbc.update("UPDATE users SET jurisdiction_id=NULL WHERE user_id=?", adminId);
    list(token, "", null, null, "", 0, 10).andExpect(status().isForbidden());
  }

  @Test
  void returnsStatusCountsWithScreenGroupingAndSearchFilters() throws Exception {
    addIssue(
        "부천시 원미구 중앙로 1",
        37.5000000,
        126.7600000,
        "REPEATED_FLOODING",
        "중앙 배수로 반복 침수",
        "검토 대기",
        "RECEIVED");
    addIssue(
        "부천시 원미구 중앙로 2",
        37.5010000,
        126.7600000,
        "REPEATED_FLOODING",
        "중앙 배수로 반복 침수",
        "검토 중",
        "UNDER_REVIEW");
    addIssue(
        "부천시 원미구 중앙로 3",
        37.5020000,
        126.7600000,
        "REPEATED_FLOODING",
        "중앙 배수로 반복 침수",
        "현장점검 필요",
        "INSPECTION_REQUIRED");
    addIssue(
        "부천시 소사구 남부로 1",
        37.4700000,
        126.7900000,
        "SEWAGE_BACKFLOW",
        "하수 역류",
        "조치 진행",
        "ACTION_IN_PROGRESS");
    addIssue(
        "부천시 오정구 북부로 1",
        37.5200000,
        126.7800000,
        "BLOCKED_STORM_DRAIN",
        "빗물받이 막힘",
        "조치 완료",
        "COMPLETED");
    addIssue(
        "서울특별시 구로구 중앙로 1",
        37.5000000,
        126.7600000,
        "REPEATED_FLOODING",
        "중앙 관할 외",
        "검토 대기",
        "RECEIVED");

    mvc.perform(get("/api/admin/public-issues/status").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("PUBLIC_ISSUE_200_6"))
        .andExpect(jsonPath("$.result.length()").value(4))
        .andExpect(jsonPath("$.result.registeredCount").value(5))
        .andExpect(jsonPath("$.result.pendingOrUnderReviewCount").value(3))
        .andExpect(jsonPath("$.result.actionInProgressCount").value(1))
        .andExpect(jsonPath("$.result.completedCount").value(1));

    mvc.perform(
            get("/api/admin/public-issues/status")
                .header("Authorization", "Bearer " + token)
                .param("district", "원미구")
                .param("issueType", "REPEATED_FLOODING")
                .param("keyword", "  중앙  "))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.registeredCount").value(3))
        .andExpect(jsonPath("$.result.pendingOrUnderReviewCount").value(3))
        .andExpect(jsonPath("$.result.actionInProgressCount").value(0))
        .andExpect(jsonPath("$.result.completedCount").value(0));

    mvc.perform(
            get("/api/admin/public-issues/status")
                .header("Authorization", "Bearer " + token)
                .param("keyword", "없는 문제"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.registeredCount").value(0))
        .andExpect(jsonPath("$.result.pendingOrUnderReviewCount").value(0))
        .andExpect(jsonPath("$.result.actionInProgressCount").value(0))
        .andExpect(jsonPath("$.result.completedCount").value(0));
  }

  @Test
  void statusCountsValidateFiltersAndOperatorAccess() throws Exception {
    mvc.perform(
            get("/api/admin/public-issues/status")
                .header("Authorization", "Bearer " + token)
                .param("district", "중구"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_400_3"));
    mvc.perform(
            get("/api/admin/public-issues/status")
                .header("Authorization", "Bearer " + token)
                .param("issueType", "UNKNOWN"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_400_3"));
    mvc.perform(get("/api/admin/public-issues/status")).andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/admin/public-issues/status")
                .header("Authorization", "Bearer " + residentToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void returnsMapMarkersWithListFiltersInStableOrder() throws Exception {
    long first =
        addIssue(
            "부천시 원미구 중앙로 1",
            37.5000000,
            126.7600000,
            "REPEATED_FLOODING",
            "중앙 배수로 반복 침수",
            "현장 확인",
            "UNDER_REVIEW");
    long second =
        addIssue(
            "부천시 원미구 중앙로 2",
            37.5010000,
            126.7610000,
            "REPEATED_FLOODING",
            "중앙 배수로 반복 침수",
            "현장 확인",
            "UNDER_REVIEW");
    addIssue(
        "부천시 소사구 남부로 1", 37.4700000, 126.7900000, "SEWAGE_BACKFLOW", "하수 역류", "조치 완료", "COMPLETED");
    addIssue(
        "서울특별시 구로구 중앙로 1",
        37.5000000,
        126.7600000,
        "REPEATED_FLOODING",
        "중앙 관할 외",
        "현장 확인",
        "UNDER_REVIEW");

    mvc.perform(
            get("/api/admin/map/public-issues")
                .header("Authorization", "Bearer " + token)
                .param("district", "원미구")
                .param("status", "UNDER_REVIEW")
                .param("issueType", "REPEATED_FLOODING")
                .param("keyword", "  중앙  "))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("PUBLIC_ISSUE_200_7"))
        .andExpect(jsonPath("$.result.length()").value(1))
        .andExpect(jsonPath("$.result.markers.length()").value(2))
        .andExpect(jsonPath("$.result.markers[0].length()").value(4))
        .andExpect(jsonPath("$.result.markers[0].publicIssueId").value(second))
        .andExpect(jsonPath("$.result.markers[0].latitude").value(37.5010000))
        .andExpect(jsonPath("$.result.markers[0].longitude").value(126.7610000))
        .andExpect(jsonPath("$.result.markers[0].status").value("UNDER_REVIEW"))
        .andExpect(jsonPath("$.result.markers[1].publicIssueId").value(first));

    mvc.perform(
            get("/api/admin/map/public-issues")
                .header("Authorization", "Bearer " + token)
                .param("keyword", "없는 문제"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.markers").isEmpty());
  }

  @Test
  void mapMarkersValidateFiltersAndOperatorAccess() throws Exception {
    for (var values :
        List.of(
            new String[] {"district", "중구"},
            new String[] {"status", "UNKNOWN"},
            new String[] {"issueType", "UNKNOWN"},
            new String[] {"keyword", "가".repeat(256)}))
      mvc.perform(
              get("/api/admin/map/public-issues")
                  .header("Authorization", "Bearer " + token)
                  .param(values[0], values[1]))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("COMMON_400_3"));
    mvc.perform(get("/api/admin/map/public-issues")).andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/admin/map/public-issues").header("Authorization", "Bearer " + residentToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void swaggerPublishesListSearchContract() throws Exception {
    var response = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
    var operation =
        mapper
            .readTree(response.getResponse().getContentAsString())
            .path("paths")
            .path("/api/admin/public-issues")
            .path("get");
    assertThat(operation.path("summary").asString()).isEqualTo("지역·공공 문제 목록 조회·검색");
    var parameters = new ArrayList<String>();
    for (var parameter : operation.path("parameters"))
      parameters.add(parameter.path("name").asString());
    assertThat(parameters)
        .containsExactlyInAnyOrder("district", "status", "issueType", "keyword", "page", "size");

    var statusOperation =
        mapper
            .readTree(response.getResponse().getContentAsString())
            .path("paths")
            .path("/api/admin/public-issues/status")
            .path("get");
    assertThat(statusOperation.path("summary").asString()).isEqualTo("지역·공공 문제 현황 건수 조회");
    var statusParameters = new ArrayList<String>();
    for (var parameter : statusOperation.path("parameters"))
      statusParameters.add(parameter.path("name").asString());
    assertThat(statusParameters).containsExactlyInAnyOrder("district", "issueType", "keyword");

    var mapOperation =
        mapper
            .readTree(response.getResponse().getContentAsString())
            .path("paths")
            .path("/api/admin/map/public-issues")
            .path("get");
    assertThat(mapOperation.path("summary").asString()).isEqualTo("지역·공공 문제 지도 조회");
    var mapParameters = new ArrayList<String>();
    for (var parameter : mapOperation.path("parameters"))
      mapParameters.add(parameter.path("name").asString());
    assertThat(mapParameters)
        .containsExactlyInAnyOrder("district", "status", "issueType", "keyword");
  }
}
