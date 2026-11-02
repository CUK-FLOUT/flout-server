package com.flout.server.domain.inspectionapplication.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.inspectionapplication.dto.request.CreateInspectionApplicationRequest;
import com.flout.server.domain.inspectionapplication.dto.response.CreateInspectionApplicationResponse;
import com.flout.server.domain.inspectionapplication.entity.*;
import com.flout.server.domain.inspectionapplication.repository.*;
import com.flout.server.domain.riskassessment.entity.*;
import com.flout.server.domain.riskassessment.repository.RiskAssessmentRepository;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:internal-memo;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
    })
@AutoConfigureMockMvc
class InternalMemoTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired RiskAssessmentRepository assessments;
  @Autowired InspectionApplicationRepository applications;
  @Autowired ApplicationStatusHistoryRepository histories;
  @Autowired JwtService jwt;
  @Autowired ObjectMapper mapper;
  User resident;
  Long operatorId;
  String operatorToken;
  Long applicationId;

  @BeforeEach
  void setup() {
    for (String table :
        List.of(
            "application_inflow_locations",
            "application_status_histories",
            "inspection_applications",
            "prevention_guides",
            "risk_assessments",
            "refresh_tokens",
            "user_consents",
            "users")) {
      jdbc.update("DELETE FROM " + table);
    }
    resident = users.saveAndFlush(User.resident("resident@example.com", "hash", "주민"));
    jdbc.update(
        """
        INSERT INTO users (email, password_hash, name, role, jurisdiction_id)
        VALUES ('operator@example.com', 'hash', '김담당', 'ADMIN',
          (SELECT jurisdiction_id FROM jurisdictions WHERE name = '부천시'))
        """);
    var operator = users.findByEmail("operator@example.com").orElseThrow();
    operatorId = operator.getId();
    operatorToken = jwt.issue(operator).accessToken();
    var assessment =
        assessments.saveAndFlush(
            RiskAssessment.completedDemo(resident, "경기도 부천시 원미구 길주로 210", "101호", RiskLevel.HIGH));
    var request =
        new CreateInspectionApplicationRequest(
            assessment.getId(),
            null,
            assessment.getAddress(),
            assessment.getDetailAddress(),
            new BigDecimal("37.4874850"),
            new BigDecimal("126.7985672"),
            "010-1234-5678",
            OccupancyType.TENANT,
            FloodExperience.NO,
            false,
            null,
            null,
            null,
            null);
    var application =
        applications.saveAndFlush(
            InspectionApplication.received(resident, assessment, request, "IA-MEMO-TEST"));
    applicationId = application.getId();
    histories.saveAndFlush(ApplicationStatusHistory.received(application));
  }

  private String path() {
    return "/api/admin/applications/" + applicationId + "/internal-memo";
  }

  private String body(String memo) {
    return mapper.writeValueAsString(Map.of("internalMemo", memo));
  }

  private String savedMemo() {
    return jdbc.queryForObject(
        "SELECT internal_memo FROM inspection_applications WHERE application_id = ?",
        String.class,
        applicationId);
  }

  // 신청 정보 변경 없이 메모 저장
  @ParameterizedTest
  @EnumSource(
      value = InspectionApplicationStatus.class,
      names = {"RECEIVED", "INSPECTION_COMPLETED"})
  void savesAndReplacesMemoWithoutChangingApplication(InspectionApplicationStatus initialStatus)
      throws Exception {
    jdbc.update(
        "UPDATE inspection_applications SET status = ?, internal_memo = '기존 메모' WHERE application_id = ?",
        initialStatus.name(),
        applicationId);
    for (String memo : List.of("  방문 전 연락해주세요.  ", "담당자에게 확인했습니다.")) {
      mvc.perform(
              patch(path())
                  .header("Authorization", "Bearer " + operatorToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body(memo)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("APPLICATION_200_1"))
          .andExpect(jsonPath("$.result.applicationId").value(applicationId))
          .andExpect(jsonPath("$.result.internalMemo").value(memo.strip()));
      assertThat(savedMemo()).isEqualTo(memo.strip());
    }
    var application = applications.findById(applicationId).orElseThrow();
    assertThat(application.getStatus()).isEqualTo(initialStatus);
    assertThat(application.getReceiptNo()).isEqualTo("IA-MEMO-TEST");
    assertThat(application.getContact()).isEqualTo("010-1234-5678");
    assertThat(histories.count()).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "\n\t "})
  void emptyOrWhitespaceMemoClearsExistingValue(String memo) throws Exception {
    jdbc.update(
        "UPDATE inspection_applications SET internal_memo = '기존 메모' WHERE application_id = ?",
        applicationId);
    mvc.perform(
            patch(path())
                .header("Authorization", "Bearer " + operatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(memo)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.internalMemo").hasJsonPath())
        .andExpect(jsonPath("$.result.internalMemo").value(org.hamcrest.Matchers.nullValue()));
    assertThat(savedMemo()).isNull();
  }

  @Test
  void rejectsMissingNullMalformedAndOverlongMemo() throws Exception {
    jdbc.update(
        "UPDATE inspection_applications SET internal_memo = '유지할 메모' WHERE application_id = ?",
        applicationId);
    for (String invalid :
        List.of(
            "{}",
            "{\"internalMemo\":null}",
            "{",
            "[]",
            "{\"internalMemo\":{}}",
            body("가".repeat(2001)))) {
      mvc.perform(
              patch(path())
                  .header("Authorization", "Bearer " + operatorToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(invalid))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("COMMON_400_2"));
    }
    mvc.perform(
            patch(path())
                .header("Authorization", "Bearer " + operatorToken)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
    assertThat(savedMemo()).isEqualTo("유지할 메모");
    mvc.perform(
            patch(path())
                .header("Authorization", "Bearer " + operatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("가".repeat(2000))))
        .andExpect(status().isOk());
    assertThat(savedMemo()).hasSize(2000);
  }

  @Test
  void requiresAdminAuthentication() throws Exception {
    mvc.perform(patch(path()).contentType(MediaType.APPLICATION_JSON).content(body("메모")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("COMMON_401_1"));
    mvc.perform(
            patch(path())
                .header("Authorization", "Bearer invalid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("메모")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("COMMON_401_3"));
    String residentToken = jwt.issue(resident).accessToken();
    mvc.perform(
            patch(path())
                .header("Authorization", "Bearer " + residentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("메모")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("COMMON_403_1"));
    assertThat(savedMemo()).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"서울특별시 동작구 흑석로 84", "부천시민로 1", "서울특별시 부천시 길주로 210", "확인되지 않은 주소"})
  void rejectsOutsideAndAmbiguousAddresses(String address) throws Exception {
    jdbc.update(
        "UPDATE inspection_applications SET address = ? WHERE application_id = ?",
        address,
        applicationId);
    mvc.perform(
            patch(path())
                .header("Authorization", "Bearer " + operatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("메모")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("APPLICATION_403_3"));
    assertThat(savedMemo()).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"부천시 원미구 길주로 210", "경기 부천시 원미구 길주로 210"})
  void acceptsBucheonAddressVariants(String address) throws Exception {
    jdbc.update(
        "UPDATE inspection_applications SET address = ? WHERE application_id = ?",
        address,
        applicationId);
    mvc.perform(
            patch(path())
                .header("Authorization", "Bearer " + operatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("메모")))
        .andExpect(status().isOk());
  }

  @Test
  void rejectsMissingOrDifferentOperatorJurisdiction() throws Exception {
    jdbc.update("UPDATE users SET jurisdiction_id = NULL WHERE user_id = ?", operatorId);
    mvc.perform(
            patch(path())
                .header("Authorization", "Bearer " + operatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("메모")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("APPLICATION_403_3"));
    jdbc.update(
        "INSERT INTO jurisdictions (name) SELECT '서울특별시' WHERE NOT EXISTS (SELECT 1 FROM jurisdictions WHERE name = '서울특별시')");
    jdbc.update(
        "UPDATE users SET jurisdiction_id = (SELECT jurisdiction_id FROM jurisdictions WHERE name = '서울특별시') WHERE user_id = ?",
        operatorId);
    mvc.perform(
            patch(path())
                .header("Authorization", "Bearer " + operatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("메모")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("APPLICATION_403_3"));
    assertThat(savedMemo()).isNull();
  }

  @Test
  void rejectsInvalidAndUnknownApplicationIds() throws Exception {
    for (String id : List.of("0", "-1", "invalid", "999999999999999999999999")) {
      mvc.perform(
              patch("/api/admin/applications/" + id + "/internal-memo")
                  .header("Authorization", "Bearer " + operatorToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body("메모")))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("COMMON_400_3"));
    }
    mvc.perform(
            patch("/api/admin/applications/" + Long.MAX_VALUE + "/internal-memo")
                .header("Authorization", "Bearer " + operatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("메모")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("APPLICATION_404_2"));
  }

  // 주민 응답의 내부 메모 제외
  @Test
  void residentResponseDoesNotExposeMemo() {
    jdbc.update(
        "UPDATE inspection_applications SET internal_memo = '주민 비공개 메모' WHERE application_id = ?",
        applicationId);
    var response =
        CreateInspectionApplicationResponse.from(
            applications.findById(applicationId).orElseThrow(), 1L);
    assertThat(mapper.readTree(mapper.writeValueAsString(response)).has("internalMemo")).isFalse();
  }

  @Test
  void rejectsUnsupportedContentType() throws Exception {
    mvc.perform(
            patch(path())
                .header("Authorization", "Bearer " + operatorToken)
                .contentType(MediaType.TEXT_PLAIN)
                .content("메모"))
        .andExpect(status().isUnsupportedMediaType());
  }

  @Test
  void swaggerGroupsMemoApiAndDocumentsResponses() throws Exception {
    var result =
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(
                jsonPath(
                        "$.paths['/api/admin/applications/{applicationId}/internal-memo'].patch.tags[0]")
                    .value("운영자-GIS 신청관리"))
            .andExpect(
                jsonPath(
                        "$.paths['/api/admin/applications/{applicationId}/internal-memo'].patch.responses['200'].content['application/json'].examples.memoSaved.value.code")
                    .value("APPLICATION_200_1"))
            .andExpect(
                jsonPath(
                        "$.paths['/api/admin/applications/{applicationId}/internal-memo'].patch.responses['403']")
                    .exists())
            .andExpect(
                jsonPath(
                        "$.paths['/api/admin/applications/{applicationId}/internal-memo'].patch.responses['404'].description")
                    .value("현장점검 신청을 찾을 수 없습니다."))
            .andReturn()
            .getResponse()
            .getContentAsString();
    var tags = mapper.readTree(result).path("tags");
    assertThat(tags.path(0).path("name").asText()).isEqualTo("인증/인가");
    assertThat(tags.path(1).path("name").asText()).isEqualTo("운영자-GIS 신청관리");
  }
}
