package com.flout.server.domain.inspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
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
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:inspection-result-draft;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false"
    })
@AutoConfigureMockMvc
@Transactional
class InspectionResultDraftTest {
  @Autowired MockMvc mvc;
  @Autowired jakarta.persistence.EntityManager entityManager;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper mapper;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  Long inspectionId;
  Long applicationId;
  Long operatorId;
  String token;
  User resident;

  @BeforeEach
  void setup() {
    String suffix = UUID.randomUUID().toString();
    resident =
        users.saveAndFlush(User.resident("resident-" + suffix + "@example.com", "hash", "주민"));
    String email = "admin-" + suffix + "@example.com";
    jdbc.update(
        "INSERT INTO users(email,password_hash,name,role,jurisdiction_id) VALUES(?,'hash','운영자','ADMIN',(SELECT jurisdiction_id FROM jurisdictions WHERE name='부천시'))",
        email);
    var operator = users.findByEmail(email).orElseThrow();
    operatorId = operator.getId();
    token = jwt.issue(operator).accessToken();
    jdbc.update(
        "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status) VALUES(?,'부천시 원미구 길주로 53','101호','HIGH','COMPLETED')",
        resident.getId());
    Long assessmentId =
        jdbc.queryForObject(
            "SELECT MAX(assessment_id) FROM risk_assessments WHERE user_id=?",
            Long.class,
            resident.getId());
    jdbc.update(
        "INSERT INTO inspection_applications(applicant_name,applicant_user_id,assessment_id,receipt_no,address,detail_address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,status) VALUES('김주민',?, ?, ?, '부천시 원미구 길주로 53','101호',37.5,126.76,'010-0000-0000','OWNER','NO',FALSE,'RECEIVED')",
        resident.getId(),
        assessmentId,
        "DRAFT-" + assessmentId);
    applicationId =
        jdbc.queryForObject(
            "SELECT application_id FROM inspection_applications WHERE assessment_id=?",
            Long.class,
            assessmentId);
    jdbc.update(
        "INSERT INTO inspections(application_id,status) VALUES(?,'IN_PROGRESS')", applicationId);
    inspectionId =
        jdbc.queryForObject(
            "SELECT inspection_id FROM inspections WHERE application_id=?",
            Long.class,
            applicationId);
  }

  String path() {
    return "/api/admin/inspections/" + inspectionId + "/result/draft";
  }

