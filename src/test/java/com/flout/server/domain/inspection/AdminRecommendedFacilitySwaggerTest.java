package com.flout.server.domain.inspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flout.server.domain.inspectionapplication.service.RecommendedFacilityExtractor;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "app.inspection.auto-start.enabled=false")
@AutoConfigureMockMvc
@Transactional
class AdminRecommendedFacilitySwaggerTest {
  private static final String PATH = "/api/admin/inspections/recommended-facilities/validate";
  private static final String BODY = "{\"descriptions\":[\"출입구에 물막이판 설치를 권장합니다.\"]}";
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired ObjectMapper mapper;
  @MockitoSpyBean RecommendedFacilityExtractor extractor;
  String adminToken;
  String residentToken;

  @BeforeEach
  void setup() {
    String suffix = UUID.randomUUID().toString();
    String email = "swagger-admin-" + suffix + "@example.com";
    jdbc.update(
        "INSERT INTO users(email,password_hash,name,role,created_at,updated_at) VALUES(?,'hash','운영자','ADMIN',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        email);
    adminToken = jwt.issue(users.findByEmail(email).orElseThrow()).accessToken();
    User resident =
        users.saveAndFlush(
            User.resident("swagger-resident-" + suffix + "@example.com", "hash", "주민"));
    residentToken = jwt.issue(resident).accessToken();
  }

  @Test
  void errorExamplesMatchActualResponses() throws Exception {
    JsonNode responses = documentation().path("paths").path(PATH).path("post").path("responses");
    assertExampleMatches(
        responses,
        "400",
        "잘못된 JSON",
        mvc.perform(
                post(PATH)
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{"))
            .andExpect(status().isBadRequest())
            .andReturn());
    assertExampleMatches(
        responses,
        "401",
        "authenticationRequired",
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isUnauthorized())
            .andReturn());
    assertExampleMatches(
        responses,
        "401",
        "invalidOrExpiredToken",
        mvc.perform(
                post(PATH)
                    .header("Authorization", "Bearer invalid-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(BODY))
            .andExpect(status().isUnauthorized())
            .andReturn());
    assertExampleMatches(
        responses,
        "403",
        "accessDenied",
        mvc.perform(
                post(PATH)
                    .header("Authorization", "Bearer " + residentToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(BODY))
            .andExpect(status().isForbidden())
            .andReturn());
    assertExampleMatches(
        responses,
        "406",
        "지원하지 않는 응답 형식",
        mvc.perform(
                post(PATH)
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_PLAIN)
                    .content(BODY))
            .andExpect(status().isNotAcceptable())
            .andReturn());
    assertExampleMatches(
        responses,
        "415",
        "지원하지 않는 요청 형식",
        mvc.perform(
                post(PATH)
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.TEXT_PLAIN)
                    .content(BODY))
            .andExpect(status().isUnsupportedMediaType())
            .andReturn());
    doThrow(new IllegalStateException("test failure")).when(extractor).extract(anyList());
    assertExampleMatches(
        responses,
        "500",
        "serverError",
        mvc.perform(
                post(PATH)
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(BODY))
            .andExpect(status().isInternalServerError())
            .andReturn());
  }

  @Test
  void successAndValidationDocumentationMatchesContract() throws Exception {
    JsonNode api = documentation();
    JsonNode operation = api.path("paths").path(PATH).path("post");
    assertThat(operation.path("summary").asText()).isEqualTo("권장 예방조치 시설 추출 사전 검증");
    assertThat(operation.path("description").asText())
        .contains("관리자(ADMIN)", "기존 규칙", "빈 배열", "저장하거나 주민에게 공개하지 않으며", "신청 자격을 판단하지 않습니다");
    JsonNode success =
        operation.path("responses").path("200").path("content").path("application/json");
    assertThat(success.path("schema").path("$ref").asText())
        .contains("ValidateRecommendedFacilitiesResponse");
    assertThat(
            success
                .path("examples")
                .path("시설 추출")
                .path("value")
                .path("result")
                .path("facilities")
                .get(0)
                .asText())
        .isEqualTo("출입구 물막이판");
    assertThat(
            success
                .path("examples")
                .path("시설 없음")
                .path("value")
                .path("result")
                .path("hasFacilities")
                .asBoolean())
        .isFalse();
    assertThat(
            success
                .path("examples")
                .path("시설 없음")
                .path("value")
                .path("result")
                .path("facilities")
                .isEmpty())
        .isTrue();
    JsonNode request =
        api.path("components").path("schemas").path("ValidateRecommendedFacilitiesRequest");
    assertThat(request.path("required").toString()).contains("descriptions");
    assertThat(request.path("properties").path("descriptions").path("maxItems").asInt())
        .isEqualTo(20);
    assertThat(request.path("properties").path("descriptions").path("description").asText())
        .contains("1~20개", "공백을 포함해 최대 4000자");
    MvcResult invalid =
        mvc.perform(
                post(PATH)
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"descriptions\":[]}"))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertThat(mapper.readTree(invalid.getResponse().getContentAsString()).path("code").asText())
        .isEqualTo("COMMON_400_2");
    assertThat(operation.path("responses").path("400").path("description").asText())
        .contains("유효성 검사 메시지");
  }

  private JsonNode documentation() throws Exception {
    return mapper.readTree(
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private void assertExampleMatches(
      JsonNode responses, String status, String name, MvcResult result) throws Exception {
    JsonNode example =
        responses
            .path(status)
            .path("content")
            .path("application/json")
            .path("examples")
            .path(name)
            .path("value");
    JsonNode actual = mapper.readTree(result.getResponse().getContentAsString());
    assertThat(example.isMissingNode()).as("%s %s", status, name).isFalse();
    for (String field : new String[] {"isSuccess", "status", "code", "message", "result"}) {
      assertThat(example.path(field))
          .as("%s %s %s", status, name, field)
          .isEqualTo(actual.path(field));
    }
    assertThat(example.path("timeStamp").isTextual()).isTrue();
    assertThat(actual.path("timeStamp").isTextual()).isTrue();
  }
}
