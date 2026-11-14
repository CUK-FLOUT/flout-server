package com.flout.server.domain.supportapplication;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.inspection.entity.Inspection;
import com.flout.server.domain.inspection.repository.InspectionRepository;
import com.flout.server.domain.inspectionapplication.dto.request.CreateInspectionApplicationRequest;
import com.flout.server.domain.inspectionapplication.entity.*;
import com.flout.server.domain.inspectionapplication.repository.InspectionApplicationRepository;
import com.flout.server.domain.riskassessment.entity.*;
import com.flout.server.domain.riskassessment.repository.RiskAssessmentRepository;
import com.flout.server.domain.supportapplication.client.GeminiSupportClient;
import com.flout.server.domain.supportapplication.response.code.SupportApplicationErrorCode;
import com.flout.server.domain.supportprogram.entity.SupportProgram;
import com.flout.server.domain.supportprogram.repository.SupportProgramRepository;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.exception.BaseException;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:support-program-matching;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false",
      "app.support.fallback-guidance.id=BUCHEON-CONSULT",
      "app.support.fallback-guidance.title=공식 상담 안내",
      "app.support.fallback-guidance.description=담당 기관에서 자격을 확인하세요",
      "app.support.fallback-guidance.official-url=https://www.bucheon.go.kr"
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
  @MockitoBean GeminiSupportClient gemini;

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
            "prevention_recommendations",
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

    when(gemini.decide(anyMap()))
        .thenReturn(
            new GeminiSupportClient.Decision(
                "MATCHED",
                "PROGRAM",
                entrance.getId(),
                null,
                "DIRECT",
                "출입구의 침수 위험에 물막이판 설치 지원이 관련됩니다.",
                List.of()));

    mvc.perform(matching(ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("SUPPORT_200_3"))
        .andExpect(jsonPath("$.result.supportApplicationId").value(applicationId))
        .andExpect(jsonPath("$.result.matchStatus").value("NEEDS_CONFIRMATION"))
        .andExpect(jsonPath("$.result.recommendation.itemType").value("PROGRAM"))
        .andExpect(jsonPath("$.result.recommendation.matchType").value("DIRECT"))
        .andExpect(jsonPath("$.result.recommendation.programId").value(entrance.getId()))
        .andExpect(jsonPath("$.result.recommendation.program.name").value("물막이판 설치 지원"))
        .andExpect(jsonPath("$.result.recommendation.program.description", containsString("물막이판")))
        .andExpect(
            jsonPath("$.result.recommendation.program.targetDescription").value("부천시 침수 취약 주택"))
        .andExpect(jsonPath("$.result.recommendation.reason", containsString("출입구")))
        .andExpect(jsonPath("$.result.recommendation.missingInformation", hasItem("신청 자격 확인 필요")))
        .andExpect(jsonPath("$.result.recommendation.guidance").value(nullValue()))
        .andExpect(jsonPath("$.result.address").doesNotExist())
        .andExpect(jsonPath("$.result.detailAddress").doesNotExist())
        .andExpect(jsonPath("$.result.latitude").doesNotExist())
        .andExpect(jsonPath("$.result.longitude").doesNotExist())
        .andExpect(jsonPath("$.result.shapValues").doesNotExist());

    verify(gemini).decide(anyMap());
  }

  @Test
  void handlesNullVulnerabilityTypeAndUsesOnlyTheAddressMatchReason() throws Exception {
    Long resultId = publishResult("점검 결과");
    jdbc.update(
        "INSERT INTO inspection_vulnerabilities(result_id,type,title,sort_order) VALUES(?,NULL,'관련 없는 취약점',0)",
        resultId);
    programs.saveAndFlush(SupportProgram.create("다른 지역 사업", "설명", "서울시 지원 대상", "접수중"));
    var bucheon = programs.saveAndFlush(SupportProgram.create("지역 지원사업", "설명", "부천시 지원 대상", "접수중"));
    when(gemini.decide(anyMap()))
        .thenReturn(
            new GeminiSupportClient.Decision(
                "NEEDS_CONFIRMATION",
                "PROGRAM",
                bucheon.getId(),
                null,
                "COMPLEMENTARY",
                "거주 지역과 관련된 사업으로 검토할 수 있습니다.",
                List.of("시설 관련성 확인 필요")));

    mvc.perform(matching(ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.matchStatus").value("NEEDS_CONFIRMATION"))
        .andExpect(jsonPath("$.result.recommendation.programId").value(bucheon.getId()))
        .andExpect(jsonPath("$.result.recommendation.matchType").value("COMPLEMENTARY"))
        .andExpect(jsonPath("$.result.recommendation.reason", containsString("거주 지역")))
        .andExpect(jsonPath("$.result.recommendation.missingInformation", hasItem("시설 관련성 확인 필요")));
  }

  @Test
  void returnsARealProgramWhenGeminiUnexpectedlyChoosesConsultation() throws Exception {
    publishResult("점검 결과");
    var fallback =
        programs.saveAndFlush(
            SupportProgram.create("일반 주거 지원", "침수와 관련 없는 사업", "서울시 지원 대상", "접수중"));
    when(gemini.decide(anyMap()))
        .thenReturn(
            new GeminiSupportClient.Decision(
                "GUIDANCE",
                "CONSULTATION",
                null,
                "BUCHEON-CONSULT",
                "GUIDANCE",
                "관련 사업을 찾지 못했습니다.",
                List.of()));

    mvc.perform(matching(ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.matchStatus").value("NEEDS_CONFIRMATION"))
        .andExpect(jsonPath("$.result.recommendation.itemType").value("PROGRAM"))
        .andExpect(jsonPath("$.result.recommendation.programId").value(fallback.getId()))
        .andExpect(jsonPath("$.result.recommendation.matchType").value("COMPLEMENTARY"))
        .andExpect(jsonPath("$.result.recommendation.guidanceId").value(nullValue()))
        .andExpect(jsonPath("$.result.recommendation.program.name").value("일반 주거 지원"));
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
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("SUPPORT_500_1"));

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
    verifyNoInteractions(gemini);
  }

  @Test
  void swaggerExposesTheSpecifiedPostPathAndResponseSchema() throws Exception {
    String operation =
        "$.paths['/api/resident/support-application/{supportApplicationId}/matching'].post";
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath(operation + ".summary").value("지원사업 AI 매칭"))
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
                        + ".responses['503'].content['application/json'].examples.geminiRetryRequired.value.code")
                .value("SUPPORT_503_1"))
        .andExpect(
            jsonPath(
                    operation
                        + ".responses['200'].content['application/json'].examples.supportProgramMatched.value.result.matchStatus")
                .value("NEEDS_CONFIRMATION"))
        .andExpect(
            jsonPath(
                    operation
                        + ".responses['200'].content['application/json'].examples.supportProgramMatched.value.result.recommendation.itemType")
                .value("PROGRAM"))
        .andExpect(
            jsonPath("$.components.schemas.SupportProgramMatchingResponse.properties.matchStatus")
                .exists())
        .andExpect(
            jsonPath(
                    "$.components.schemas.SupportProgramMatchingResponse.properties.recommendation")
                .exists())
        .andExpect(
            jsonPath(
                    "$.components.schemas.SupportProgramMatchingResponse.properties.eligibilityConfirmed")
                .doesNotExist());
  }

  @Test
  void fallsBackToARealProgramForUnrecognizedGeminiProgramId() throws Exception {
    publishResult("점검 결과");
    var fallback =
        programs.saveAndFlush(SupportProgram.create("물막이판 설치 지원", "출입구 설치", "부천시", "접수중"));
    when(gemini.decide(anyMap()))
        .thenReturn(
            new GeminiSupportClient.Decision(
                "MATCHED", "PROGRAM", Long.MAX_VALUE, null, "DIRECT", "추천", List.of()));

    mvc.perform(matching(ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.matchStatus").value("NEEDS_CONFIRMATION"))
        .andExpect(jsonPath("$.result.recommendation.programId").value(fallback.getId()))
        .andExpect(jsonPath("$.result.recommendation.matchType").value("COMPLEMENTARY"))
        .andExpect(jsonPath("$.result.recommendation.guidanceId").value(nullValue()));
  }

  @Test
  void keepsClosedProgramAsAReferenceCandidate() throws Exception {
    publishResult("점검 결과");
    var program = programs.saveAndFlush(SupportProgram.create("종료된 지원사업", "출입구 설치", "부천시", "접수종료"));
    jdbc.update(
        "UPDATE support_programs SET application_status='CLOSED' WHERE program_id=?",
        program.getId());
    when(gemini.decide(anyMap()))
        .thenReturn(
            new GeminiSupportClient.Decision(
                "NEEDS_CONFIRMATION",
                "PROGRAM",
                program.getId(),
                null,
                "COMPLEMENTARY",
                "현재 종료된 사업이지만 참고용으로 안내합니다.",
                List.of()));

    mvc.perform(matching(ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.matchStatus").value("NEEDS_CONFIRMATION"))
        .andExpect(jsonPath("$.result.recommendation.programId").value(program.getId()))
        .andExpect(jsonPath("$.result.recommendation.program.applicationStatus").value("CLOSED"));
    verify(gemini).decide(anyMap());
  }

  @Test
  void directFacilityMatchIncludesOnlyRealExpertActionId() throws Exception {
    Long resultId = publishResult("현장점검 결과");
    jdbc.update(
        "INSERT INTO prevention_recommendations(result_id,description,sort_order) VALUES(?,?,?)",
        resultId,
        "출입구에 물막이판 설치를 권장합니다.",
        0);
    jdbc.update(
        "INSERT INTO prevention_recommendations(result_id,description,sort_order) VALUES(?,?,?)",
        resultId,
        "배수펌프 설치를 권장합니다.",
        1);
    Long recommendedId =
        jdbc.queryForObject(
            "SELECT recommendation_id FROM prevention_recommendations WHERE result_id=? AND sort_order=0",
            Long.class,
            resultId);
    var program = programs.saveAndFlush(SupportProgram.create("물막이판 지원", "설치", "부천시", "접수중"));
    jdbc.update(
        "UPDATE support_programs SET supported_facilities=? WHERE program_id=?",
        "[\"물막이판\"]",
        program.getId());
    when(gemini.decide(anyMap()))
        .thenReturn(
            new GeminiSupportClient.Decision(
                "MATCHED", "PROGRAM", program.getId(), null, "DIRECT", "권장시설과 일치", List.of()));

    mvc.perform(matching(ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.recommendation.matchedActionIds[0]").value(recommendedId))
        .andExpect(jsonPath("$.result.recommendation.matchedActionIds.length()").value(1))
        .andExpect(
            jsonPath(
                "$.result.recommendation.evidence[*].sourceField",
                hasItem("preventionRecommendation." + recommendedId)));
  }

  @Test
  void directFacilityMismatchStillReturnsTheChosenProgramAsComplementary() throws Exception {
    Long resultId = publishResult("현장점검 결과");
    jdbc.update(
        "INSERT INTO prevention_recommendations(result_id,description,sort_order) VALUES(?,?,?)",
        resultId,
        "배수펌프 설치를 권장합니다.",
        0);
    var program = programs.saveAndFlush(SupportProgram.create("물막이판 지원", "설치", "부천시", "접수중"));
    jdbc.update(
        "UPDATE support_programs SET supported_facilities=? WHERE program_id=?",
        "[\"물막이판\"]",
        program.getId());
    when(gemini.decide(anyMap()))
        .thenReturn(
            new GeminiSupportClient.Decision(
                "MATCHED", "PROGRAM", program.getId(), null, "DIRECT", "추천", List.of()));

    mvc.perform(matching(ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.matchStatus").value("NEEDS_CONFIRMATION"))
        .andExpect(jsonPath("$.result.recommendation.itemType").value("PROGRAM"))
        .andExpect(jsonPath("$.result.recommendation.programId").value(program.getId()))
        .andExpect(jsonPath("$.result.recommendation.matchType").value("COMPLEMENTARY"));
  }

  @Test
  void complementaryRecommendationDoesNotClaimDirectExpertFacilityMatch() throws Exception {
    Long resultId = publishResult("현장점검 결과");
    jdbc.update(
        "INSERT INTO prevention_recommendations(result_id,description,sort_order) VALUES(?,?,?)",
        resultId,
        "물막이판 설치를 권장합니다.",
        0);
    var program = programs.saveAndFlush(SupportProgram.create("이주 지원", "상담", "부천시", "문의 필요"));
    jdbc.update(
        "UPDATE support_programs SET supported_facilities='[]' WHERE program_id=?",
        program.getId());
    when(gemini.decide(anyMap()))
        .thenReturn(
            new GeminiSupportClient.Decision(
                "NEEDS_CONFIRMATION",
                "PROGRAM",
                program.getId(),
                null,
                "COMPLEMENTARY",
                "이주 상담 안내",
                List.of()));

    mvc.perform(matching(ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.recommendation.matchType").value("COMPLEMENTARY"))
        .andExpect(jsonPath("$.result.recommendation.matchedActionIds.length()").value(0));
  }

  @Test
  void returnsRetryableErrorWhenGeminiIsUnavailable() throws Exception {
    publishResult("점검 결과");
    programs.saveAndFlush(SupportProgram.create("물막이판 설치 지원", "출입구 설치", "부천시", "접수중"));
    when(gemini.decide(anyMap()))
        .thenThrow(new BaseException(SupportApplicationErrorCode.GEMINI_RETRY_REQUIRED));

    mvc.perform(matching(ownerToken))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("SUPPORT_503_1"));
  }
}
