package com.flout.server.domain.inspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
      "spring.datasource.url=jdbc:h2:mem:inspection-result-query;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false"
    })
@AutoConfigureMockMvc
class InspectionResultQueryTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @MockitoBean AttachmentStorage storage;
  Long inspectionId;
  Long operatorId;
  User resident;
  String token;

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
    resident = users.saveAndFlush(User.resident("result-resident@example.com", "hash", "주민"));
    jdbc.update(
        "INSERT INTO users(email,password_hash,name,role,jurisdiction_id) VALUES('result-admin@example.com','hash','운영자','ADMIN',(SELECT jurisdiction_id FROM jurisdictions WHERE name='부천시'))");
    var operator = users.findByEmail("result-admin@example.com").orElseThrow();
    operatorId = operator.getId();
    token = jwt.issue(operator).accessToken();
    jdbc.update(
        "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status) VALUES(?,'부천시 원미구 길주로 53','101호','HIGH','COMPLETED')",
        resident.getId());
    var assessmentId =
        jdbc.queryForObject("SELECT MAX(assessment_id) FROM risk_assessments", Long.class);
    jdbc.update(
        "INSERT INTO inspection_applications(applicant_name,applicant_user_id,assessment_id,receipt_no,address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,status) VALUES('주민',?,?,'RESULT-QUERY','부천시 원미구 길주로 53',37.5,126.76,'010-0000-0000','OWNER','NO',FALSE,'RECEIVED')",
        resident.getId(),
        assessmentId);
    var applicationId =
        jdbc.queryForObject("SELECT MAX(application_id) FROM inspection_applications", Long.class);
    jdbc.update(
        "INSERT INTO inspections(application_id,status) VALUES(?,'IN_PROGRESS')", applicationId);
    inspectionId = jdbc.queryForObject("SELECT MAX(inspection_id) FROM inspections", Long.class);
  }

  String path() {
    return "/api/admin/inspections/" + inspectionId + "/result";
  }

  Long addResult(String status, String summary) {
    jdbc.update(
        "INSERT INTO inspection_results(inspection_id,result_status,summary,internal_memo,published_at) VALUES(?,?,?,'내부 메모',?)",
        inspectionId,
        status,
        summary,
        status.equals("PUBLISHED") ? java.time.LocalDateTime.of(2026, 11, 4, 10, 0) : null);
    return jdbc.queryForObject(
        "SELECT result_id FROM inspection_results WHERE inspection_id=? AND result_status=?",
        Long.class,
        inspectionId,
        status);
  }

  @Test
  void returnsDraftBeforePublishedWithOrderedContentAndOwnPhotosWithoutMutation() throws Exception {
    var publishedId = addResult("PUBLISHED", "공개 요약");
    var draftId = addResult("DRAFT", "초안 요약");
    jdbc.update(
        "INSERT INTO inspection_vulnerabilities(result_id,type,title,sort_order) VALUES(?,'DRAIN','두번째',1),(?,NULL,NULL,0)",
        draftId,
        draftId);
    jdbc.update(
        "INSERT INTO prevention_recommendations(result_id,description,sort_order) VALUES(?,'두번째 조치',1),(?,'첫번째 조치',0)",
        draftId,
        draftId);
    jdbc.update(
        "INSERT INTO inspection_result_photos(result_id,object_key,original_filename,file_size,content_type) VALUES(?,'draft-key','현장.png',100,'image/png'),(?,'published-key','공개.png',200,'image/png')",
        draftId,
        publishedId);
    when(storage.readUrl("draft-key"))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return "https://example.com/signed";
            });
    var before = jdbc.queryForList("SELECT * FROM inspection_results ORDER BY result_id");
    mvc.perform(get(path()).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.resultId").value(draftId))
        .andExpect(jsonPath("$.result.resultStatus").value("DRAFT"))
        .andExpect(jsonPath("$.result.summary").value("초안 요약"))
        .andExpect(jsonPath("$.result.internalMemo").value("내부 메모"))
        .andExpect(jsonPath("$.result.publishedAt").isEmpty())
        .andExpect(jsonPath("$.result.vulnerabilities[0].type").isEmpty())
        .andExpect(jsonPath("$.result.vulnerabilities[1].title").value("두번째"))
        .andExpect(jsonPath("$.result.recommendedActions[0].description").value("첫번째 조치"))
        .andExpect(jsonPath("$.result.photos.length()").value(1))
        .andExpect(jsonPath("$.result.photos[0].url").value("https://example.com/signed"))
        .andExpect(jsonPath("$.result.photos[0].objectKey").doesNotExist());
    verify(storage, never()).readUrl("published-key");
    assertThat(jdbc.queryForList("SELECT * FROM inspection_results ORDER BY result_id"))
        .isEqualTo(before);
  }

  @Test
  void fallsBackToPublishedResultAndReturnsEmptyLists() throws Exception {
    addResult("PUBLISHED", "공개 결과");
    mvc.perform(get(path()).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.resultStatus").value("PUBLISHED"))
        .andExpect(jsonPath("$.result.publishedAt").value("2026-11-04T10:00:00+09:00"))
        .andExpect(jsonPath("$.result.vulnerabilities").isEmpty())
        .andExpect(jsonPath("$.result.recommendedActions").isEmpty())
        .andExpect(jsonPath("$.result.photos").isEmpty());
    verifyNoInteractions(storage);
  }

  @Test
  void noResultReturnsNullWithoutCreatingDraft() throws Exception {
    mvc.perform(get(path()).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result").isEmpty());
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inspection_results", Integer.class))
        .isZero();
    verifyNoInteractions(storage);
  }

  @Test
  void validatesIdAndMissingInspection() throws Exception {
    for (String id : List.of("0", "-1", "abc")) {
      mvc.perform(
              get("/api/admin/inspections/" + id + "/result")
                  .header("Authorization", "Bearer " + token))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("COMMON_400_3"));
    }
    mvc.perform(
            get("/api/admin/inspections/9223372036854775807/result")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INSPECTION_404_1"));
  }

  @Test
  void rejectsUnauthenticatedResidentAndMissingJurisdiction() throws Exception {
    mvc.perform(get(path())).andExpect(status().isUnauthorized());
    mvc.perform(get(path()).header("Authorization", "Bearer " + jwt.issue(resident).accessToken()))
        .andExpect(status().isForbidden());
    jdbc.update("UPDATE users SET jurisdiction_id=NULL WHERE user_id=?", operatorId);
    mvc.perform(get(path()).header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("INSPECTION_403_2"));
    verifyNoInteractions(storage);
  }

  @Test
  void rejectsOutsideJurisdictionBeforeReadingPhotos() throws Exception {
    jdbc.update("UPDATE inspection_applications SET address='서울특별시 중구 세종대로 1'");
    mvc.perform(get(path()).header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("INSPECTION_403_2"));
    verifyNoInteractions(storage);
  }

  @Test
  void urlFailureReturnsServerErrorWithoutChangingSavedResult() throws Exception {
    var resultId = addResult("DRAFT", "보존할 결과");
    jdbc.update(
        "INSERT INTO inspection_result_photos(result_id,object_key,original_filename,file_size,content_type) VALUES(?,'failed-key','현장.png',100,'image/png')",
        resultId);
    when(storage.readUrl("failed-key")).thenThrow(new IllegalStateException("storage unavailable"));
    mvc.perform(get(path()).header("Authorization", "Bearer " + token))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("COMMON_500_1"));
    assertThat(
            jdbc.queryForObject(
                "SELECT summary FROM inspection_results WHERE result_id=?", String.class, resultId))
        .isEqualTo("보존할 결과");
  }
}
