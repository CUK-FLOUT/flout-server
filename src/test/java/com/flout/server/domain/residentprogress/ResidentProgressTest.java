package com.flout.server.domain.residentprogress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.inspectionapplication.service.AttachmentStorage;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:resident-progress-190;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "app.inspection.auto-start.enabled=false"
    })
@AutoConfigureMockMvc
class ResidentProgressTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired ObjectMapper mapper;
  @MockitoBean AttachmentStorage storage;

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  FormDraftService draftService;

  long userId, assessmentId, applicationId, programId;
  String token;

  private String requestHash(Object request) throws Exception {
    byte[] json =
        mapper.writeValueAsString(request).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    return java.util.HexFormat.of()
        .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json));
  }

  private void insertLegacyDraft(String id, String type, long source, Map<String, Object> fields) {
    jdbc.update(
        "INSERT INTO resident_form_drafts(draft_id,user_id,draft_type,source_id,form_state,fields_json,version,created_at,updated_at) VALUES(?,?,?,?,'EDITING',?,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        id,
        userId,
        type,
        source,
        mapper.writeValueAsString(fields));
  }

  @BeforeEach
  void setup() {
    for (String table :
        new String[] {
          "attachment_upload_intents",
          "application_attachments",
          "support_attachments",
          "notifications",
          "resident_form_drafts",
          "support_applications",
          "support_programs",
          "inspection_results",
          "inspection_status_histories",
          "inspections",
          "application_inflow_locations",
          "application_status_histories",
          "inspection_applications",
          "risk_assessments",
          "users"
        }) jdbc.update("DELETE FROM " + table);
    var user = users.saveAndFlush(User.resident("progress-test@example.test", "hash", "주민"));
    userId = user.getId();
    token = jwt.issue(user).accessToken();
    jdbc.update(
        "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status,result_json) VALUES(?,'부천시 원미구','101호','HIGH','COMPLETED','{}')",
        userId);
    assessmentId =
        jdbc.queryForObject("SELECT MAX(assessment_id) FROM risk_assessments", Long.class);
    jdbc.update(
        "INSERT INTO inspection_applications(applicant_name,applicant_user_id,assessment_id,receipt_no,address,detail_address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,status) VALUES('주민',?,?,'PROGRESS-TEST','부천시 원미구','101호',37.5,126.7,'010-1234-5678','OWNER','NO',FALSE,'RECEIVED')",
        userId,
        assessmentId);
    applicationId =
        jdbc.queryForObject("SELECT MAX(application_id) FROM inspection_applications", Long.class);
    jdbc.update(
        "INSERT INTO inspections(application_id,status) VALUES(?,'COMPLETED')", applicationId);
    long inspection = jdbc.queryForObject("SELECT MAX(inspection_id) FROM inspections", Long.class);
    jdbc.update(
        "INSERT INTO inspection_results(inspection_id,result_status,summary,published_at) VALUES(?,'PUBLISHED','결과',CURRENT_TIMESTAMP)",
        inspection);
    jdbc.update("INSERT INTO support_programs(name,created_at) VALUES('지원',CURRENT_TIMESTAMP)");
    programId = jdbc.queryForObject("SELECT MAX(program_id) FROM support_programs", Long.class);
  }

  @Test
  void savesResumesAndSubmitsSupportIdempotentlyWithoutChangingLegacyConflict() throws Exception {
    String id = UUID.randomUUID().toString();
    String partial =
        mapper.writeValueAsString(
            Map.of("applicationId", applicationId, "fields", Map.of("programId", programId)));
    var save =
        put("/api/resident/support-form-drafts/{id}", id)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(partial);
    mvc.perform(save).andExpect(status().isOk()).andExpect(jsonPath("$.result.version").value(1));
    mvc.perform(save).andExpect(status().isOk()).andExpect(jsonPath("$.result.version").value(1));
    mvc.perform(
            get("/api/resident/support-applications/drafts")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.drafts[0].formState").value("EDITING"));
    mvc.perform(get("/api/resident/progress").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.items[0].currentStage").value("SUPPORT_FORM_EDITING"))
        .andExpect(jsonPath("$.result.items[0].applicationType").value("SUPPORT"))
        .andExpect(
            jsonPath("$.result.items[0].receiptNumber").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(
            jsonPath("$.result.items[0].stageHistory[*].stage")
                .value(org.hamcrest.Matchers.hasItem("RISK_ASSESSMENT_COMPLETED")));
    String submitted =
        mapper.writeValueAsString(
            Map.of(
                "applicationId",
                applicationId,
                "programId",
                programId,
                "requestedFacility",
                "물막이판",
                "preferredVisitDate",
                LocalDate.now().plusDays(2).toString(),
                "preferredVisitTime",
                "ANY"));
    var post =
        post("/api/resident/support-applications")
            .header("Authorization", "Bearer " + token)
            .header("X-Form-Draft-Id", id)
            .contentType(MediaType.APPLICATION_JSON)
            .content(submitted);
    var first =
        mvc.perform(post)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var retry =
        mvc.perform(post)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(mapper.readTree(first).path("result"))
        .isEqualTo(mapper.readTree(retry).path("result"));
    mvc.perform(
            post("/api/resident/support-applications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(submitted))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SUPPORT_409_1"));
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM support_applications", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT form_state FROM resident_form_drafts WHERE draft_id=?", String.class, id))
        .isEqualTo("COMPLETED");
    mvc.perform(get("/api/resident/progress").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.items[0].currentStage").value("SUPPORT_REVIEW"))
        .andExpect(jsonPath("$.result.items[0].applicationType").value("SUPPORT"))
        .andExpect(jsonPath("$.result.items[0].title").value("지원 · 물막이판"))
        .andExpect(
            jsonPath("$.result.items[0].receiptNumber")
                .value(String.format(java.util.Locale.ROOT, "%06d", applicationId)));
    mvc.perform(get("/api/resident/applications").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result[0].title").value("지원 · 물막이판"))
        .andExpect(
            jsonPath("$.result[0].receiptNumber")
                .value(String.format(java.util.Locale.ROOT, "%06d", applicationId)));
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"INSPECTION", "SUPPORT"})
  void preventsNewEditingDuplicatesButRetainsLegacyUpdates(String type) throws Exception {
    boolean inspection = type.equals("INSPECTION");
    long source = applicationId;
    if (inspection) {
      jdbc.update(
          "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status,result_json) VALUES(?,'다른 주소','101호','LOW','COMPLETED','{}')",
          userId);
      source = jdbc.queryForObject("SELECT MAX(assessment_id) FROM risk_assessments", Long.class);
    }
    String path =
        inspection
            ? "/api/resident/application-form-drafts/{id}"
            : "/api/resident/support-form-drafts/{id}";
    String list =
        inspection
            ? "/api/resident/applications/drafts"
            : "/api/resident/support-applications/drafts";
    String sourceKey = inspection ? "assessmentId" : "applicationId";
    String id = UUID.randomUUID().toString();
    String legacyId = UUID.randomUUID().toString();
    String form =
        mapper.writeValueAsString(Map.of(sourceKey, source, "fields", Map.of("name", "first")));
    var initial =
        put(path, id)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(form);
    mvc.perform(initial)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.version").value(1));
    mvc.perform(initial)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.version").value(1));
    mvc.perform(
            put(path, UUID.randomUUID())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(form))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("FORM_DRAFT_409_6"));
    String updated =
        mapper.writeValueAsString(
            Map.of(sourceKey, source, "version", 1, "fields", Map.of("name", "updated")));
    var update =
        put(path, id)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(updated);
    mvc.perform(update).andExpect(status().isOk()).andExpect(jsonPath("$.result.version").value(2));
    mvc.perform(update)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("FORM_DRAFT_409_1"));
    insertLegacyDraft(legacyId, type, source, Map.of("name", "legacy"));
    String legacyUpdate =
        mapper.writeValueAsString(
            Map.of(sourceKey, source, "version", 1, "fields", Map.of("name", "legacy updated")));
    mvc.perform(
            put(path, legacyId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(legacyUpdate))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.version").value(2));
    mvc.perform(
            put(path, UUID.randomUUID())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(form))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("FORM_DRAFT_409_6"));
    mvc.perform(get(list).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.drafts.length()").value(1));
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM resident_form_drafts WHERE user_id=? AND draft_type=? AND source_id=? AND form_state='EDITING'",
                Integer.class,
                userId,
                type,
                source))
        .isEqualTo(2);
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"INSPECTION", "SUPPORT"})
  void concurrentNewFormSavesCreateOneEditingDraft(String type) throws Exception {
    boolean inspection = type.equals("INSPECTION");
    long source = applicationId;
    if (inspection) {
      jdbc.update(
          "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status,result_json) VALUES(?,'다른 주소','101호','LOW','COMPLETED','{}')",
          userId);
      source = jdbc.queryForObject("SELECT MAX(assessment_id) FROM risk_assessments", Long.class);
    }
    String path =
        inspection
            ? "/api/resident/application-form-drafts/{id}"
            : "/api/resident/support-form-drafts/{id}";
    String form =
        mapper.writeValueAsString(
            Map.of(inspection ? "assessmentId" : "applicationId", source, "fields", Map.of()));
    var ready = new java.util.concurrent.CountDownLatch(2);
    var start = new java.util.concurrent.CountDownLatch(1);
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      java.util.concurrent.Callable<Integer> save =
          () -> {
            ready.countDown();
            if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS))
              throw new IllegalStateException("start timeout");
            return mvc.perform(
                    put(path, UUID.randomUUID())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(form))
                .andReturn()
                .getResponse()
                .getStatus();
          };
      var first = pool.submit(save);
      var second = pool.submit(save);
      assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(
              java.util.List.of(
                  first.get(30, java.util.concurrent.TimeUnit.SECONDS),
                  second.get(30, java.util.concurrent.TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM resident_form_drafts WHERE user_id=? AND draft_type=? AND source_id=? AND form_state='EDITING'",
                  Integer.class,
                  userId,
                  type,
                  source))
          .isEqualTo(1);
    } finally {
      start.countDown();
      pool.shutdownNow();
    }
  }

  @Test
  void supportFormRequiresPublishedResultOnlyForFirstSave() throws Exception {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO inspection_results(inspection_id,result_status,summary) SELECT inspection_id,'DRAFT','임시 결과' FROM inspections WHERE application_id=?",
        applicationId);
    String form =
        mapper.writeValueAsString(
            Map.of("applicationId", applicationId, "fields", Map.of("programId", programId)));
    var initial =
        put("/api/resident/support-form-drafts/{id}", id)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(form);
    mvc.perform(initial)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.version").value(1));
    jdbc.update("DELETE FROM inspection_results WHERE result_status='DRAFT'");
    jdbc.update("UPDATE inspection_results SET result_status='DRAFT',published_at=NULL");
    mvc.perform(
            put("/api/resident/support-form-drafts/{id}", UUID.randomUUID())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(form))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SUPPORT_400_2"));
    mvc.perform(initial)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.version").value(1));
    String update =
        mapper.writeValueAsString(
            Map.of(
                "applicationId",
                applicationId,
                "version",
                1,
                "fields",
                Map.of("requestedFacility", "보존")));
    mvc.perform(
            put("/api/resident/support-form-drafts/{id}", id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(update))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.version").value(2));
    mvc.perform(
            get("/api/resident/support-applications/drafts")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.drafts[0].fields.requestedFacility").value("보존"));
    String submitted =
        mapper.writeValueAsString(
            Map.of(
                "applicationId",
                applicationId,
                "programId",
                programId,
                "requestedFacility",
                "물막이판",
                "preferredVisitDate",
                LocalDate.now().plusDays(2).toString(),
                "preferredVisitTime",
                "ANY"));
    mvc.perform(
            post("/api/resident/support-applications")
                .header("Authorization", "Bearer " + token)
                .header("X-Form-Draft-Id", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(submitted))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SUPPORT_400_2"));
    jdbc.update("DELETE FROM inspection_results");
    mvc.perform(
            put("/api/resident/support-form-drafts/{id}", UUID.randomUUID())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(form))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SUPPORT_400_2"));
    jdbc.update("DELETE FROM inspections");
    mvc.perform(
            put("/api/resident/support-form-drafts/{id}", UUID.randomUUID())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(form))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SUPPORT_400_2"));
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM resident_form_drafts WHERE draft_id=? AND form_state='EDITING'",
                Integer.class,
                id))
        .isEqualTo(1);
  }

  @Test
  void existingSupportConflictTakesPriorityOverUnpublishedResult() throws Exception {
    String submitted =
        mapper.writeValueAsString(
            Map.of(
                "applicationId",
                applicationId,
                "programId",
                programId,
                "requestedFacility",
                "물막이판",
                "preferredVisitDate",
                LocalDate.now().plusDays(2).toString(),
                "preferredVisitTime",
                "ANY"));
    mvc.perform(
            post("/api/resident/support-applications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(submitted))
        .andExpect(status().isCreated());
    jdbc.update("DELETE FROM inspection_results");
    String form =
        mapper.writeValueAsString(Map.of("applicationId", applicationId, "fields", Map.of()));
    for (String state : java.util.List.of("DRAFT", "SUBMITTED")) {
      jdbc.update(
          "UPDATE support_applications SET status=? WHERE application_id=?", state, applicationId);
      mvc.perform(
              put("/api/resident/support-form-drafts/{id}", UUID.randomUUID())
                  .header("Authorization", "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(form))
          .andExpect(status().isConflict())
          .andExpect(
              jsonPath("$.code")
                  .value(state.equals("DRAFT") ? "FORM_DRAFT_409_4" : "FORM_DRAFT_409_5"));
    }
  }

  @Test
  void supportFormBlocksNewDraftForEveryExistingStatusAndPreservesEditingForms() throws Exception {
    String first = UUID.randomUUID().toString();
    String second = UUID.randomUUID().toString();
    String form =
        mapper.writeValueAsString(
            Map.of("applicationId", applicationId, "fields", Map.of("programId", programId)));
    mvc.perform(
            put("/api/resident/support-form-drafts/{id}", first)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(form))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.version").value(1));
    insertLegacyDraft(second, "SUPPORT", applicationId, Map.of("programId", programId));
    jdbc.update(
        "UPDATE resident_form_drafts SET updated_at=TIMESTAMP '2025-01-01 00:00:00' WHERE draft_id=?",
        first);
    mvc.perform(
            get("/api/resident/support-applications/drafts")
                .header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$.result.drafts.length()").value(1))
        .andExpect(jsonPath("$.result.drafts[0].draftId").value(second));
    String submitted =
        mapper.writeValueAsString(
            Map.of(
                "applicationId",
                applicationId,
                "programId",
                programId,
                "requestedFacility",
                "물막이판",
                "preferredVisitDate",
                LocalDate.now().plusDays(2).toString(),
                "preferredVisitTime",
                "ANY"));
    mvc.perform(
            post("/api/resident/support-applications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(submitted))
        .andExpect(status().isCreated());
    mvc.perform(
            get("/api/resident/support-applications/drafts")
                .header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$.result.drafts.length()").value(0));
    mvc.perform(
            put("/api/resident/support-form-drafts/{id}", first)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(form))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.version").value(1));
    String update =
        mapper.writeValueAsString(
            Map.of(
                "applicationId",
                applicationId,
                "version",
                1,
                "fields",
                Map.of("requestedFacility", "보존")));
    mvc.perform(
            put("/api/resident/support-form-drafts/{id}", first)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(update))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.version").value(2));
    for (String state : java.util.List.of("DRAFT", "SUBMITTED", "APPROVED", "REJECTED")) {
      jdbc.update(
          "UPDATE support_applications SET status=? WHERE application_id=?", state, applicationId);
      mvc.perform(
              put("/api/resident/support-form-drafts/{id}", UUID.randomUUID())
                  .header("Authorization", "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(form))
          .andExpect(status().isConflict())
          .andExpect(
              jsonPath("$.code")
                  .value(state.equals("DRAFT") ? "FORM_DRAFT_409_4" : "FORM_DRAFT_409_5"));
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM resident_form_drafts WHERE draft_type='SUPPORT' AND source_id=? AND form_state='EDITING' AND linked_id IS NULL",
                Integer.class,
                applicationId))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM support_applications WHERE application_id=?",
                Integer.class,
                applicationId))
        .isEqualTo(1);
  }

  @Test
  void supportSubmissionAndNewFormSaveAreSerialized() throws Exception {
    var locked = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    org.mockito.Mockito.doAnswer(
            invocation -> {
              Object value = invocation.callRealMethod();
              locked.countDown();
              if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS))
                throw new IllegalStateException("lock test timed out");
              return value;
            })
        .when(draftService)
        .lockSupportSource(org.mockito.ArgumentMatchers.anyLong());
    String submitted =
        mapper.writeValueAsString(
            Map.of(
                "applicationId",
                applicationId,
                "programId",
                programId,
                "requestedFacility",
                "물막이판",
                "preferredVisitDate",
                LocalDate.now().plusDays(2).toString(),
                "preferredVisitTime",
                "ANY"));
    String form =
        mapper.writeValueAsString(
            Map.of("applicationId", applicationId, "fields", Map.of("programId", programId)));
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      var submission =
          pool.submit(
              () ->
                  mvc.perform(
                          post("/api/resident/support-applications")
                              .header("Authorization", "Bearer " + token)
                              .contentType(MediaType.APPLICATION_JSON)
                              .content(submitted))
                      .andReturn()
                      .getResponse()
                      .getStatus());
      assertThat(locked.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      var saving =
          pool.submit(
              () ->
                  mvc.perform(
                          put("/api/resident/support-form-drafts/{id}", UUID.randomUUID())
                              .header("Authorization", "Bearer " + token)
                              .contentType(MediaType.APPLICATION_JSON)
                              .content(form))
                      .andReturn()
                      .getResponse()
                      .getStatus());
      org.junit.jupiter.api.Assertions.assertThrows(
          java.util.concurrent.TimeoutException.class,
          () -> saving.get(500, java.util.concurrent.TimeUnit.MILLISECONDS));
      release.countDown();
      assertThat(submission.get(20, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(201);
      assertThat(saving.get(20, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(409);
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM resident_form_drafts WHERE draft_type='SUPPORT' AND source_id=?",
                  Integer.class,
                  applicationId))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM support_applications WHERE application_id=?",
                  Integer.class,
                  applicationId))
          .isEqualTo(1);
    } finally {
      release.countDown();
      pool.shutdownNow();
    }
  }

  @Test
  void existingInspectionBlocksNewFormButPreservesOldForms() throws Exception {
    jdbc.update(
        "UPDATE inspection_applications SET status='DRAFT' WHERE application_id=?", applicationId);
    String oldId = UUID.randomUUID().toString();
    String oldFields = "{\"applicantName\":\"보존된 이름\"}";
    jdbc.update(
        "INSERT INTO resident_form_drafts(draft_id,user_id,draft_type,source_id,form_state,fields_json,version,created_at,updated_at) VALUES(?,?,'INSPECTION',?,'EDITING',?,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        oldId,
        userId,
        assessmentId,
        oldFields);
    String oldRequest =
        mapper.writeValueAsString(
            Map.of("assessmentId", assessmentId, "fields", Map.of("applicantName", "보존된 이름")));
    mvc.perform(
            put("/api/resident/application-form-drafts/{id}", oldId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(oldRequest))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.version").value(1));
    String newId = UUID.randomUUID().toString();
    mvc.perform(
            put("/api/resident/application-form-drafts/{id}", newId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(oldRequest))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("FORM_DRAFT_409_2"));
    mvc.perform(get("/api/resident/applications/drafts").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.drafts.length()").value(0));
    assertThat(
            jdbc.queryForObject(
                "SELECT fields_json FROM resident_form_drafts WHERE draft_id=?",
                String.class,
                oldId))
        .isEqualTo(oldFields);
    String updated =
        mapper.writeValueAsString(
            Map.of(
                "assessmentId",
                assessmentId,
                "version",
                1,
                "fields",
                Map.of("applicantName", "수정된 이름")));
    mvc.perform(
            put("/api/resident/application-form-drafts/{id}", oldId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updated))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.version").value(2));
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM resident_form_drafts WHERE draft_id=? AND linked_id IS NULL AND form_state='EDITING'",
                Integer.class,
                oldId))
        .isEqualTo(1);
    jdbc.update("DELETE FROM inspection_results");
    jdbc.update("DELETE FROM inspections");
    mvc.perform(get("/api/resident/progress").header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$.result.items[0].currentStage").value("INSPECTION_DRAFT"))
        .andExpect(jsonPath("$.result.items[0].nextAction.type").value("UPLOAD_INSPECTION_PHOTO"))
        .andExpect(jsonPath("$.result.items[0].relatedIds.applicationId").value(applicationId))
        .andExpect(
            jsonPath("$.result.items[0].relatedIds.inspectionFormDraftId")
                .value(org.hamcrest.Matchers.nullValue()));
    jdbc.update(
        "UPDATE inspection_applications SET status='RECEIVED' WHERE application_id=?",
        applicationId);
    mvc.perform(
            put("/api/resident/application-form-drafts/{id}", UUID.randomUUID())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(oldRequest))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("FORM_DRAFT_409_3"));
  }

  @Test
  void inspectionDraftListShowsLatestWithoutDeletingOthers() throws Exception {
    jdbc.update(
        "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status,result_json) VALUES(?,'다른 주소','101호','LOW','COMPLETED','{}')",
        userId);
    long other = jdbc.queryForObject("SELECT MAX(assessment_id) FROM risk_assessments", Long.class);
    String body =
        mapper.writeValueAsString(
            Map.of("assessmentId", other, "fields", Map.of("applicantName", "주민")));
    String first = UUID.randomUUID().toString();
    String second = UUID.randomUUID().toString();
    mvc.perform(
            put("/api/resident/application-form-drafts/{id}", first)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
    insertLegacyDraft(second, "INSPECTION", other, Map.of("applicantName", "주민"));
    jdbc.update(
        "UPDATE resident_form_drafts SET updated_at=TIMESTAMP '2025-01-01 00:00:00' WHERE draft_id=?",
        first);
    mvc.perform(get("/api/resident/applications/drafts").header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$.result.drafts.length()").value(1))
        .andExpect(jsonPath("$.result.drafts[0].draftId").value(second));
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM resident_form_drafts WHERE source_id=? AND draft_type='INSPECTION'",
                Integer.class,
                other))
        .isEqualTo(2);
  }

  @Test
  void progressUsesExistingReceiptAndNullNumberForAssessmentAndForm() throws Exception {
    jdbc.update(
        "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status,result_json) VALUES(?,'다른 주소','101호','LOW','COMPLETED','{}')",
        userId);
    long other = jdbc.queryForObject("SELECT MAX(assessment_id) FROM risk_assessments", Long.class);
    var result =
        mapper
            .readTree(
                mvc.perform(
                        get("/api/resident/progress").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .path("result");
    assertThat(result.has("primary")).isFalse();
    assertThat(result.has("history")).isFalse();
    for (var item : result.path("items")) {
      if (item.path("assessmentId").asLong() == assessmentId) {
        assertThat(item.path("applicationType").asText()).isEqualTo("INSPECTION");
        assertThat(item.path("receiptNumber").asText())
            .isEqualTo(String.format(java.util.Locale.ROOT, "%06d", applicationId));
        assertThat(item.path("relatedIds").path("applicationId").asLong()).isEqualTo(applicationId);
      } else {
        assertThat(item.path("assessmentId").asLong()).isEqualTo(other);
        assertThat(item.path("applicationType").asText()).isEqualTo("RISK_ASSESSMENT");
        assertThat(item.path("receiptNumber").isNull()).isTrue();
      }
    }
    String draftId = UUID.randomUUID().toString();
    mvc.perform(
            put("/api/resident/application-form-drafts/{id}", draftId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(Map.of("assessmentId", other, "fields", Map.of()))))
        .andExpect(status().isOk());
    mvc.perform(get("/api/resident/progress").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.items[0].assessmentId").value(other))
        .andExpect(jsonPath("$.result.items[0].applicationType").value("INSPECTION"))
        .andExpect(
            jsonPath("$.result.items[0].receiptNumber").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.result.items[0].relatedIds.inspectionFormDraftId").value(draftId));
  }

  @Test
  void progressCursorPaginatesAllItemsWithoutDuplicatesAndDefaultsToTwenty() throws Exception {
    for (int i = 0; i < 22; i++) {
      jdbc.update(
          "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status,result_json,created_at) VALUES(?,'주소','101호','LOW','COMPLETED','{}',TIMESTAMP '2025-01-01 00:00:00')",
          userId);
    }
    var first =
        mapper
            .readTree(
                mvc.perform(
                        get("/api/resident/progress").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .path("result");
    assertThat(first.path("items").size()).isEqualTo(20);
    var ids = new java.util.HashSet<Long>();
    String cursor = null;
    do {
      var request =
          get("/api/resident/progress")
              .header("Authorization", "Bearer " + token)
              .param("size", "5");
      if (cursor != null) request.param("cursor", cursor);
      var result =
          mapper
              .readTree(
                  mvc.perform(request)
                      .andExpect(status().isOk())
                      .andReturn()
                      .getResponse()
                      .getContentAsString())
              .path("result");
      for (var item : result.path("items"))
        assertThat(ids.add(item.path("assessmentId").asLong())).isTrue();
      cursor = result.path("nextCursor").isNull() ? null : result.path("nextCursor").asText();
    } while (cursor != null);
    assertThat(ids).hasSize(23);
    mvc.perform(
            get("/api/resident/progress")
                .header("Authorization", "Bearer " + token)
                .param("cursor", "invalid"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/resident/progress")
                .header("Authorization", "Bearer " + token)
                .param("size", "0"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void expiredSupportRetryRestoresOnlyTheSameCommittedRequest() throws Exception {
    String id = UUID.randomUUID().toString();
    String unsent = UUID.randomUUID().toString();
    String form =
        mapper.writeValueAsString(
            Map.of("applicationId", applicationId, "fields", Map.of("programId", programId)));
    mvc.perform(
            put("/api/resident/support-form-drafts/{id}", id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(form))
        .andExpect(status().isOk());
    insertLegacyDraft(unsent, "SUPPORT", applicationId, Map.of("programId", programId));
    var body = new java.util.LinkedHashMap<String, Object>();
    body.put("applicationId", applicationId);
    body.put("programId", programId);
    body.put("requestedFacility", "물막이판");
    body.put("preferredVisitDate", LocalDate.now().toString());
    body.put("preferredVisitTime", "ANY");
    String first =
        mvc.perform(
                post("/api/resident/support-applications")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Form-Draft-Id", id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    body.put("preferredVisitDate", LocalDate.now().minusDays(1).toString());
    String expired = mapper.writeValueAsString(body);
    var submitted =
        mapper.readValue(
            expired,
            com.flout.server.domain.supportapplication.dto.request.CreateSupportApplicationRequest
                .class);
    jdbc.update(
        "UPDATE resident_form_drafts SET submission_hash=? WHERE draft_id=?",
        requestHash(submitted),
        id);
    jdbc.update(
        "UPDATE support_applications SET preferred_visit_date=? WHERE application_id=?",
        LocalDate.now().minusDays(1),
        applicationId);
    var retry =
        mvc.perform(
                post("/api/resident/support-applications")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Form-Draft-Id", id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(expired))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(mapper.readTree(retry).path("result"))
        .isEqualTo(mapper.readTree(first).path("result"));
    mvc.perform(
            post("/api/resident/support-applications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(expired))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_400_2"));
    mvc.perform(
            post("/api/resident/support-applications")
                .header("Authorization", "Bearer " + token)
                .header("X-Form-Draft-Id", unsent)
                .contentType(MediaType.APPLICATION_JSON)
                .content(expired))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_400_2"));
    var other =
        users.saveAndFlush(User.resident("expired-retry-other@example.test", "hash", "다른 주민"));
    mvc.perform(
            post("/api/resident/support-applications")
                .header("Authorization", "Bearer " + jwt.issue(other).accessToken())
                .header("X-Form-Draft-Id", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(expired))
        .andExpect(status().isBadRequest());
    body.put("requestedFacility", "다른 시설");
    mvc.perform(
            post("/api/resident/support-applications")
                .header("Authorization", "Bearer " + token)
                .header("X-Form-Draft-Id", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
        .andExpect(status().isConflict());
    body.put("requestedFacility", " ");
    mvc.perform(
            post("/api/resident/support-applications")
                .header("Authorization", "Bearer " + token)
                .header("X-Form-Draft-Id", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM support_applications", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void progressDistinguishesDraftAndUnpublishedInspectionAndProtectsAssessment() throws Exception {
    jdbc.update("UPDATE inspection_results SET result_status='DRAFT',published_at=NULL");
    mvc.perform(get("/api/resident/progress").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.items[0].currentStage").value("INSPECTION_RESULT_PENDING"));
    mvc.perform(
            get("/api/resident/risk-assessments/{id}", assessmentId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.assessmentId").value(assessmentId));
    var other = users.saveAndFlush(User.resident("progress-other@example.test", "hash", "다른 주민"));
    mvc.perform(
            get("/api/resident/risk-assessments/{id}", assessmentId)
                .header("Authorization", "Bearer " + jwt.issue(other).accessToken()))
        .andExpect(status().isNotFound());
    jdbc.update(
        "INSERT INTO resident_form_drafts(draft_id,user_id,draft_type,source_id,form_state,fields_json,version,created_at,updated_at) VALUES(?,?,'SUPPORT',?,'EDITING',?,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        UUID.randomUUID().toString(),
        userId,
        applicationId,
        mapper.writeValueAsString(Map.of("programId", programId)));
    jdbc.update("DELETE FROM inspection_results");
    jdbc.update("DELETE FROM inspections");
    jdbc.update(
        "UPDATE inspection_applications SET status='DRAFT' WHERE application_id=?", applicationId);
    mvc.perform(get("/api/resident/progress").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.items[0].nextAction.type").value("UPLOAD_INSPECTION_PHOTO"));
  }

  @Test
  void prioritizesDirectActionAndKeepsOtherActiveFlowsSeparateFromClosed() throws Exception {
    jdbc.update(
        "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status,result_json) VALUES(?,'부천시 소사구','202호','CAUTION','COMPLETED','{}')",
        userId);
    long otherAssessment =
        jdbc.queryForObject("SELECT MAX(assessment_id) FROM risk_assessments", Long.class);
    jdbc.update(
        "INSERT INTO inspection_applications(applicant_name,applicant_user_id,assessment_id,receipt_no,address,detail_address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,status) VALUES('주민',?,?,'OTHER-PROGRESS','부천시 소사구','202호',37.5,126.7,'010-1234-5678','OWNER','NO',FALSE,'DRAFT')",
        userId,
        otherAssessment);
    long otherApplication =
        jdbc.queryForObject("SELECT MAX(application_id) FROM inspection_applications", Long.class);
    jdbc.update("UPDATE inspection_results SET result_status='DRAFT',published_at=NULL");
    mvc.perform(get("/api/resident/progress").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.items[0].relatedIds.applicationId").value(otherApplication))
        .andExpect(jsonPath("$.result.items[0].nextAction.type").value("UPLOAD_INSPECTION_PHOTO"))
        .andExpect(jsonPath("$.result.items[1].assessmentId").value(assessmentId))
        .andExpect(jsonPath("$.result.items[1].closed").value(false));
  }

  @Test
  void equalPriorityUsesMostRecentStateChange() throws Exception {
    jdbc.update("DELETE FROM inspection_results");
    jdbc.update("DELETE FROM inspections");
    jdbc.update(
        "UPDATE inspection_applications SET status='DRAFT',created_at='2030-01-01 10:00:00' WHERE application_id=?",
        applicationId);
    jdbc.update(
        "UPDATE risk_assessments SET created_at='2030-01-01 09:00:00' WHERE assessment_id=?",
        assessmentId);
    jdbc.update(
        "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status,result_json,created_at) VALUES(?,'부천시 소사구','202호','HIGH','COMPLETED','{}','2030-01-02 09:00:00')",
        userId);
    long other = jdbc.queryForObject("SELECT MAX(assessment_id) FROM risk_assessments", Long.class);
    jdbc.update(
        "INSERT INTO inspection_applications(applicant_name,applicant_user_id,assessment_id,receipt_no,address,detail_address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,status,created_at) VALUES('주민',?,?,'ORDER-OTHER','부천시 소사구','202호',37.5,126.7,'010-1234-5678','OWNER','NO',FALSE,'DRAFT','2030-01-02 10:00:00')",
        userId,
        other);
    mvc.perform(get("/api/resident/progress").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.items[0].assessmentId").value(other))
        .andExpect(jsonPath("$.result.items[1].assessmentId").value(assessmentId))
        .andExpect(jsonPath("$.result.items[1].closed").value(false));
  }

  @Test
  void unrelatedSupportFormUpdateDoesNotReorderInspectionStages() throws Exception {
    jdbc.update(
        "UPDATE inspections SET status='RECEIVED',updated_at='2030-01-01 10:00:00' WHERE application_id=?",
        applicationId);
    jdbc.update(
        "UPDATE inspection_applications SET submitted_at='2030-01-01 10:00:00' WHERE application_id=?",
        applicationId);
    jdbc.update(
        "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status,result_json,created_at) VALUES(?,'부천시 소사구','202호','HIGH','COMPLETED','{}','2030-01-02 09:00:00')",
        userId);
    long other = jdbc.queryForObject("SELECT MAX(assessment_id) FROM risk_assessments", Long.class);
    jdbc.update(
        "INSERT INTO inspection_applications(applicant_name,applicant_user_id,assessment_id,receipt_no,address,detail_address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,status,created_at,submitted_at) VALUES('주민',?,?,'ORDER-RECEIVED','부천시 소사구','202호',37.5,126.7,'010-1234-5678','OWNER','NO',FALSE,'RECEIVED','2030-01-02 09:00:00','2030-01-02 10:00:00')",
        userId,
        other);
    String id = UUID.randomUUID().toString();
    String draft =
        mapper.writeValueAsString(
            Map.of("applicationId", applicationId, "fields", Map.of("programId", programId)));
    mvc.perform(
            put("/api/resident/support-form-drafts/{id}", id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(draft))
        .andExpect(status().isOk());
    jdbc.update("DELETE FROM inspection_results");
    jdbc.update(
        "UPDATE resident_form_drafts SET updated_at='2040-01-01 10:00:00' WHERE draft_id=?", id);
    mvc.perform(get("/api/resident/progress").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.items[0].assessmentId").value(other))
        .andExpect(jsonPath("$.result.items[1].assessmentId").value(assessmentId));
  }

  @Test
  void scheduledOrderingUsesStatusHistoryInsteadOfMutableUpdatedAt() throws Exception {
    jdbc.update("DELETE FROM inspection_results");
    jdbc.update(
        "UPDATE inspections SET status='SCHEDULED',updated_at='2040-01-01 10:00:00' WHERE application_id=?",
        applicationId);
    long firstInspection =
        jdbc.queryForObject(
            "SELECT inspection_id FROM inspections WHERE application_id=?",
            Long.class,
            applicationId);
    jdbc.update(
        "INSERT INTO inspection_status_histories(inspection_id,previous_status,status,change_source,changed_at) VALUES(?,'RECEIVED','SCHEDULED','MANUAL','2030-01-01 10:00:00')",
        firstInspection);
    jdbc.update(
        "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status,result_json) VALUES(?,'부천시 소사구','202호','HIGH','COMPLETED','{}')",
        userId);
    long other = jdbc.queryForObject("SELECT MAX(assessment_id) FROM risk_assessments", Long.class);
    jdbc.update(
        "INSERT INTO inspection_applications(applicant_name,applicant_user_id,assessment_id,receipt_no,address,detail_address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,status) VALUES('주민',?,?,'ORDER-SCHEDULED','부천시 소사구','202호',37.5,126.7,'010-1234-5678','OWNER','NO',FALSE,'RECEIVED')",
        userId,
        other);
    long otherApplication =
        jdbc.queryForObject("SELECT MAX(application_id) FROM inspection_applications", Long.class);
    jdbc.update(
        "INSERT INTO inspections(application_id,status,updated_at) VALUES(?,'SCHEDULED','2030-01-02 10:00:00')",
        otherApplication);
    long otherInspection =
        jdbc.queryForObject("SELECT MAX(inspection_id) FROM inspections", Long.class);
    jdbc.update(
        "INSERT INTO inspection_status_histories(inspection_id,previous_status,status,change_source,changed_at) VALUES(?,'RECEIVED','SCHEDULED','MANUAL','2030-01-02 10:00:00')",
        otherInspection);
    mvc.perform(get("/api/resident/progress").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.items[0].assessmentId").value(other));
  }

  @Test
  void legacySupportRequestWithoutHeaderRetainsCreatedResponse() throws Exception {
    String submitted =
        mapper.writeValueAsString(
            Map.of(
                "applicationId",
                applicationId,
                "programId",
                programId,
                "requestedFacility",
                "물막이판",
                "preferredVisitDate",
                LocalDate.now().plusDays(2).toString(),
                "preferredVisitTime",
                "ANY"));
    mvc.perform(
            post("/api/resident/support-applications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(submitted))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value("SUPPORT_201_1"))
        .andExpect(jsonPath("$.result.applicationId").value(applicationId))
        .andExpect(jsonPath("$.result.status").value("SUBMITTED"));
  }

  @Test
  void legacySubmissionLinksOneMatchingDraftAndConsentCompletesIt() throws Exception {
    jdbc.update(
        "UPDATE inspection_applications SET occupancy_type='TENANT' WHERE application_id=?",
        applicationId);
    String id = UUID.randomUUID().toString();
    String draft =
        mapper.writeValueAsString(
            Map.of("applicationId", applicationId, "fields", Map.of("programId", programId)));
    mvc.perform(
            put("/api/resident/support-form-drafts/{id}", id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(draft))
        .andExpect(status().isOk());
    String body =
        mapper.writeValueAsString(
            Map.of(
                "applicationId",
                applicationId,
                "programId",
                programId,
                "requestedFacility",
                "물막이판",
                "preferredVisitDate",
                LocalDate.now().plusDays(2).toString(),
                "preferredVisitTime",
                "ANY"));
    var result =
        mvc.perform(
                post("/api/resident/support-applications")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.result.status").value("DRAFT"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long supportId = mapper.readTree(result).path("result").path("supportApplicationId").asLong();
    assertThat(
            jdbc.queryForObject(
                "SELECT form_state FROM resident_form_drafts WHERE draft_id=?", String.class, id))
        .isEqualTo("LINKED");
    var bytes = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(
        new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB),
        "png",
        bytes);
    mvc.perform(
            multipart("/api/resident/support-applications/{id}/attachments", supportId)
                .file(
                    new MockMultipartFile("file", "consent.png", "image/png", bytes.toByteArray()))
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isCreated());
    assertThat(
            jdbc.queryForObject(
                "SELECT form_state FROM resident_form_drafts WHERE draft_id=?", String.class, id))
        .isEqualTo("COMPLETED");
    mvc.perform(
            post("/api/resident/support-applications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SUPPORT_409_1"));
  }

  @Test
  void legacySubmissionDoesNotGuessBetweenSeveralDrafts() throws Exception {
    String draft =
        mapper.writeValueAsString(
            Map.of("applicationId", applicationId, "fields", Map.of("programId", programId)));
    mvc.perform(
            put("/api/resident/support-form-drafts/{id}", UUID.randomUUID())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(draft))
        .andExpect(status().isOk());
    insertLegacyDraft(
        UUID.randomUUID().toString(), "SUPPORT", applicationId, Map.of("programId", programId));
    String body =
        mapper.writeValueAsString(
            Map.of(
                "applicationId",
                applicationId,
                "programId",
                programId,
                "requestedFacility",
                "물막이판",
                "preferredVisitDate",
                LocalDate.now().plusDays(2).toString(),
                "preferredVisitTime",
                "ANY"));
    mvc.perform(
            post("/api/resident/support-applications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM resident_form_drafts WHERE form_state='EDITING' AND linked_id IS NULL",
                Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM support_applications WHERE application_id=?",
                Integer.class,
                applicationId))
        .isEqualTo(1);
    mvc.perform(
            get("/api/resident/support-applications/drafts")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.drafts.length()").value(0));
  }

  @Test
  void legacySubmissionDoesNotLinkAConflictingSingleDraft() throws Exception {
    String id = UUID.randomUUID().toString();
    String draft =
        mapper.writeValueAsString(
            Map.of("applicationId", applicationId, "fields", Map.of("requestedFacility", "別の施設")));
    mvc.perform(
            put("/api/resident/support-form-drafts/{id}", id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(draft))
        .andExpect(status().isOk());
    String body =
        mapper.writeValueAsString(
            Map.of(
                "applicationId",
                applicationId,
                "programId",
                programId,
                "requestedFacility",
                "物막이판",
                "preferredVisitDate",
                LocalDate.now().plusDays(2).toString(),
                "preferredVisitTime",
                "ANY"));
    mvc.perform(
            post("/api/resident/support-applications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());
    assertThat(
            jdbc.queryForObject(
                "SELECT form_state FROM resident_form_drafts WHERE draft_id=?", String.class, id))
        .isEqualTo("EDITING");
    assertThat(
            jdbc.queryForObject(
                "SELECT linked_id FROM resident_form_drafts WHERE draft_id=?", Long.class, id))
        .isNull();
    mvc.perform(
            get("/api/resident/support-applications/drafts")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.drafts.length()").value(0));
  }

  @Test
  void failedAutomaticAssociationKeepsSubmissionAndDraftAvailable() throws Exception {
    String id = UUID.randomUUID().toString();
    String form =
        mapper.writeValueAsString(
            Map.of("applicationId", applicationId, "fields", Map.of("programId", programId)));
    mvc.perform(
            put("/api/resident/support-form-drafts/{id}", id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(form))
        .andExpect(status().isOk());
    org.mockito.Mockito.doThrow(new IllegalStateException("simulated association failure"))
        .when(draftService)
        .link(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.eq("SUPPORT"),
            org.mockito.ArgumentMatchers.notNull(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyBoolean());
    String body =
        mapper.writeValueAsString(
            Map.of(
                "applicationId",
                applicationId,
                "programId",
                programId,
                "requestedFacility",
                "물막이판",
                "preferredVisitDate",
                LocalDate.now().plusDays(2).toString(),
                "preferredVisitTime",
                "ANY"));
    mvc.perform(
            post("/api/resident/support-applications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM support_applications WHERE application_id=?",
                Integer.class,
                applicationId))
        .isEqualTo(1);
    mvc.perform(
            get("/api/resident/support-applications/drafts")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.drafts.length()").value(0));
    assertThat(
            jdbc.queryForObject(
                "SELECT form_state FROM resident_form_drafts WHERE draft_id=?", String.class, id))
        .isEqualTo("EDITING");
    mvc.perform(
            post("/api/resident/support-applications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SUPPORT_409_1"));
  }

  @Test
  void explicitDraftForAnotherSourceCannotCreateOrLinkSupport() throws Exception {
    String id = UUID.randomUUID().toString();
    String draft =
        mapper.writeValueAsString(
            Map.of("applicationId", applicationId, "fields", Map.of("programId", programId)));
    mvc.perform(
            put("/api/resident/support-form-drafts/{id}", id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(draft))
        .andExpect(status().isOk());
    String body =
        mapper.writeValueAsString(
            Map.of(
                "applicationId",
                applicationId + 999,
                "programId",
                programId,
                "requestedFacility",
                "물막이판",
                "preferredVisitDate",
                LocalDate.now().plusDays(2).toString(),
                "preferredVisitTime",
                "ANY"));
    mvc.perform(
            post("/api/resident/support-applications")
                .header("Authorization", "Bearer " + token)
                .header("X-Form-Draft-Id", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isConflict());
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM support_applications", Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT form_state FROM resident_form_drafts WHERE draft_id=?", String.class, id))
        .isEqualTo("EDITING");
  }

  @Test
  void openApiKeepsSixEndpointsAndOptionalLegacyHeaders() throws Exception {
    var response =
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var doc = mapper.readTree(response);
    var paths = doc.path("paths");
    for (String route :
        java.util.List.of(
            "/api/resident/application-form-drafts/{draftId}",
            "/api/resident/support-form-drafts/{draftId}"))
      assertThat(
              paths
                  .path(route)
                  .path("put")
                  .path("responses")
                  .path("409")
                  .path("content")
                  .path("application/json")
                  .path("examples")
                  .path("editingFormExists")
                  .path("value")
                  .path("code")
                  .asText())
          .isEqualTo("FORM_DRAFT_409_6");
    assertThat(
            paths
                .path("/api/resident/support-form-drafts/{draftId}")
                .path("put")
                .path("responses")
                .path("200")
                .isObject())
        .isTrue();
    assertThat(
            paths
                .path("/api/resident/support-form-drafts/{draftId}")
                .path("put")
                .path("responses")
                .path("400")
                .path("content")
                .path("application/json")
                .path("examples")
                .path("resultNotPublished")
                .path("value")
                .path("code")
                .asText())
        .isEqualTo("SUPPORT_400_2");
    for (var route :
        new String[][] {
          {"/api/resident/progress", "get"},
          {"/api/resident/risk-assessments/{assessmentId}", "get"},
          {"/api/resident/applications/drafts", "get"},
          {"/api/resident/application-form-drafts/{draftId}", "put"},
          {"/api/resident/support-applications/drafts", "get"},
          {"/api/resident/support-form-drafts/{draftId}", "put"}
        }) assertThat(paths.path(route[0]).path(route[1]).isObject()).as(route[0]).isTrue();
    var params = paths.path("/api/resident/progress").path("get").path("parameters");
    var names = new java.util.HashSet<String>();
    for (var parameter : params) names.add(parameter.path("name").asString());
    assertThat(names).contains("cursor", "size").doesNotContain("historyCursor", "historySize");
    for (var parameter :
        paths.path("/api/resident/applications/drafts").path("get").path("parameters"))
      assertThat(parameter.path("name").asString()).isNotEqualTo("includeConflicting");
    var flowProperties = doc.path("components").path("schemas").path("Flow").path("properties");
    assertThat(flowProperties.has("receiptNumber")).isTrue();
    assertThat(flowProperties.has("title")).isTrue();
    assertThat(flowProperties.has("applicationNumber")).isFalse();
    for (String route :
        java.util.List.of("/api/resident/applications", "/api/resident/support-applications")) {
      var operation = paths.path(route).path("post");
      boolean found = false;
      for (var parameter : operation.path("parameters")) {
        if ("X-Form-Draft-Id".equals(parameter.path("name").asString())) {
          found = true;
          assertThat(parameter.path("required").asBoolean()).isFalse();
        }
      }
      assertThat(found).as(route).isTrue();
      assertThat(operation.path("requestBody").path("required").asBoolean()).isTrue();
      assertThat(operation.path("responses").path("201").isObject()).isTrue();
    }
  }

  @Test
  void rejectsAnotherUserAndStaleDraftUpdates() throws Exception {
    String id = UUID.randomUUID().toString();
    String initial =
        mapper.writeValueAsString(
            Map.of("applicationId", applicationId, "fields", Map.of("requestedFacility", "물막이판")));
    mvc.perform(
            put("/api/resident/support-form-drafts/{id}", id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(initial))
        .andExpect(status().isOk());
    String update =
        mapper.writeValueAsString(
            Map.of(
                "applicationId",
                applicationId,
                "version",
                1,
                "fields",
                Map.of("requestedFacility", "배수펌프")));
    mvc.perform(
            put("/api/resident/support-form-drafts/{id}", id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(update))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.version").value(2));
    mvc.perform(
            put("/api/resident/support-form-drafts/{id}", id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(update))
        .andExpect(status().isConflict());
    var other = users.saveAndFlush(User.resident("draft-other@example.test", "hash", "다른 주민"));
    mvc.perform(
            put("/api/resident/support-form-drafts/{id}", id)
                .header("Authorization", "Bearer " + jwt.issue(other).accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(initial))
        .andExpect(status().isNotFound());
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
  void inspectionFormLinksToExistingDraftAndCompletesOnlyAfterPhotoUpload(boolean useHeader)
      throws Exception {
    jdbc.update(
        "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status,result_json) VALUES(?,'부천시 소사구','202호','HIGH','COMPLETED','{}')",
        userId);
    long other = jdbc.queryForObject("SELECT MAX(assessment_id) FROM risk_assessments", Long.class);
    String id = UUID.randomUUID().toString();
    String partial =
        mapper.writeValueAsString(
            Map.of("assessmentId", other, "fields", Map.of("applicantName", "주민")));
    mvc.perform(
            put("/api/resident/application-form-drafts/{id}", id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(partial))
        .andExpect(status().isOk());
    mvc.perform(get("/api/resident/applications/drafts").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.drafts[0].draftId").value(id));
    String submitted =
        mapper.writeValueAsString(
            Map.ofEntries(
                Map.entry("assessmentId", other),
                Map.entry("address", "부천시 소사구"),
                Map.entry("detailAddress", "202호"),
                Map.entry("latitude", 37.5),
                Map.entry("longitude", 126.7),
                Map.entry("contact", "010-1234-5678"),
                Map.entry("occupancyType", "OWNER"),
                Map.entry("floodExperience", "NO"),
                Map.entry("existingFacility", false),
                Map.entry("preferredVisitDate", LocalDate.now().plusDays(2).toString()),
                Map.entry("preferredTimeSlot", "ANY"),
                Map.entry("inflowLocations", java.util.List.of("DRAIN")),
                Map.entry("applicantName", "주민")));
    var create =
        post("/api/resident/applications")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(submitted);
    if (useHeader) create.header("X-Form-Draft-Id", id);
    var first =
        mvc.perform(create)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.result.status").value("DRAFT"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long newApplication = mapper.readTree(first).path("result").path("applicationId").asLong();
    mvc.perform(create)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.result.applicationId").value(newApplication));
    assertThat(
            jdbc.queryForObject(
                "SELECT form_state FROM resident_form_drafts WHERE draft_id=?", String.class, id))
        .isEqualTo("LINKED");
    mvc.perform(
            patch("/api/resident/applications/{id}", newApplication)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"contact\":\"010-2222-3333\",\"requestReason\":\"수정됨\",\"inflowLocations\":[\"WINDOW\"]}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/resident/applications/drafts").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.drafts[0].fields.contact").value("010-2222-3333"))
        .andExpect(jsonPath("$.result.drafts[0].fields.requestReason").value("수정됨"))
        .andExpect(jsonPath("$.result.drafts[0].fields.inflowLocations[0]").value("WINDOW"));
    mvc.perform(get("/api/resident/progress").header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$.result.items[0].nextAction.type").value("UPLOAD_INSPECTION_PHOTO"));
    var bytes = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(
        new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB),
        "png",
        bytes);
    var photo = new MockMultipartFile("files", "photo.png", "image/png", bytes.toByteArray());
    mvc.perform(
            multipart("/api/resident/applications/{id}/attachments", newApplication)
                .file(photo)
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "photo-test"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value("APPLICATION_201_2"));
    assertThat(
            jdbc.queryForObject(
                "SELECT form_state FROM resident_form_drafts WHERE draft_id=?", String.class, id))
        .isEqualTo("COMPLETED");
    if (useHeader) {
      mvc.perform(create)
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.result.applicationId").value(newApplication));
      var expired = (tools.jackson.databind.node.ObjectNode) mapper.readTree(submitted);
      expired.put("preferredVisitDate", LocalDate.now().minusDays(1).toString());
      var prior =
          mapper.readValue(
              mapper.writeValueAsString(expired),
              com.flout.server.domain.inspectionapplication.dto.request
                  .CreateInspectionApplicationRequest.class);
      jdbc.update(
          "UPDATE resident_form_drafts SET submission_hash=? WHERE draft_id=?",
          requestHash(prior),
          id);
      jdbc.update(
          "UPDATE inspection_applications SET preferred_visit_date=? WHERE application_id=?",
          LocalDate.now().minusDays(1),
          newApplication);
      mvc.perform(
              post("/api/resident/applications")
                  .header("Authorization", "Bearer " + token)
                  .header("X-Form-Draft-Id", id)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(mapper.writeValueAsString(expired)))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.result.applicationId").value(newApplication));
      mvc.perform(
              post("/api/resident/applications")
                  .header("Authorization", "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(mapper.writeValueAsString(expired)))
          .andExpect(status().isBadRequest());
      expired.put("requestReason", "다른 요청");
      mvc.perform(
              post("/api/resident/applications")
                  .header("Authorization", "Bearer " + token)
                  .header("X-Form-Draft-Id", id)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(mapper.writeValueAsString(expired)))
          .andExpect(status().isConflict());
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM inspection_applications WHERE assessment_id=?",
                  Integer.class,
                  other))
          .isEqualTo(1);
    } else {
      mvc.perform(create).andExpect(status().isConflict());
    }
  }

  @Test
  void tenantSupportWaitsForConsentAndOnlyCompletesAfterUpload() throws Exception {
    jdbc.update(
        "UPDATE inspection_applications SET occupancy_type='TENANT' WHERE application_id=?",
        applicationId);
    String id = UUID.randomUUID().toString();
    String partial =
        mapper.writeValueAsString(
            Map.of("applicationId", applicationId, "fields", Map.of("programId", programId)));
    mvc.perform(
            put("/api/resident/support-form-drafts/{id}", id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(partial))
        .andExpect(status().isOk());
    String submitted =
        mapper.writeValueAsString(
            Map.of(
                "applicationId",
                applicationId,
                "programId",
                programId,
                "requestedFacility",
                "물막이판",
                "preferredVisitDate",
                LocalDate.now().plusDays(2).toString(),
                "preferredVisitTime",
                "ANY"));
    var create =
        post("/api/resident/support-applications")
            .header("Authorization", "Bearer " + token)
            .header("X-Form-Draft-Id", id)
            .contentType(MediaType.APPLICATION_JSON)
            .content(submitted);
    var response =
        mvc.perform(create)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.result.status").value("DRAFT"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long supportId = mapper.readTree(response).path("result").path("supportApplicationId").asLong();
    mvc.perform(get("/api/resident/progress").header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$.result.items[0].nextAction.type").value("UPLOAD_LANDLORD_CONSENT"));
    assertThat(
            jdbc.queryForObject(
                "SELECT form_state FROM resident_form_drafts WHERE draft_id=?", String.class, id))
        .isEqualTo("LINKED");
    var bad = new MockMultipartFile("file", "bad.pdf", "application/pdf", new byte[] {1, 2});
    mvc.perform(
            multipart("/api/resident/support-applications/{id}/attachments", supportId)
                .file(bad)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest());
    assertThat(
            jdbc.queryForObject(
                "SELECT form_state FROM resident_form_drafts WHERE draft_id=?", String.class, id))
        .isEqualTo("LINKED");
    var bytes = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(
        new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB),
        "png",
        bytes);
    var file = new MockMultipartFile("file", "consent.png", "image/png", bytes.toByteArray());
    mvc.perform(
            multipart("/api/resident/support-applications/{id}/attachments", supportId)
                .file(file)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.result.status").value("SUBMITTED"));
    assertThat(
            jdbc.queryForObject(
                "SELECT form_state FROM resident_form_drafts WHERE draft_id=?", String.class, id))
        .isEqualTo("COMPLETED");
    mvc.perform(create)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.result.supportApplicationId").value(supportId));
  }
}
