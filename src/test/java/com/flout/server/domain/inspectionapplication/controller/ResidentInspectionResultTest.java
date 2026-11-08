package com.flout.server.domain.inspectionapplication.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.inspectionapplication.service.AttachmentStorage;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:resident-inspection-result;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false"
    })
@AutoConfigureMockMvc
class ResidentInspectionResultTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @MockitoBean AttachmentStorage storage;
  Long applicationId;
  Long inspectionId;
  String ownerToken;
  String otherToken;

  @BeforeEach
  void setup() {
    for (String table :
        List.of(
            "inspection_result_photos",
            "inspection_vulnerabilities",
            "prevention_recommendations",
            "inspection_results",
            "inspections",
            "inspection_applications",
            "risk_assessments",
            "users")) jdbc.update("DELETE FROM " + table);
    var owner = users.saveAndFlush(User.resident("owner-result@example.com", "hash", "주민"));
    var other = users.saveAndFlush(User.resident("other-result@example.com", "hash", "타인"));
    ownerToken = jwt.issue(owner).accessToken();
    otherToken = jwt.issue(other).accessToken();
    jdbc.update(
        "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status) VALUES(?,'부천시 원미구 길주로 53','101호','HIGH','COMPLETED')",
        owner.getId());
    Long assessmentId =
        jdbc.queryForObject("SELECT MAX(assessment_id) FROM risk_assessments", Long.class);
    jdbc.update(
        "INSERT INTO inspection_applications(applicant_name,applicant_user_id,assessment_id,receipt_no,address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,status) VALUES('주민',?,?,'RESIDENT-RESULT','부천시 원미구 길주로 53',37.5,126.76,'010-0000-0000','OWNER','NO',FALSE,'RECEIVED')",
        owner.getId(),
        assessmentId);
    applicationId =
        jdbc.queryForObject("SELECT MAX(application_id) FROM inspection_applications", Long.class);
    jdbc.update(
        "INSERT INTO inspections(application_id,status) VALUES(?,'COMPLETED')", applicationId);
    inspectionId = jdbc.queryForObject("SELECT MAX(inspection_id) FROM inspections", Long.class);
  }

  private Long addResult(String state, String summary) {
    jdbc.update(
        "INSERT INTO inspection_results(inspection_id,result_status,summary,internal_memo,published_at) VALUES(?,?,?,'비공개 메모',?)",
        inspectionId,
        state,
        summary,
        state.equals("PUBLISHED") ? java.time.LocalDateTime.of(2026, 11, 4, 10, 0) : null);
    return jdbc.queryForObject(
        "SELECT result_id FROM inspection_results WHERE inspection_id=? AND result_status=?",
        Long.class,
        inspectionId,
        state);
  }

  @Test
  void returnsPublishedContentAndSignedPhotoWithoutDraftOrInternalData() throws Exception {
    Long published = addResult("PUBLISHED", "공개 요약");
    Long draft = addResult("DRAFT", "비공개 요약");
    jdbc.update(
        "INSERT INTO inspection_vulnerabilities(result_id,type,title,expert_opinion,field_findings,sort_order) VALUES(?,'DRAIN','배수구','두번째 의견',NULL,1),(?,'ENTRANCE','출입구','첫번째 의견','현장 확인',0)",
        published,
        published);
    jdbc.update(
        "INSERT INTO inspection_result_photos(result_id,object_key,original_filename,file_size,content_type) VALUES(?,'public-key','현장.png',100,'image/png'),(?,'draft-key','초안.png',100,'image/png')",
        published,
        draft);
    when(storage.readUrl("public-key"))
        .thenAnswer(
            inv -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return "https://example.com/signed";
            });
    mvc.perform(
            get("/api/resident/applications/{id}/result", applicationId)
                .header("Authorization", "Bearer " + ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("APPLICATION_200_14"))
        .andExpect(jsonPath("$.result.applicationId").value(applicationId))
        .andExpect(jsonPath("$.result.summary").value("공개 요약"))
        .andExpect(jsonPath("$.result.publishedAt").value("2026-11-04T10:00:00+09:00"))
        .andExpect(jsonPath("$.result.vulnerabilities[0].type").value("ENTRANCE"))
        .andExpect(jsonPath("$.result.vulnerabilities[0].title").value("출입구"))
        .andExpect(jsonPath("$.result.vulnerabilities[0].expertOpinion").value("첫번째 의견"))
        .andExpect(jsonPath("$.result.vulnerabilities[0].fieldFindings").value("현장 확인"))
        .andExpect(jsonPath("$.result.vulnerabilities[1].fieldFindings").isEmpty())
        .andExpect(jsonPath("$.result.photos.length()").value(1))
        .andExpect(jsonPath("$.result.photos[0].photoId").isNumber())
        .andExpect(jsonPath("$.result.photos[0].url").value("https://example.com/signed"))
        .andExpect(jsonPath("$.result.photos[0].objectKey").doesNotExist())
        .andExpect(jsonPath("$.result.internalMemo").doesNotExist());
    verify(storage, never()).readUrl("draft-key");
  }

  @Test
  void emptyVulnerabilitiesAndPhotosAreArrays() throws Exception {
    addResult("PUBLISHED", "요약");
    mvc.perform(
            get("/api/resident/applications/{id}/result", applicationId)
                .header("Authorization", "Bearer " + ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.vulnerabilities.length()").value(0))
        .andExpect(jsonPath("$.result.photos.length()").value(0));
    verifyNoInteractions(storage);
  }

  @Test
  void enforcesAuthenticationOwnershipExistenceAndPublication() throws Exception {
    String path = "/api/resident/applications/{id}/result";
    mvc.perform(get(path, applicationId)).andExpect(status().isUnauthorized());
    mvc.perform(get(path, applicationId).header("Authorization", "Bearer " + otherToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("APPLICATION_403_6"));
    mvc.perform(get(path, Long.MAX_VALUE).header("Authorization", "Bearer " + ownerToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("APPLICATION_404_2"));
    mvc.perform(get(path, applicationId).header("Authorization", "Bearer " + ownerToken))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("APPLICATION_400_5"));
    addResult("DRAFT", "초안");
    mvc.perform(get(path, applicationId).header("Authorization", "Bearer " + ownerToken))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("APPLICATION_400_5"));
    verifyNoInteractions(storage);
  }

  @Test
  void documentsResultAndErrorsInOpenApi() throws Exception {
    String path = "$.paths['/api/resident/applications/{applicationId}/result'].get.responses";
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                    path
                        + "['200'].content['application/json'].examples.residentInspectionResult.value.result.photos[0].url")
                .isNotEmpty())
        .andExpect(
            jsonPath(
                    path
                        + "['400'].content['application/json'].examples.resultNotPublished.value.code")
                .value("APPLICATION_400_5"))
        .andExpect(
            jsonPath(
                    path
                        + "['403'].content['application/json'].examples.applicationAccessDenied.value.code")
                .value("APPLICATION_403_6"))
        .andExpect(
            jsonPath(
                    path
                        + "['404'].content['application/json'].examples.applicationNotFound.value.code")
                .value("APPLICATION_404_2"));
  }
}