  org.springframework.test.web.servlet.ResultActions save(Object body) throws Exception {
    return mvc.perform(
        put(path())
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(body)));
  }

  Long resultId() {
    return jdbc.queryForObject(
        "SELECT result_id FROM inspection_results WHERE inspection_id=? AND result_status='DRAFT'",
        Long.class,
        inspectionId);
  }

  Map<String, Object> full() {
    return Map.of(
        "summary",
        "요약",
        "vulnerabilities",
        List.of(
            Map.of(
                "type", "ENTRANCE", "title", "출입구", "expertOpinion", "의견", "fieldFindings", "현장")),
        "recommendedActions",
        List.of(Map.of("description", "설명")),
        "followUpType",
        "INDIVIDUAL_HOUSE",
        "internalMemo",
        "비공개 메모");
  }

  int count(String table) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
  }

  void completeInspection() throws Exception {
    mvc.perform(
            patch("/api/admin/inspections/" + inspectionId + "/status")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"COMPLETED\"}"))
        .andExpect(status().isOk());
  }

  void addPhoto() throws Exception {
    if (jdbc.queryForObject(
            "SELECT COUNT(*) FROM inspection_results WHERE inspection_id=? AND result_status='DRAFT'",
            Integer.class,
            inspectionId)
        == 0) {
      save(Map.of()).andExpect(status().isOk());
    }
    jdbc.update(
        "INSERT INTO inspection_result_photos(result_id,object_key,original_filename,file_size,content_type) VALUES(?,?,'현장.png',100,'image/png')",
        resultId(),
        "required-photo-" + inspectionId);
  }

  @Test
  void rejectsPublishingWithoutFieldPhotoAndPreservesDraftAndStates() throws Exception {
    completeInspection();
    var state =
        jdbc.queryForMap(
            "SELECT status,updated_at FROM inspections WHERE inspection_id=?", inspectionId);
    for (boolean draftExists : List.of(false, true)) {
      if (draftExists) save(full()).andExpect(status().isOk());
      mvc.perform(
              post("/api/admin/inspections/" + inspectionId + "/result")
                  .header("Authorization", "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(mapper.writeValueAsString(full())))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INSPECTION_400_5"));
      assertThat(count("inspection_results")).isEqualTo(draftExists ? 1 : 0);
      assertThat(
              jdbc.queryForMap(
                  "SELECT status,updated_at FROM inspections WHERE inspection_id=?", inspectionId))
          .isEqualTo(state);
      assertThat(count("application_status_histories")).isZero();
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT result_status FROM inspection_results WHERE inspection_id=?",
                String.class,
                inspectionId))
        .isEqualTo("DRAFT");
  }

  @Test
  void publishRequiresCompletedInspectionAndDoesNotDuplicateCompletionHistory() throws Exception {
    for (String state : List.of("RECEIVED", "SCHEDULED", "IN_PROGRESS")) {
      jdbc.update("UPDATE inspections SET status=? WHERE inspection_id=?", state, inspectionId);
      entityManager.clear();
      mvc.perform(
              post("/api/admin/inspections/" + inspectionId + "/result")
                  .header("Authorization", "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(mapper.writeValueAsString(full())))
          .andExpect(status().isConflict());
    }
    assertThat(count("inspection_results")).isZero();
    entityManager.clear();
    completeInspection();
    addPhoto();
    var historyCount = count("inspection_status_histories");
    var completed =
        jdbc.queryForMap(
            "SELECT status,updated_at FROM inspections WHERE inspection_id=?", inspectionId);
    mvc.perform(
            post("/api/admin/inspections/" + inspectionId + "/result")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(full())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.inspectionStatus").value("COMPLETED"))
        .andExpect(jsonPath("$.result.resultStatus").value("PUBLISHED"));
    assertThat(count("inspection_status_histories")).isEqualTo(historyCount);
    assertThat(
            jdbc.queryForMap(
                "SELECT status,updated_at FROM inspections WHERE inspection_id=?", inspectionId))
        .isEqualTo(completed);
    mvc.perform(
            post("/api/admin/inspections/" + inspectionId + "/result")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(full())))
        .andExpect(status().isConflict());
    assertThat(count("application_status_histories")).isEqualTo(1);
  }

  @Test
  void savesMultipleDescriptionOnlyActionsIndependentlyOfVulnerabilities() throws Exception {
    var body = new HashMap<String, Object>(full());
    body.put(
        "recommendedActions",
        List.of(
            Map.of("description", "물막이판 설치"),
            Map.of("description", "배수구 정비"),
            Map.of("description", "사전점검")));
    save(body).andExpect(status().isOk());
    assertThat(
            jdbc.queryForList(
                "SELECT description FROM prevention_recommendations WHERE result_id=? ORDER BY sort_order",
                String.class,
                resultId()))
        .containsExactly("물막이판 설치", "배수구 정비", "사전점검");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM inspection_vulnerabilities WHERE result_id=?",
                Integer.class,
                resultId()))
        .isEqualTo(1);
    body.put("recommendedActions", List.of(Map.of("description", "교체한 조치")));
    completeInspection();
    addPhoto();
    mvc.perform(
            post("/api/admin/inspections/" + inspectionId + "/result")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
        .andExpect(status().isOk());
    assertThat(
            jdbc.queryForList(
                "SELECT description FROM prevention_recommendations WHERE result_id=(SELECT result_id FROM inspection_results WHERE inspection_id=?) ORDER BY sort_order",
                String.class,
                inspectionId))
        .containsExactly("교체한 조치");
  }

  @Test
  void publishAcceptsMoreActionsThanVulnerabilitiesAndRejectsBlankDescriptions() throws Exception {
    var body = new HashMap<String, Object>(full());
    for (var action :
        List.of(Map.of(), Map.of("description", "   "), Map.of("description", "가".repeat(4001)))) {
      body.put("recommendedActions", List.of(action));
      mvc.perform(
              post("/api/admin/inspections/" + inspectionId + "/result")
                  .header("Authorization", "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(mapper.writeValueAsString(body)))
          .andExpect(status().isBadRequest());
    }
    completeInspection();
    addPhoto();
    body.put(
        "recommendedActions",
        List.of(Map.of("description", "첫 조치"), Map.of("description", "추가 조치")));
    mvc.perform(
            post("/api/admin/inspections/" + inspectionId + "/result")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
        .andExpect(status().isOk());
    assertThat(
            jdbc.queryForList(
                "SELECT description FROM prevention_recommendations ORDER BY sort_order",
                String.class))
        .containsExactly("첫 조치", "추가 조치");
  }

  @Test
  void createsEmptyDraftAndUpdatesSameRowWithPartialNestedContent() throws Exception {
    save(Map.of())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.inspectionId").value(inspectionId))
        .andExpect(jsonPath("$.result.resultStatus").value("DRAFT"))
        .andExpect(jsonPath("$.result.savedAt").isNotEmpty());
    Long first = resultId();
    var before =
        jdbc.queryForObject(
            "SELECT updated_at FROM inspection_results WHERE result_id=?",
            java.sql.Timestamp.class,
            first);
    save(Map.of(
            "vulnerabilities",
            List.of(Map.of("title", "제목만"), Map.of()),
            "recommendedActions",
            List.of(Map.of("description", "설명만"))))
        .andExpect(status().isOk());
    assertThat(resultId()).isEqualTo(first);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM inspection_results WHERE inspection_id=?",
                Integer.class,
                inspectionId))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT type FROM inspection_vulnerabilities WHERE result_id=? AND sort_order=0",
                String.class,
                first))
        .isNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM inspection_vulnerabilities WHERE result_id=?",
                Integer.class,
                first))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT updated_at FROM inspection_results WHERE result_id=?",
                java.sql.Timestamp.class,
                first))
        .isAfterOrEqualTo(before);
  }

  @Test
  void replacesListsClearsOmittedFieldsAndPreservesStatesAndHistories() throws Exception {
    save(full()).andExpect(status().isOk());
    Long id = resultId();
    save(Map.of("summary", "새 요약", "vulnerabilities", List.of(Map.of("type", "DRAIN"))))
        .andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "SELECT type FROM inspection_vulnerabilities WHERE result_id=?", String.class, id))
        .isEqualTo("DRAIN");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM prevention_recommendations WHERE result_id=?",
                Integer.class,
                id))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT internal_memo FROM inspection_results WHERE result_id=?", String.class, id))
        .isNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT follow_up_type FROM inspection_results WHERE result_id=?",
                String.class,
                id))
        .isNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM inspections WHERE inspection_id=?", String.class, inspectionId))
        .isEqualTo("IN_PROGRESS");
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM inspection_applications WHERE application_id=?",
                String.class,
                applicationId))
        .isEqualTo("RECEIVED");
    assertThat(count("inspection_status_histories")).isZero();
    assertThat(count("application_status_histories")).isZero();
    save(Map.of()).andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM inspection_vulnerabilities WHERE result_id=?",
                Integer.class,
                id))
        .isZero();
  }

  @Test
  void preservesPublishedRowAndItsChildrenWhenSavingSeparateDraft() throws Exception {
    jdbc.update(
        "INSERT INTO inspection_results(inspection_id,result_status,summary,internal_memo,published_at) VALUES(?,'PUBLISHED','공개 요약','공개본 메모',CURRENT_TIMESTAMP)",
        inspectionId);
    Long publishedId =
        jdbc.queryForObject(
            "SELECT result_id FROM inspection_results WHERE inspection_id=?",
            Long.class,
            inspectionId);
    jdbc.update(
        "INSERT INTO inspection_vulnerabilities(result_id,title) VALUES(?,'공개 취약지점')", publishedId);
    jdbc.update(
        "INSERT INTO prevention_recommendations(result_id,title) VALUES(?,'공개 예방조치')", publishedId);
    var before =
        jdbc.queryForMap("SELECT * FROM inspection_results WHERE result_id=?", publishedId);
    save(full()).andExpect(status().isOk());
    save(Map.of("internalMemo", "초안 변경")).andExpect(status().isOk());
    assertThat(jdbc.queryForMap("SELECT * FROM inspection_results WHERE result_id=?", publishedId))
        .isEqualTo(before);
    assertThat(
            jdbc.queryForObject(
                "SELECT title FROM inspection_vulnerabilities WHERE result_id=?",
                String.class,
                publishedId))
        .isEqualTo("공개 취약지점");
    assertThat(
            jdbc.queryForObject(
                "SELECT title FROM prevention_recommendations WHERE result_id=?",
                String.class,
                publishedId))
        .isEqualTo("공개 예방조치");
    assertThat(resultId()).isNotEqualTo(publishedId);
  }

  @ParameterizedTest
  @ValueSource(strings = {"RECEIVED", "SCHEDULED", "IN_PROGRESS", "COMPLETED"})
  void permitsEachInspectionStatusWithoutChangingIt(String state) throws Exception {
    jdbc.update("UPDATE inspections SET status=? WHERE inspection_id=?", state, inspectionId);
    save(Map.of("summary", "작성 중")).andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM inspections WHERE inspection_id=?", String.class, inspectionId))
        .isEqualTo(state);
  }

  static Stream<Object> invalidBodies() {
    List<Object> cases = new ArrayList<>();
    cases.add(Map.of("followUpType", "INVALID"));
    cases.add(Map.of("vulnerabilities", List.of(Map.of("type", "INVALID"))));
    cases.add(Map.of("vulnerabilities", Collections.singletonList(null)));
    cases.add(Map.of("recommendedActions", Collections.singletonList(null)));
    cases.add(Map.of("vulnerabilities", Collections.nCopies(21, Map.of())));
    cases.add(Map.of("recommendedActions", Collections.nCopies(21, Map.of())));
    for (String field : List.of("summary", "internalMemo"))
      cases.add(Map.of(field, "가".repeat(4001)));
    for (String field : List.of("expertOpinion", "fieldFindings"))
      cases.add(Map.of("vulnerabilities", List.of(Map.of(field, "가".repeat(4001)))));
    cases.add(Map.of("recommendedActions", List.of(Map.of("description", "가".repeat(4001)))));
    cases.add(Map.of("vulnerabilities", List.of(Map.of("title", "x".repeat(256)))));
    return cases.stream();
  }

  @ParameterizedTest
  @MethodSource("invalidBodies")
  void rejectsInvalidInputWithoutCreatingResult(Object body) throws Exception {
    save(body).andExpect(status().isBadRequest());
    assertThat(count("inspection_results")).isZero();
  }

  @Test
  void acceptsAllMaximumLengthsAndListCounts() throws Exception {
    var vulnerability =
        Map.of(
            "title",
            "가".repeat(255),
            "expertOpinion",
            "가".repeat(4000),
            "fieldFindings",
            "가".repeat(4000));
    var action = Map.of("description", "가".repeat(4000));
    save(Map.of(
            "summary",
            "가".repeat(4000),
            "internalMemo",
            "가".repeat(4000),
            "vulnerabilities",
            Collections.nCopies(20, vulnerability),
            "recommendedActions",
            Collections.nCopies(20, action)))
        .andExpect(status().isOk());
    assertThat(count("inspection_vulnerabilities")).isEqualTo(20);
    assertThat(count("prevention_recommendations")).isEqualTo(20);
  }

  @Test
  void rejectsMissingInvalidResidentTokensAndNonPositiveOrMissingInspections() throws Exception {
    mvc.perform(put(path()).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            put(path())
                .header("Authorization", "Bearer invalid")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnauthorized());
    String residentToken = jwt.issue(resident).accessToken();
    mvc.perform(
            put(path())
                .header("Authorization", "Bearer " + residentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
    for (long id : List.of(0L, -1L, Long.MAX_VALUE)) {
      var action =
          mvc.perform(
              put("/api/admin/inspections/" + id + "/result/draft")
                  .header("Authorization", "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{}"));
      if (id <= 0) action.andExpect(status().isBadRequest());
      else action.andExpect(status().isNotFound());
    }
  }

  @Test
  void rejectsMissingJurisdictionAndOutsideAddress() throws Exception {
    jdbc.update(
        "UPDATE inspection_applications SET address='서울특별시 중구' WHERE application_id=?",
        applicationId);
    save(Map.of()).andExpect(status().isForbidden());
    jdbc.update(
        "UPDATE inspection_applications SET address='부천시 원미구' WHERE application_id=?",
        applicationId);
    String email = "no-jurisdiction-" + UUID.randomUUID() + "@example.com";
    jdbc.update(
        "INSERT INTO users(email,password_hash,name,role) VALUES(?,'hash','무관할','ADMIN')", email);
    token = jwt.issue(users.findByEmail(email).orElseThrow()).accessToken();
    save(Map.of()).andExpect(status().isForbidden());
  }

  @Test
  void swaggerExamplesUseScreenDataForBothDraftAndPublish() throws Exception {
    var response =
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var paths = mapper.readTree(response).get("paths");
    for (String suffix : List.of("/result/draft", "/result")) {
      var operation =
          paths
              .get("/api/admin/inspections/{inspectionId}" + suffix)
              .get(suffix.endsWith("draft") ? "put" : "post");
      var example =
          operation
              .get("requestBody")
              .get("content")
              .get("application/json")
              .get("examples")
              .get("화면 입력 예시")
              .get("value");
      assertThat(example.get("summary").asText()).startsWith("출입구가 낮고 배수구 주변에");
      assertThat(example.get("vulnerabilities").get(0).get("title").asText())
          .isEqualTo("낮은 출입구 높이");
      var action = example.get("recommendedActions").get(0);
      assertThat(action.get("description").asText()).isEqualTo("출입구 물막이판 · 관련 취약지점: 출입구");
      assertThat(action.has("title")).isFalse();
      assertThat(action.has("relatedTarget")).isFalse();
      assertThat(example.get("followUpType").asText()).isEqualTo("INDIVIDUAL_HOUSE");
    }
  }

  @Test
  void documentsResponseErrorsAndOptionalFields() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/inspections/{inspectionId}/result/draft'].put.responses['200']")
                .exists())
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/inspections/{inspectionId}/result/draft'].put.responses['400']")
                .exists())
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/inspections/{inspectionId}/result/draft'].put.responses['401']")
                .exists())
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/inspections/{inspectionId}/result/draft'].put.responses['403']")
                .exists())
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/inspections/{inspectionId}/result/draft'].put.responses['404']")
                .exists());
  }
}
