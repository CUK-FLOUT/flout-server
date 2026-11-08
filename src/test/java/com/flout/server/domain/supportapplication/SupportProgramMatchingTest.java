package com.flout.server.domain.supportapplication;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.inspection.entity.Inspection;
import com.flout.server.domain.inspection.repository.InspectionRepository;
import com.flout.server.domain.inspectionapplication.dto.request.CreateInspectionApplicationRequest;
import com.flout.server.domain.inspectionapplication.entity.*;
import com.flout.server.domain.inspectionapplication.repository.InspectionApplicationRepository;
import com.flout.server.domain.riskassessment.entity.*;
import com.flout.server.domain.riskassessment.repository.RiskAssessmentRepository;
import com.flout.server.domain.supportprogram.entity.SupportProgram;
import com.flout.server.domain.supportprogram.repository.SupportProgramRepository;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
      "spring.datasource.url=jdbc:h2:mem:support-program-matching;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false"
    })
@AutoConfigureMockMvc
class SupportProgramMatchingTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired RiskAssessmentRepository assessments;
  @Autowired InspectionApplicationRepository applications;
  @Autowired InspectionRepository inspections;
  @Autowired SupportProgramRepository programs;
  @Autowired JwtService jwt;

  Long applicationId;
  Long inspectionId;
  String ownerToken;
  String otherToken;

  @BeforeEach
  void setup() {
    for (String table :
        List.of(
            "support_attachments",
            "support_application_status_histories",
            "constructions",
            "support_applications",
            "support_programs",
            "inspection_vulnerabilities",
            "inspection_results",
            "inspection_status_histories",
            "inspections",
            "application_inflow_locations",
            "application_status_histories",
            "inspection_applications",
            "risk_assessments",
            "refresh_tokens",
            "user_consents",
            "users")) {
      jdbc.update("DELETE FROM " + table);
    }

    User owner = users.saveAndFlush(User.resident("matching-owner@example.com", "hash", "김주민"));
    User other = users.saveAndFlush(User.resident("matching-other@example.com", "hash", "타인"));
    ownerToken = jwt.issue(owner).accessToken();
    otherToken = jwt.issue(other).accessToken();

    var assessment =
        assessments.saveAndFlush(
            RiskAssessment.completedDemo(owner, "경기도 부천시 원미구 길주로 53", "101호", RiskLevel.HIGH));
    var request =
        new CreateInspectionApplicationRequest(
            assessment.getId(),
            null,
            assessment.getAddress(),
            assessment.getDetailAddress(),
            new BigDecimal("37.5"),
            new BigDecimal("126.7"),
            "010-1234-1234",
            OccupancyType.TENANT,
            FloodExperience.YES,
            false,
            "침수 예방시설이 필요해요.",
            LocalDate.of(2026, 11, 10),
            PreferredTimeSlot.ANY,
            null,
            "김주민");
    var application =
        applications.saveAndFlush(
            InspectionApplication.received(owner, assessment, request, "IA-MATCHING"));
    applicationId = application.getId();
    inspectionId = inspections.saveAndFlush(Inspection.received(application)).getId();
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder matching(
      String token) {
    return post("/api/resident/support-application/{id}/matching", applicationId)
        .header("Authorization", "Bearer " + token);
  }

  private Long publishResult(String summary) {
    jdbc.update(
        "INSERT INTO inspection_results(inspection_id,summary,result_status) VALUES(?,?,'PUBLISHED')",
        inspectionId,
        summary);
    return jdbc.queryForObject(
        "SELECT result_id FROM inspection_results WHERE inspection_id=? AND result_status='PUBLISHED'",
        Long.class,
        inspectionId);
  }

  @Test
  void matchesTheMostRelevantProgramFromPublishedFindingsAndAddress() throws Exception {
    Long resultId = publishResult("출입구를 통한 침수 위험이 확인되었습니다.");
    jdbc.update(
        "INSERT INTO inspection_vulnerabilities(result_id,type,title,expert_opinion,sort_order) VALUES(?,'ENTRANCE','출입구','물막이판 설치가 필요합니다.',0)",
        resultId);
    programs.saveAndFlush(
        SupportProgram.create("배수펌프 설치 지원", "저지대 주택의 배수 설비를 지원합니다.", "배수 취약 주택", "접수중"));
    var entrance =
        programs.saveAndFlush(
            SupportProgram.create(
                "물막이판 설치 지원", "침수 예방을 위한 출입구 물막이판 설치 비용을 지원합니다.", "부천시 침수 취약 주택", "접수중"));

    mvc.perform(matching(ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("SUPPORT_200_3"))
        .andExpect(jsonPath("$.result.supportApplicationId").value(applicationId))
        .andExpect(jsonPath("$.result.programId").value(entrance.getId()))
        .andExpect(jsonPath("$.result.programName").value("물막이판 설치 지원"))
        .andExpect(jsonPath("$.result.jurisdiction").value("부천시"))
        .andExpect(jsonPath("$.result.description", containsString("물막이판")))
        .andExpect(jsonPath("$.result.targetDescription").value("부천시 침수 취약 주택"))
        .andExpect(jsonPath("$.result.operatingStatus").value("접수중"))
        .andExpect(jsonPath("$.result.matchedReason").value("현장점검에서 출입구 취약지점이 확인됐어요."))
        .andExpect(jsonPath("$.result.eligibilityConfirmed").value(false))
        .andExpect(jsonPath("$.result.address").doesNotExist())
        .andExpect(jsonPath("$.result.detailAddress").doesNotExist())
        .andExpect(jsonPath("$.result.latitude").doesNotExist())
        .andExpect(jsonPath("$.result.longitude").doesNotExist())
        .andExpect(jsonPath("$.result.shapValues").doesNotExist());
  }

  @Test
  void matchesByAddressWhenNoVulnerabilityWasRegistered() throws Exception {
    publishResult("점검 결과");
    programs.saveAndFlush(SupportProgram.create("다른 지역 사업", "설명", "서울시 지원 대상", "접수중"));
    var bucheon = programs.saveAndFlush(SupportProgram.create("지역 지원사업", "설명", "부천시 지원 대상", "접수중"));

    mvc.perform(matching(ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.programId").value(bucheon.getId()))
        .andExpect(jsonPath("$.result.matchedReason").value("현장점검 결과를 바탕으로 확인할 수 있는 지원사업이에요."));
  }

  @Test
  void doesNotRecommendAnUnrelatedProgramAsAFallback() throws Exception {
    publishResult("점검 결과");
    programs.saveAndFlush(SupportProgram.create("일반 주거 지원", "침수와 관련 없는 사업", "서울시 지원 대상", "접수중"));

    mvc.perform(matching(ownerToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SUPPORT_404_3"))
        .andExpect(jsonPath("$.result").value(nullValue()));
  }

  @Test
  void enforcesAuthenticationOwnershipPublishedResultAndAvailablePrograms() throws Exception {
    mvc.perform(post("/api/resident/support-application/{id}/matching", applicationId))
        .andExpect(status().isUnauthorized());
    mvc.perform(matching(otherToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("APPLICATION_403_6"));
    mvc.perform(matching(ownerToken))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SUPPORT_400_2"));

    publishResult("점검 결과");
    mvc.perform(matching(ownerToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SUPPORT_404_2"));
    mvc.perform(
            post("/api/resident/support-application/0/matching")
                .header("Authorization", "Bearer " + ownerToken))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/resident/support-application/9223372036854775807/matching")
                .header("Authorization", "Bearer " + ownerToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("APPLICATION_404_2"));
    mvc.perform(
            post("/api/resident/support-application/not-a-number/matching")
                .header("Authorization", "Bearer " + ownerToken))
        .andExpect(status().isBadRequest());
  }

  @Test
  void swaggerExposesTheSpecifiedPostPathAndResponseSchema() throws Exception {
    String operation =
        "$.paths['/api/resident/support-application/{supportApplicationId}/matching'].post";
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath(operation + ".summary").value("지원사업 매칭"))
        .andExpect(
            jsonPath(
                    operation
                        + ".responses['200'].content['application/json'].examples.supportProgramMatched.value.code")
                .value("SUPPORT_200_3"))
        .andExpect(
            jsonPath(
                    operation
                        + ".responses['400'].content['application/json'].examples.resultNotPublished.value.code")
                .value("SUPPORT_400_2"))
        .andExpect(
            jsonPath(
                    operation
                        + ".responses['403'].content['application/json'].examples.applicationAccessDenied.value.code")
                .value("APPLICATION_403_6"))
        .andExpect(
            jsonPath(
                    operation
                        + ".responses['404'].content['application/json'].examples.matchedProgramNotFound.value.code")
                .value("SUPPORT_404_3"))
        .andExpect(
            jsonPath(
                    "$.components.schemas.SupportProgramMatchingResponse.properties.eligibilityConfirmed")
                .exists())
        .andExpect(
            jsonPath("$.components.schemas.SupportProgramMatchingResponse.properties.matchedReason")
                .exists());
  }
}
