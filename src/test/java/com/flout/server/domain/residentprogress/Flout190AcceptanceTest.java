package com.flout.server.domain.residentprogress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.construction.service.ConstructionStatusService;
import com.flout.server.domain.inspection.service.InspectionStatusService;
import com.flout.server.domain.inspectionapplication.service.AttachmentStorage;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:flout190-acceptance;MODE=MySQL;DB_CLOSE_DELAY=0",
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "app.inspection.auto-start.enabled=false",
      "app.construction.auto-start.enabled=false"
    })
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Flout190AcceptanceTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired PasswordEncoder passwords;
  @Autowired ObjectMapper mapper;
  @Autowired InspectionStatusService inspectionStatus;
  @Autowired ConstructionStatusService constructionStatus;
  @MockitoBean AttachmentStorage storage;

  @MockitoBean(name = "inspectionClock")
  Clock clock;

  long userId;
  String residentToken, adminToken, email;
  long programId;
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  @BeforeEach
  void setup() {
    String suffix = UUID.randomUUID().toString();
    email = "acceptance-" + suffix + "@example.test";
    var user = users.saveAndFlush(User.resident(email, passwords.encode("password123"), "주민"));
    userId = user.getId();
    residentToken = jwt.issue(user).accessToken();
    String adminEmail = "admin-" + suffix + "@example.test";
    jdbc.update(
        "INSERT INTO users(email,password_hash,name,role,jurisdiction_id) VALUES(?,'hash','운영자','ADMIN',(SELECT jurisdiction_id FROM jurisdictions WHERE name='부천시'))",
        adminEmail);
    adminToken = jwt.issue(users.findByEmail(adminEmail).orElseThrow()).accessToken();
    jdbc.update("INSERT INTO support_programs(name,created_at) VALUES('지원사업',CURRENT_TIMESTAMP)");
    programId = jdbc.queryForObject("SELECT MAX(program_id) FROM support_programs", Long.class);
    when(storage.readUrl(anyString())).thenReturn("https://storage.example.test/test");
    when(clock.getZone()).thenReturn(SEOUL);
    setClock("2030-01-01T09:00:00");
  }

  void setClock(String date) {
    when(clock.instant()).thenReturn(LocalDateTime.parse(date).atZone(SEOUL).toInstant());
  }

  long risk() {
    jdbc.update(
        "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status,result_json) VALUES(?,'부천시 원미구 길주로 210','101호','HIGH','COMPLETED','{}')",
        userId);
    return jdbc.queryForObject(
        "SELECT MAX(assessment_id) FROM risk_assessments WHERE user_id=?", Long.class, userId);
  }

  long application(long assessment, String occupancy, boolean published) {
    jdbc.update(
        "INSERT INTO inspection_applications(applicant_name,applicant_user_id,assessment_id,receipt_no,address,detail_address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,status,submitted_at) VALUES('주민',?,?,?,'부천시 원미구 길주로 210','101호',37.5,126.7,'010-1234-5678',?,'NO',FALSE,'RECEIVED',CURRENT_TIMESTAMP)",
        userId,
        assessment,
        "ACC-" + assessment,
        occupancy);
    long id =
        jdbc.queryForObject(
            "SELECT application_id FROM inspection_applications WHERE assessment_id=?",
            Long.class,
            assessment);
    jdbc.update(
        "INSERT INTO inspections(application_id,status) VALUES(?,?)",
        id,
        published ? "COMPLETED" : "RECEIVED");
    long inspection =
        jdbc.queryForObject(
            "SELECT inspection_id FROM inspections WHERE application_id=?", Long.class, id);
    if (published)
      jdbc.update(
          "INSERT INTO inspection_results(inspection_id,result_status,summary,published_at) VALUES(?,'PUBLISHED','결과',CURRENT_TIMESTAMP)",
          inspection);
    return id;
  }

  String json(Object value) {
    return mapper.writeValueAsString(value);
  }

  JsonNode result(org.springframework.test.web.servlet.ResultActions action) throws Exception {
    return mapper.readTree(action.andReturn().getResponse().getContentAsString()).path("result");
  }

  String login() throws Exception {
    return result(
            mvc.perform(
                    post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "password123"))))
                .andExpect(status().isOk()))
        .path("accessToken")
        .asText();
  }

  void progress(String stage, String action) throws Exception {
    mvc.perform(get("/api/resident/progress").header("Authorization", "Bearer " + residentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.items.length()").value(1))
        .andExpect(jsonPath("$.result.items[0].currentStage").value(stage))
        .andExpect(jsonPath("$.result.items[0].nextAction.type").value(action));
  }

  MockMultipartFile photo(String name) throws Exception {
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
    return new MockMultipartFile(name, "photo.png", "image/png", bytes.toByteArray());
  }

  String supportBody(long app) {
    return json(
        Map.of(
            "applicationId",
            app,
            "programId",
            programId,
            "requestedFacility",
            "물막이판",
            "preferredVisitDate",
            LocalDate.now().plusDays(3).toString(),
            "preferredVisitTime",
            "ANY"));
  }

  @Test
  void bothDraftsSurviveNewLoginAndOtherResidentsCannotReadOrWrite() throws Exception {
    long risk = risk();
    long app = application(risk(), "OWNER", true);
    String inspectionId = UUID.randomUUID().toString(), supportId = UUID.randomUUID().toString();
    String firstSession = login();
    String inspection =
        json(
            Map.of(
                "assessmentId",
                risk,
                "fields",
                Map.of("applicantName", "김주민", "note", List.of("부분 저장"))));
    String support =
        json(
            Map.of(
                "applicationId",
                app,
                "fields",
                Map.of("programId", programId, "requestedFacility", "물막이판")));
    JsonNode savedInspection =
        result(
            mvc.perform(
                    put("/api/resident/application-form-drafts/{id}", inspectionId)
                        .header("Authorization", "Bearer " + firstSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inspection))
                .andExpect(status().isOk()));
    JsonNode savedSupport =
        result(
            mvc.perform(
                    put("/api/resident/support-form-drafts/{id}", supportId)
                        .header("Authorization", "Bearer " + firstSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(support))
                .andExpect(status().isOk()));
    for (String session : List.of(firstSession, login())) {
      for (var pair :
          List.of(
              Map.entry("/api/resident/applications/drafts", savedInspection),
              Map.entry("/api/resident/support-applications/drafts", savedSupport))) {
        var draft =
            result(
                    mvc.perform(get(pair.getKey()).header("Authorization", "Bearer " + session))
                        .andExpect(status().isOk()))
                .path("drafts")
                .get(0);
        for (String key : List.of("draftId", "fields", "version"))
          assertThat(draft.path(key)).as(key).isEqualTo(pair.getValue().path(key));
      }
    }
    var other =
        users.saveAndFlush(
            User.resident(
                "other-" + UUID.randomUUID() + "@example.test",
                passwords.encode("password123"),
                "다른 주민"));
    String otherToken = jwt.issue(other).accessToken();
    for (String path :
        List.of("/api/resident/applications/drafts", "/api/resident/support-applications/drafts"))
      mvc.perform(get(path).header("Authorization", "Bearer " + otherToken))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result.drafts").isEmpty());
    for (var pair :
        List.of(
            Map.entry("/api/resident/application-form-drafts/" + inspectionId, inspection),
            Map.entry("/api/resident/support-form-drafts/" + supportId, support)))
      mvc.perform(
              put(pair.getKey())
                  .header("Authorization", "Bearer " + otherToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(pair.getValue()))
          .andExpect(status().isNotFound());
    jdbc.update(
        "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status) VALUES(?,'부천시 원미구','101호','HIGH','COMPLETED')",
        other.getId());
    long otherRisk =
        jdbc.queryForObject(
            "SELECT MAX(assessment_id) FROM risk_assessments WHERE user_id=?",
            Long.class,
            other.getId());
    jdbc.update(
        "INSERT INTO inspection_applications(applicant_name,applicant_user_id,assessment_id,receipt_no,address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,status) VALUES('다른 주민',?,?,?,'부천시 원미구',37.5,126.7,'010-1234-5678','OWNER','NO',FALSE,'RECEIVED')",
        other.getId(),
        otherRisk,
        "OTHER-" + otherRisk);
    long otherApp =
        jdbc.queryForObject(
            "SELECT application_id FROM inspection_applications WHERE assessment_id=?",
            Long.class,
            otherRisk);
    for (var pair :
        List.of(
            Map.entry(
                "/api/resident/application-form-drafts/" + inspectionId,
                json(
                    Map.of(
                        "assessmentId",
                        otherRisk,
                        "version",
                        1,
                        "fields",
                        Map.of("changed", true)))),
            Map.entry(
                "/api/resident/support-form-drafts/" + supportId,
                json(
                    Map.of(
                        "applicationId",
                        otherApp,
                        "version",
                        1,
                        "fields",
                        Map.of("changed", true)))))) {
      mvc.perform(
              put(pair.getKey())
                  .header("Authorization", "Bearer " + otherToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(pair.getValue()))
          .andExpect(status().isNotFound());
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM resident_form_drafts WHERE draft_id IN (?,?) AND user_id=? AND version=1",
                Integer.class,
                inspectionId,
                supportId,
                userId))
        .isEqualTo(2);
  }

  @ParameterizedTest
  @ValueSource(strings = {"OWNER", "ETC"})
  void ownerAndOtherSubmitWithoutConsentAndRetryDoesNotDuplicate(String occupancy)
      throws Exception {
    long app = application(risk(), occupancy, true);
    String id = UUID.randomUUID().toString();
    mvc.perform(
            put("/api/resident/support-form-drafts/{id}", id)
                .header("Authorization", "Bearer " + residentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(Map.of("applicationId", app, "fields", Map.of("programId", programId)))))
        .andExpect(status().isOk());
    String body = supportBody(app);
    long supportId = 0;
    for (int i = 0; i < 2; i++) {
      long returned =
          result(
                  mvc.perform(
                          post("/api/resident/support-applications")
                              .header("Authorization", "Bearer " + residentToken)
                              .header("X-Form-Draft-Id", id)
                              .contentType(MediaType.APPLICATION_JSON)
                              .content(body))
                      .andExpect(status().isCreated())
                      .andExpect(jsonPath("$.result.status").value("SUBMITTED")))
              .path("supportApplicationId")
              .asLong();
      if (i == 0) supportId = returned;
      else assertThat(returned).isEqualTo(supportId);
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT form_state FROM resident_form_drafts WHERE draft_id=?", String.class, id))
        .isEqualTo("COMPLETED");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM support_applications WHERE application_id=?",
                Integer.class,
                app))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM support_attachments WHERE support_application_id=?",
                Integer.class,
                supportId))
        .isZero();
    progress("SUPPORT_REVIEW", "VIEW_SUPPORT_PROGRESS");
  }

  @Test
  void adminTransitionsAreReflectedAndTimeAloneDoesNotChangeReadButSchedulerDoes()
      throws Exception {
    long app = application(risk(), "OWNER", false);
    long inspection =
        jdbc.queryForObject(
            "SELECT inspection_id FROM inspections WHERE application_id=?", Long.class, app);
    progress("INSPECTION_RECEIVED", "WAIT_FOR_VISIT_SCHEDULE");
    mvc.perform(
            post("/api/admin/inspections/{id}/assignment", inspection)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "visitDate",
                            "2030-01-02",
                            "visitTime",
                            "10:00",
                            "preVisitNotice",
                            "방문 안내"))))
        .andExpect(status().isOk());
    progress("VISIT_SCHEDULED", "VIEW_VISIT_SCHEDULE");
    setClock("2030-01-03T10:00:00");
    progress("VISIT_SCHEDULED", "VIEW_VISIT_SCHEDULE");
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM inspections WHERE inspection_id=?", String.class, inspection))
        .isEqualTo("SCHEDULED");
    assertThat(inspectionStatus.startIfDue(inspection)).isTrue();
    progress("INSPECTION_IN_PROGRESS", "VIEW_INSPECTION_PROGRESS");
    mvc.perform(
            multipart("/api/admin/inspections/{id}/attachments", inspection)
                .file(photo("files"))
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());
    var publication =
        Map.of(
            "summary",
            "현장 결과",
            "vulnerabilities",
            List.of(Map.of("type", "ENTRANCE", "title", "출입구", "expertOpinion", "점검 의견")),
            "recommendedActions",
            List.of(Map.of("description", "설치")),
            "followUpType",
            "INDIVIDUAL_HOUSE");
    mvc.perform(
            post("/api/admin/inspections/{id}/result", inspection)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(publication)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.inspectionStatus").value("COMPLETED"));
    progress("INSPECTION_RESULT_PUBLISHED", "CHECK_SUPPORT_OPTIONS");
    long support =
        result(
                mvc.perform(
                        post("/api/resident/support-applications")
                            .header("Authorization", "Bearer " + residentToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(supportBody(app)))
                    .andExpect(status().isCreated()))
            .path("supportApplicationId")
            .asLong();
    progress("SUPPORT_REVIEW", "VIEW_SUPPORT_PROGRESS");
    var approval =
        Map.of(
            "supportScope",
            "물막이판 설치",
            "applicantHousingReviewed",
            true,
            "expertInspectionReviewed",
            true,
            "eligibilityDocumentsReviewed",
            true);
    long construction =
        result(
                mvc.perform(
                        post("/api/admin/support-applications/{id}/approve", support)
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(approval)))
                    .andExpect(status().isOk()))
            .path("constructionId")
            .asLong();
    progress("SUPPORT_APPROVED", "VIEW_CONSTRUCTION_SCHEDULE");
    mvc.perform(
            post("/api/admin/constructions/{id}/schedule", construction)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(Map.of("constructionDate", "2030-01-05", "constructionTime", "10:00"))))
        .andExpect(status().isOk());
    progress("CONSTRUCTION_SCHEDULED", "VIEW_CONSTRUCTION_SCHEDULE");
    setClock("2030-01-06T10:00:00");
    progress("CONSTRUCTION_SCHEDULED", "VIEW_CONSTRUCTION_SCHEDULE");
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM constructions WHERE construction_id=?",
                String.class,
                construction))
        .isEqualTo("SCHEDULED");
    assertThat(constructionStatus.startIfDue(construction)).isTrue();
    progress("CONSTRUCTION_IN_PROGRESS", "VIEW_PROGRESS");
    mvc.perform(
            multipart("/api/admin/constructions/{id}/photos", construction)
                .file(photo("files"))
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/admin/constructions")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "constructionId", construction, "finalInstallationResult", "설치 완료"))))
        .andExpect(status().isOk());
    progress("CONSTRUCTION_COMPLETED", "VIEW_COMPLETION");
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM constructions WHERE construction_id=?",
                String.class,
                construction))
        .isEqualTo("COMPLETED");
  }

  @Test
  void adminRejectionAppearsInResidentProgress() throws Exception {
    long app = application(risk(), "OWNER", true);
    long support =
        result(
                mvc.perform(
                        post("/api/resident/support-applications")
                            .header("Authorization", "Bearer " + residentToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(supportBody(app)))
                    .andExpect(status().isCreated()))
            .path("supportApplicationId")
            .asLong();
    progress("SUPPORT_REVIEW", "VIEW_SUPPORT_PROGRESS");
    mvc.perform(
            post("/api/admin/support-applications/{id}/reject", support)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("reason", "지원 조건 미충족"))))
        .andExpect(status().isOk());
    progress("SUPPORT_REJECTED", "VIEW_REJECTION");
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM support_applications WHERE support_application_id=?",
                String.class,
                support))
        .isEqualTo("REJECTED");
  }
}
