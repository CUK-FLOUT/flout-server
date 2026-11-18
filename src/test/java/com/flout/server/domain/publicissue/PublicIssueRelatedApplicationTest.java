package com.flout.server.domain.publicissue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:public-issue-related-applications;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false",
      "app.fcm.enabled=false",
      "app.s3.cleanup-interval-millis=3600000"
    })
@AutoConfigureMockMvc
class PublicIssueRelatedApplicationTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;

  long residentId;
  String adminToken;
  String residentToken;

  @BeforeEach
  void setup() {
    for (String table :
        List.of(
            "public_issues",
            "inspections",
            "inspection_applications",
            "risk_assessments",
            "refresh_tokens",
            "users")) {
      jdbc.update("DELETE FROM " + table);
    }

    var resident =
        users.saveAndFlush(User.resident("resident@related-applications.test", "unused", "김주민"));
    residentId = resident.getId();
    residentToken = jwt.issue(resident).accessToken();

    var admin =
        users.saveAndFlush(User.resident("admin@related-applications.test", "unused", "박운영자"));
    jdbc.update(
        "UPDATE users SET role='ADMIN', jurisdiction_id=(SELECT jurisdiction_id FROM jurisdictions WHERE name='부천시') WHERE user_id=?",
        admin.getId());
    adminToken = jwt.issue(users.findById(admin.getId()).orElseThrow()).accessToken();
  }

  @Test
  void returnsOnlyNormalizedAddressMatchesExcludingDraftsInStableOrder() throws Exception {
    long issueId =
        addIssue(" 경기  부천시 원미구 길주로 210 ", "경기도 부천시 원미구 원본로 1", LocalDateTime.of(2026, 11, 1, 9, 0));

    var older =
        addApplication(
            "경기도 부천시 원미구 길주로 210", "LOW", null, "RECEIVED", LocalDateTime.of(2026, 11, 2, 10, 0));
    var newerFirst =
        addApplication(
            "경기 부천시 원미구 길주로 210",
            "CAUTION",
            "배수구 확인 필요",
            "RECEIVED",
            LocalDateTime.of(2026, 11, 3, 11, 0));
    var newerSecond =
        addApplication(
            " 경기도 부천시 원미구 길주로 210 ",
            "HIGH",
            "현관 유입 확인",
            "RECEIVED",
            LocalDateTime.of(2026, 11, 3, 11, 0));
    addApplication(
        "경기도 부천시 원미구 길주로 210", "HIGH", "임시저장", "DRAFT", LocalDateTime.of(2026, 11, 4, 12, 0));
    addApplication(
        "경기도 부천시 원미구 길주로 211", "HIGH", "다른 주소", "RECEIVED", LocalDateTime.of(2026, 11, 5, 13, 0));

    int applicationCountBefore =
        jdbc.queryForObject("SELECT COUNT(*) FROM inspection_applications", Integer.class);

    mvc.perform(
            get("/api/admin/public-issues/{publicIssueId}/resident-applications", issueId)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("PUBLIC_ISSUE_200_9"))
        .andExpect(jsonPath("$.result.publicIssueId").value(issueId))
        .andExpect(jsonPath("$.result.totalCount").value(3))
        .andExpect(jsonPath("$.result.applications.length()").value(3))
        .andExpect(jsonPath("$.result.applications[0].applicationId").value(newerSecond.id()))
        .andExpect(jsonPath("$.result.applications[0].receiptNo").value(receipt(newerSecond.id())))
        .andExpect(jsonPath("$.result.applications[0].vulnerabilityGrade").value("HIGH"))
        .andExpect(jsonPath("$.result.applications[0].fieldFindings").value("현관 유입 확인"))
        .andExpect(jsonPath("$.result.applications[1].applicationId").value(newerFirst.id()))
        .andExpect(jsonPath("$.result.applications[1].vulnerabilityGrade").value("CAUTION"))
        .andExpect(jsonPath("$.result.applications[2].applicationId").value(older.id()))
        .andExpect(jsonPath("$.result.applications[2].fieldFindings").doesNotExist());

    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inspection_applications", Integer.class))
        .isEqualTo(applicationCountBefore);
  }

  @Test
  void returnsAnEmptyResultWhenNoApplicationHasTheSameAddress() throws Exception {
    long issueId =
        addIssue("경기도 부천시 소사구 경인로 10", "경기도 부천시 소사구 원본로 1", LocalDateTime.of(2026, 11, 1, 9, 0));

    mvc.perform(
            get("/api/admin/public-issues/{publicIssueId}/resident-applications", issueId)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalCount").value(0))
        .andExpect(jsonPath("$.result.applications").isEmpty());
  }

  @Test
  void validatesIdExistenceAuthenticationRoleAndReadJurisdiction() throws Exception {
    long issueId =
        addIssue("경기도 부천시 오정구 성오로 1", "경기도 부천시 오정구 원본로 1", LocalDateTime.of(2026, 11, 1, 9, 0));

    mvc.perform(
            get("/api/admin/public-issues/{publicIssueId}/resident-applications", 0)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_400_3"));

    mvc.perform(
            get("/api/admin/public-issues/{publicIssueId}/resident-applications", 999999)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PUBLIC_ISSUE_404_1"));

    mvc.perform(get("/api/admin/public-issues/{publicIssueId}/resident-applications", issueId))
        .andExpect(status().isUnauthorized());

    mvc.perform(
            get("/api/admin/public-issues/{publicIssueId}/resident-applications", issueId)
                .header("Authorization", "Bearer " + residentToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("COMMON_403_1"));

    var noJurisdictionAdmin =
        users.saveAndFlush(
            User.resident("admin-without-jurisdiction@related-applications.test", "unused", "무관할"));
    jdbc.update("UPDATE users SET role='ADMIN' WHERE user_id=?", noJurisdictionAdmin.getId());
    String noJurisdictionToken =
        jwt.issue(users.findById(noJurisdictionAdmin.getId()).orElseThrow()).accessToken();

    mvc.perform(
            get("/api/admin/public-issues/{publicIssueId}/resident-applications", issueId)
                .header("Authorization", "Bearer " + noJurisdictionToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("INSPECTION_403_1"));

    long outsideIssueId =
        addIssue("서울특별시 구로구 경인로 1", "경기도 부천시 오정구 원본로 2", LocalDateTime.of(2026, 11, 2, 9, 0));
    mvc.perform(
            get("/api/admin/public-issues/{publicIssueId}/resident-applications", outsideIssueId)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("INSPECTION_403_1"));
  }

  private long addIssue(String issueAddress, String sourceAddress, LocalDateTime sourceCreatedAt) {
    var source = addApplication(sourceAddress, "HIGH", "원본 신청", "RECEIVED", sourceCreatedAt);
    jdbc.update(
        "INSERT INTO inspections(application_id,status) VALUES(?,'COMPLETED')", source.id());
    long inspectionId =
        jdbc.queryForObject("SELECT MAX(inspection_id) FROM inspections", Long.class);
    jdbc.update(
        "INSERT INTO public_issues(source_inspection_id,address,latitude,longitude,issue_type,description,field_findings,status) VALUES(?,?,37.5000000,126.7600000,'BLOCKED_STORM_DRAIN','빗물받이 막힘','낙엽 퇴적','RECEIVED')",
        inspectionId,
        issueAddress);
    return jdbc.queryForObject("SELECT MAX(public_issue_id) FROM public_issues", Long.class);
  }

  private ApplicationSeed addApplication(
      String address,
      String riskLevel,
      String requestReason,
      String status,
      LocalDateTime createdAt) {
    jdbc.update(
        "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status) VALUES(?,?,'101호',?,'COMPLETED')",
        residentId,
        address,
        riskLevel);
    long assessmentId =
        jdbc.queryForObject("SELECT MAX(assessment_id) FROM risk_assessments", Long.class);
    jdbc.update(
        "INSERT INTO inspection_applications(applicant_name,applicant_user_id,assessment_id,receipt_no,address,detail_address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,request_reason,status) VALUES('김주민',?,?,?,?,'101호',37.5000000,126.7600000,'010-1234-5678','OWNER','NO',false,?,?)",
        residentId,
        assessmentId,
        UUID.randomUUID().toString().substring(0, 20),
        address,
        requestReason,
        status);
    long applicationId =
        jdbc.queryForObject("SELECT MAX(application_id) FROM inspection_applications", Long.class);
    jdbc.update(
        "UPDATE inspection_applications SET created_at=? WHERE application_id=?",
        createdAt,
        applicationId);
    return new ApplicationSeed(applicationId);
  }

  private String receipt(long applicationId) {
    return String.format(java.util.Locale.ROOT, "%06d", applicationId);
  }

  private record ApplicationSeed(long id) {}
}
