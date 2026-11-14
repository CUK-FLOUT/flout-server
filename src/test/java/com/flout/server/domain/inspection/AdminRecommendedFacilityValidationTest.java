package com.flout.server.domain.inspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "app.inspection.auto-start.enabled=false",
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:facility-validation;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
    })
@AutoConfigureMockMvc
@Transactional
class AdminRecommendedFacilityValidationTest {
  private static final String PATH = "/api/admin/inspections/recommended-facilities/validate";
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired ObjectMapper mapper;
  String adminToken;
  String residentToken;
  Long residentId;

  @BeforeEach
  void setup() {
    String suffix = UUID.randomUUID().toString();
    String email = "admin-" + suffix + "@example.com";
    jdbc.update(
        "INSERT INTO users(email,password_hash,name,role,created_at,updated_at) VALUES(?,'hash','운영자','ADMIN',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        email);
    adminToken = jwt.issue(users.findByEmail(email).orElseThrow()).accessToken();
    User resident =
        users.saveAndFlush(User.resident("resident-" + suffix + "@example.com", "hash", "주민"));
    residentId = resident.getId();
    residentToken = jwt.issue(resident).accessToken();
  }

  @Test
  void extractsExistingRulesAndPreservesOrderAndLocations() throws Exception {
    mvc.perform(
            post(PATH)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        Map.of(
                            "descriptions",
                            List.of(
                                "출입구에 물막이판 설치를 권장합니다.",
                                "배수구 주변을 정리해 주세요.",
                                "창문 차수판과 출입구 물막이판 설치를 권장합니다.")))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.status").value(200))
        .andExpect(jsonPath("$.code").value("COMMON_200"))
        .andExpect(jsonPath("$.message").value("호출에 성공했습니다."))
        .andExpect(jsonPath("$.timeStamp").isNotEmpty())
        .andExpect(jsonPath("$.result.hasFacilities").value(true))
        .andExpect(jsonPath("$.result.facilities.length()").value(2))
        .andExpect(jsonPath("$.result.facilities[0]").value("출입구 물막이판"))
        .andExpect(jsonPath("$.result.facilities[1]").value("창문 차수판"));
  }

  @Test
  void noFacilityNegationAndInspectionAreSuccessfulEmptyResults() throws Exception {
    for (String description :
        List.of("배수구 주변을 정리해 주세요.", "물막이판 설치는 필요하지 않습니다.", "기존 물막이판 상태를 점검해 주세요.")) {
      mvc.perform(
              post(PATH)
                  .header("Authorization", "Bearer " + adminToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(mapper.writeValueAsString(Map.of("descriptions", List.of(description)))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result.hasFacilities").value(false))
          .andExpect(jsonPath("$.result.facilities.length()").value(0));
    }
  }

  @Test
  void rejectsMissingEmptyNullBlankAndOversizedInputs() throws Exception {
    for (String body :
        List.of(
            "{}",
            "{\"descriptions\":null}",
            "{\"descriptions\":[]}",
            "{\"descriptions\":[null]}",
            "{\"descriptions\":[\"   \" ]}",
            mapper.writeValueAsString(Map.of("descriptions", List.of("가".repeat(4001)))),
            mapper.writeValueAsString(
                Map.of("descriptions", java.util.Collections.nCopies(21, "조치"))))) {
      mvc.perform(
              post(PATH)
                  .header("Authorization", "Bearer " + adminToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("COMMON_400_2"));
    }
  }

  @Test
  void requiresValidAdminAuthentication() throws Exception {
    String body = "{\"descriptions\":[\"물막이판 설치\"]}";
    mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("COMMON_401_1"));
    mvc.perform(
            post(PATH)
                .header("Authorization", "Bearer invalid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("COMMON_401_3"));
    mvc.perform(
            post(PATH)
                .header("Authorization", "Bearer " + residentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("COMMON_403_1"));
  }

  @Test
  void validationDoesNotWriteInspectionOrRecommendationData() throws Exception {
    jdbc.update(
        "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status) VALUES(?,'부천시 원미구 길주로 53','101호','HIGH','COMPLETED')",
        residentId);
    Long assessmentId =
        jdbc.queryForObject(
            "SELECT MAX(assessment_id) FROM risk_assessments WHERE user_id=?",
            Long.class,
            residentId);
    jdbc.update(
        "INSERT INTO inspection_applications(applicant_name,applicant_user_id,assessment_id,receipt_no,address,detail_address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,status) VALUES('김주민',?,?,?,'부천시 원미구 길주로 53','101호',37.5,126.76,'010-0000-0000','OWNER','NO',FALSE,'RECEIVED')",
        residentId,
        assessmentId,
        "TEST-" + assessmentId);
    Long applicationId =
        jdbc.queryForObject(
            "SELECT application_id FROM inspection_applications WHERE assessment_id=?",
            Long.class,
            assessmentId);
    jdbc.update(
        "INSERT INTO inspections(application_id,status) VALUES(?,'IN_PROGRESS')", applicationId);
    Long inspectionId =
        jdbc.queryForObject(
            "SELECT inspection_id FROM inspections WHERE application_id=?",
            Long.class,
            applicationId);
    jdbc.update(
        "INSERT INTO inspection_results(inspection_id,result_status,summary,created_at,updated_at) VALUES(?,'DRAFT','기존 초안',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        inspectionId);
    Long resultId =
        jdbc.queryForObject(
            "SELECT result_id FROM inspection_results WHERE inspection_id=?",
            Long.class,
            inspectionId);
    jdbc.update(
        "INSERT INTO prevention_recommendations(result_id,description,sort_order) VALUES(?,'기존 조치',0)",
        resultId);
    List<String> tables =
        List.of(
            "inspections",
            "inspection_results",
            "prevention_recommendations",
            "inspection_applications");
    var before = tables.stream().map(this::count).toList();
    var resultBefore =
        jdbc.queryForMap(
            "SELECT result_status,summary FROM inspection_results WHERE result_id=?", resultId);
    var inspectionBefore =
        jdbc.queryForObject(
            "SELECT status FROM inspections WHERE inspection_id=?", String.class, inspectionId);
    mvc.perform(
            post(PATH)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"descriptions\":[\"물막이판 설치\"]}"))
        .andExpect(status().isOk());
    assertThat(tables.stream().map(this::count).toList()).isEqualTo(before);
    assertThat(
            jdbc.queryForMap(
                "SELECT result_status,summary FROM inspection_results WHERE result_id=?", resultId))
        .isEqualTo(resultBefore);
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM inspections WHERE inspection_id=?", String.class, inspectionId))
        .isEqualTo(inspectionBefore);
    assertThat(
            jdbc.queryForObject(
                "SELECT description FROM prevention_recommendations WHERE result_id=?",
                String.class,
                resultId))
        .isEqualTo("기존 조치");
  }

  @Test
  void openApiDocumentsSchemaAndExamples() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                    "$.paths['"
                        + PATH
                        + "'].post.responses['200'].content['application/json'].schema['$ref']")
                .exists())
        .andExpect(
            jsonPath(
                    "$.paths['"
                        + PATH
                        + "'].post.responses['200'].content['application/json'].examples['시설 추출'].value.result.facilities[0]")
                .value("출입구 물막이판"))
        .andExpect(jsonPath("$.paths['" + PATH + "'].post.responses['400']").exists())
        .andExpect(jsonPath("$.paths['" + PATH + "'].post.responses['401']").exists())
        .andExpect(jsonPath("$.paths['" + PATH + "'].post.responses['403']").exists());
  }

  private Integer count(String table) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
  }
}
