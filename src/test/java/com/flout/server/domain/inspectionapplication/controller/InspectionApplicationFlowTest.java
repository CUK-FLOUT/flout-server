package com.flout.server.domain.inspectionapplication.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.inspection.repository.InspectionRepository;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationErrorCode;
import com.flout.server.domain.inspectionapplication.service.AttachmentStorage;
import com.flout.server.domain.inspectionapplication.service.ReceiptNumberGenerator;
import com.flout.server.domain.riskassessment.entity.*;
import com.flout.server.domain.riskassessment.repository.RiskAssessmentRepository;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:application-flow;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false"
    })
@AutoConfigureMockMvc
class InspectionApplicationFlowTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired RiskAssessmentRepository assessments;
  @Autowired JwtService jwt;
  @Autowired ObjectMapper mapper;
  @MockitoSpyBean InspectionRepository inspections;
  @MockitoSpyBean ReceiptNumberGenerator receiptNumbers;
  @MockitoBean AttachmentStorage storage;
  Long assessmentId;
  String token;

  // 주민과 분석 결과 준비
  @BeforeEach
  void setup() {
    for (String table :
        List.of(
            "attachment_upload_intents",
            "application_attachments",
            "inspection_status_histories",
            "inspections",
            "application_inflow_locations",
            "application_status_histories",
            "inspection_applications",
            "prevention_guides",
            "risk_assessments",
            "refresh_tokens",
            "user_consents",
            "users")) jdbc.update("DELETE FROM " + table);
    var resident = users.saveAndFlush(User.resident("flow@example.com", "hash", "주민"));
    token = jwt.issue(resident).accessToken();
    assessmentId =
        assessments
            .saveAndFlush(
                RiskAssessment.completedDemo(
                    resident, "경기도 부천시 원미구 길주로 211", "202호", RiskLevel.HIGH))
            .getId();
  }

  @Test
  void rejectsMissingBlankAndOversizedApplicantNames() throws Exception {
    for (String invalid : List.of("", "   ", "가".repeat(51))) {
      var body = request();
      body.put("applicantName", invalid);
      mvc.perform(
              post("/api/resident/applications")
                  .header("Authorization", "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(mapper.writeValueAsString(body)))
          .andExpect(status().isBadRequest());
    }
    var body = request();
    body.remove("applicantName");
    mvc.perform(
            post("/api/resident/applications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inspection_applications", Long.class))
        .isZero();
  }

  @Test
  void validatesContactFormatOnPostAndPatchAndKeepsPatchContactOptional() throws Exception {
    for (String invalid :
        List.of("abc123", "010-123", "     ", "011234", "010-12345678", "010-1234-56789")) {
      var body = request();
      body.put("contact", invalid);
      mvc.perform(
              post("/api/resident/applications")
                  .header("Authorization", "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(mapper.writeValueAsString(body)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("국내 휴대전화")));
    }
    var missing = request();
    missing.remove("contact");
    mvc.perform(
            post("/api/resident/applications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(missing)))
        .andExpect(status().isBadRequest());

    long id = createDraft();
    update(id, "{\"requestReason\":\"필드 생략\"}", token).andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "SELECT contact FROM inspection_applications WHERE application_id=?",
                String.class,
                id))
        .isEqualTo("010-1234-5678");
    update(id, "{\"contact\":null}", token).andExpect(status().isBadRequest());
    for (String invalid : List.of("abc123", "010-123", "   ", "010-1234-56789")) {
      update(id, "{\"contact\":\"" + invalid + "\"}", token)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("국내 휴대전화")));
    }
    update(id, "{\"contact\":\"01012345678\"}", token).andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "SELECT contact FROM inspection_applications WHERE application_id=?",
                String.class,
                id))
        .isEqualTo("01012345678");
  }

  @Test
  void changedApplicantNameDoesNotReuseDraft() throws Exception {
    submit().andExpect(status().isCreated());
    var body = request();
    body.put("applicantName", "다른이름");
    mvc.perform(
            post("/api/resident/applications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
        .andExpect(status().isConflict());
    assertThat(
            jdbc.queryForObject("SELECT applicant_name FROM inspection_applications", String.class))
        .isEqualTo("신청자");
  }

  private Map<String, Object> request() {
    var body =
        new java.util.HashMap<String, Object>(
            Map.of(
                "assessmentId",
                assessmentId,
                "latitude",
                37.487485,
                "longitude",
                126.7985672,
                "contact",
                "010-1234-5678",
                "occupancyType",
                "OWNER",
                "floodExperience",
                "YES",
                "existingFacility",
                false,
                "preferredVisitDate",
                LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1).toString(),
                "preferredTimeSlot",
                "MORNING",
                "inflowLocations",
                List.of("WINDOW", "DRAIN")));
    body.put("applicantName", "  신청자  ");
    body.put("address", "경기도 부천시 원미구 길주로 211");
    body.put("detailAddress", "202호");
    return body;
  }

  private org.springframework.test.web.servlet.ResultActions submit() throws Exception {
    return mvc.perform(
        post("/api/resident/applications")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(request())));
  }

  private long createDraft() throws Exception {
    var response =
        submit().andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return mapper.readTree(response).get("result").get("applicationId").asLong();
  }

  private org.springframework.test.web.servlet.ResultActions update(
      long id, String body, String accessToken) throws Exception {
    return mvc.perform(
        patch("/api/resident/applications/{id}", id)
            .header("Authorization", "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  @Test
  void updatesOnlyProvidedFieldsAndPreservesIdentity() throws Exception {
    long id = createDraft();
    var original =
        jdbc.queryForMap(
            "SELECT application_id, assessment_id, receipt_no, created_at, submitted_at, address FROM inspection_applications WHERE application_id=?",
            id);
    update(
            id,
            """
        {"applicantName":"  수정 이름  ","contact":"01012345678","occupancyType":"TENANT",
         "floodExperience":"NO","existingFacility":true,"requestReason":"사유",
         "preferredVisitDate":"2099-01-01","preferredTimeSlot":"AFTERNOON",
         "inflowLocations":["DRAIN","ENTRANCE","DRAIN"]}
        """,
            token)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("APPLICATION_200_4"))
        .andExpect(jsonPath("$.result.applicationId").value(id))
        .andExpect(jsonPath("$.result.status").value("DRAFT"));
    mvc.perform(
            get("/api/resident/applications/{id}", id).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.applicant.name").value("수정 이름"))
        .andExpect(jsonPath("$.result.applicant.phone").value("01012345678"))
        .andExpect(jsonPath("$.result.occupancyType").value("TENANT"))
        .andExpect(jsonPath("$.result.floodExperience").value("NO"))
        .andExpect(jsonPath("$.result.existingFacility").value(true))
        .andExpect(jsonPath("$.result.requestReason").value("사유"))
        .andExpect(jsonPath("$.result.preferredVisitDate").value("2099-01-01"))
        .andExpect(jsonPath("$.result.preferredTimeSlot").value("AFTERNOON"))
        .andExpect(jsonPath("$.result.inflowLocations.length()").value(2));
    update(id, "{\"contact\":\"010-9999-9999\"}", token).andExpect(status().isOk());
    var changed =
        jdbc.queryForMap(
            "SELECT application_id, assessment_id, receipt_no, created_at, submitted_at, address FROM inspection_applications WHERE application_id=?",
            id);
    assertThat(changed).isEqualTo(original);
    mvc.perform(
            get("/api/resident/applications/{id}", id).header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$.result.applicant.name").value("수정 이름"))
        .andExpect(jsonPath("$.result.requestReason").value("사유"))
        .andExpect(jsonPath("$.result.inflowLocations.length()").value(2));
    update(
            id,
            "{\"requestReason\":null,\"preferredVisitDate\":null,\"preferredTimeSlot\":null,\"inflowLocations\":[]}",
            token)
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/resident/applications/{id}", id).header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$.result.requestReason").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.result.preferredVisitDate").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.result.preferredTimeSlot").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.result.inflowLocations").isEmpty());
  }

  @Test
  void rejectsInvalidUpdatesAndEnforcesOwnershipAndDraftStatus() throws Exception {
    long id = createDraft();
    var other = users.saveAndFlush(User.resident("other-flow@example.com", "hash", "다른 주민"));
    update(id, "{\"contact\":\"010-9999-9999\"}", jwt.issue(other).accessToken())
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("APPLICATION_403_7"));
    update(0, "{}", token).andExpect(status().isBadRequest());
    update(Long.MAX_VALUE, "{}", token).andExpect(status().isNotFound());
    for (String body :
        List.of(
            "{\"applicantName\":null}",
            "{\"contact\":null}",
            "{\"occupancyType\":null}",
            "{\"floodExperience\":null}",
            "{\"existingFacility\":null}",
            "{\"inflowLocations\":null}",
            "{\"inflowLocations\":[null]}",
            "{\"contact\":\"   \"}",
            "{\"preferredVisitDate\":\"2000-01-01\"}",
            "{\"assessmentId\":2}",
            "{\"address\":\"다른 주소\"}")) {
      update(id, body, token).andExpect(status().isBadRequest());
    }
    mvc.perform(
            multipart("/api/resident/applications/{id}/attachments", id)
                .file(png())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isCreated());
    update(id, "{\"contact\":\"010-9999-9999\"}", token)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("APPLICATION_409_5"));
    assertThat(
            jdbc.queryForObject(
                "SELECT contact FROM inspection_applications WHERE application_id=?",
                String.class,
                id))
        .isEqualTo("010-1234-5678");
  }

  @Test
  void updateWaitsForPhotoReceiptAndRejectsAfterCommit() throws Exception {
    long id = createDraft();
    var holding = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    doAnswer(
            invocation -> {
              holding.countDown();
              if (!release.await(30, java.util.concurrent.TimeUnit.SECONDS))
                throw new IllegalStateException("timeout");
              var inspection = inspections.save(invocation.getArgument(0));
              inspections.flush();
              return inspection;
            })
        .when(inspections)
        .saveAndFlush(any());
    var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      var upload =
          executor.submit(
              () ->
                  mvc.perform(
                          multipart("/api/resident/applications/{id}/attachments", id)
                              .file(png())
                              .header("Authorization", "Bearer " + token))
                      .andReturn()
                      .getResponse()
                      .getStatus());
      assertThat(holding.await(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      var patchStarted = new java.util.concurrent.CountDownLatch(1);
      var patch =
          executor.submit(
              () -> {
                patchStarted.countDown();
                return update(id, "{\"contact\":\"010-9999-9999\"}", token)
                    .andReturn()
                    .getResponse()
                    .getStatus();
              });
      assertThat(patchStarted.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      assertThatThrownBy(() -> patch.get(500, java.util.concurrent.TimeUnit.MILLISECONDS))
          .isInstanceOf(java.util.concurrent.TimeoutException.class);
      release.countDown();
      assertThat(upload.get(30, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(201);
      assertThat(patch.get(30, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(409);
      assertThat(
              jdbc.queryForObject(
                  "SELECT contact FROM inspection_applications WHERE application_id=?",
                  String.class,
                  id))
          .isEqualTo("010-1234-5678");
    } finally {
      release.countDown();
      executor.shutdownNow();
      reset(inspections);
    }
  }

  @Test
  void documentsUpdateEndpoint() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                    "$.paths['/api/resident/applications/{applicationId}'].patch.responses['404'].description")
                .value(InspectionApplicationErrorCode.APPLICATION_NOT_FOUND.getMessage()))
        .andExpect(
            jsonPath(
                    "$.paths['/api/resident/applications/{applicationId}'].patch.responses['409'].description")
                .value(InspectionApplicationErrorCode.APPLICATION_NOT_DRAFT.getMessage()))
        .andExpect(
            jsonPath(
                    "$.paths['/api/resident/applications/{applicationId}'].patch.responses['200'].content['application/json'].examples.draftUpdated.value.code")
                .value("APPLICATION_200_4"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/resident/applications/{applicationId}'].patch.responses['409'].content['application/json'].examples.applicationNotDraft.value.code")
                .value("APPLICATION_409_5"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/resident/applications/{applicationId}'].patch.requestBody.content['application/json'].examples.partialUpdate.value.contact")
                .value("010-1234-5678"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/resident/applications/{applicationId}'].patch.responses['400'].content['application/json'].examples.invalidContact.value.message")
                .value(org.hamcrest.Matchers.containsString("01012345678")))
        .andExpect(
            jsonPath("$.components.schemas.UpdateInspectionApplicationRequest.properties.contact")
                .exists())
        .andExpect(
            jsonPath(
                    "$.components.schemas.UpdateInspectionApplicationRequest.properties.requestReasonProvided")
                .doesNotExist());
  }

  @Test
  void acceptsHyphenatedAndUnhyphenatedMobileNumbersOnPost() throws Exception {
    for (String contact : List.of("010-1234-5678", "01012345678")) {
      var body = request();
      body.put("contact", contact);
      mvc.perform(
              post("/api/resident/applications")
                  .header("Authorization", "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(mapper.writeValueAsString(body)))
          .andExpect(status().isCreated());
      if (contact.equals("010-1234-5678")) {
        assessmentId =
            assessments
                .saveAndFlush(
                    RiskAssessment.completedDemo(
                        users.findByEmail("flow@example.com").orElseThrow(),
                        "경기도 부천시 원미구 길주로 211",
                        "202호",
                        RiskLevel.HIGH))
                .getId();
      }
    }
  }

  @Test
  void inspectionLookupFailureRollsBackPhotoAndReceiptAndRetryDoesNotDuplicate() throws Exception {
    var response =
        submit().andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    long applicationId = mapper.readTree(response).get("result").get("applicationId").asLong();
    doThrow(new org.springframework.dao.DataAccessResourceFailureException("lookup failed"))
        .when(inspections)
        .findByApplicationId(applicationId);
    mvc.perform(
            multipart("/api/resident/applications/{id}/attachments", applicationId)
                .file(png())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isInternalServerError());
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM application_attachments", Long.class))
        .isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inspections", Long.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM application_status_histories", Long.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM inspection_applications WHERE application_id=?",
                String.class,
                applicationId))
        .isEqualTo("DRAFT");
    assertThat(
            jdbc.queryForObject(
                "SELECT submitted_at FROM inspection_applications WHERE application_id=?",
                java.sql.Timestamp.class,
                applicationId))
        .isNull();
    update(applicationId, "{\"contact\":\"010-9999-9999\"}", token).andExpect(status().isOk());
    reset(inspections);
    mvc.perform(
            multipart("/api/resident/applications/{id}/attachments", applicationId)
                .file(png())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.result.inspectionId").isNumber())
        .andExpect(jsonPath("$.result.attachments.length()").value(1));
    assertThat(
            jdbc.queryForObject(
                "SELECT contact FROM inspection_applications WHERE application_id=?",
                String.class,
                applicationId))
        .isEqualTo("010-9999-9999");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM application_attachments", Long.class))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inspections", Long.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM application_status_histories", Long.class))
        .isEqualTo(1);
  }

  @Test
  void submissionCreatesDraftAndPhotoUploadReceivesIt() throws Exception {
    var response =
        submit()
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.result.status").value("DRAFT"))
            .andExpect(jsonPath("$.result.inspectionId").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.result.submittedAt").value(org.hamcrest.Matchers.nullValue()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    var result = mapper.readTree(response).get("result");
    Long applicationId = result.get("applicationId").asLong();
    assertThat(result.get("receiptNo").asText())
        .isEqualTo(String.format(java.util.Locale.ROOT, "%06d", applicationId));
    var application =
        jdbc.queryForMap(
            "SELECT address, detail_address, applicant_name FROM inspection_applications WHERE application_id = ?",
            applicationId);
    assertThat(application.get("applicant_name")).isEqualTo("신청자");
    assertThat(application.get("address")).isEqualTo("경기도 부천시 원미구 길주로 211");
    assertThat(application.get("detail_address")).isEqualTo("202호");
    jdbc.update(
        "UPDATE inspection_applications SET created_at='2020-01-01 00:00:00' WHERE application_id=?",
        applicationId);
    mvc.perform(
            get("/api/resident/applications/{id}", applicationId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.submittedAt").value(org.hamcrest.Matchers.nullValue()));
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inspections", Long.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM application_status_histories", Long.class))
        .isZero();
    var beforeReceipt =
        java.time.LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    mvc.perform(
            multipart("/api/resident/applications/{id}/attachments", applicationId)
                .file(png())
                .header("Idempotency-Key", "initial-photo")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isCreated())
        .andExpect(
            jsonPath("$.result.inspectionId")
                .value(
                    jdbc.queryForObject(
                        "SELECT inspection_id FROM inspections WHERE application_id=?",
                        Long.class,
                        applicationId)))
        .andExpect(jsonPath("$.result.attachments[0].attachmentId").isNumber());
    var submittedAt =
        jdbc.queryForObject(
                "SELECT submitted_at FROM inspection_applications WHERE application_id=?",
                java.sql.Timestamp.class,
                applicationId)
            .toLocalDateTime();
    assertThat(submittedAt)
        .isAfterOrEqualTo(beforeReceipt)
        .isBeforeOrEqualTo(java.time.LocalDateTime.now().plusSeconds(1));
    mvc.perform(
            get("/api/resident/applications/{id}", applicationId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.createdAt").value("2020-01-01T00:00:00"))
        .andExpect(jsonPath("$.result.submittedAt").value(submittedAt.toString()));
    mvc.perform(
            multipart("/api/resident/applications/{id}/attachments", applicationId)
                .file(png())
                .header("Idempotency-Key", "initial-photo")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isCreated())
        .andExpect(
            jsonPath("$.result.inspectionId")
                .value(
                    jdbc.queryForObject(
                        "SELECT inspection_id FROM inspections WHERE application_id=?",
                        Long.class,
                        applicationId)))
        .andExpect(jsonPath("$.result.attachments[0].attachmentId").isNumber());
    assertThat(
            jdbc.queryForObject(
                    "SELECT submitted_at FROM inspection_applications WHERE application_id=?",
                    java.sql.Timestamp.class,
                    applicationId)
                .toLocalDateTime())
        .isEqualTo(submittedAt);
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM inspection_applications WHERE application_id=?",
                String.class,
                applicationId))
        .isEqualTo("RECEIVED");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM inspections WHERE application_id=?",
                Long.class,
                applicationId))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM application_status_histories WHERE application_id=?",
                Long.class,
                applicationId))
        .isEqualTo(1);
    var row = jdbc.queryForMap("SELECT * FROM inspections WHERE application_id = ?", applicationId);
    Long inspectionId = ((Number) row.get("inspection_id")).longValue();
    assertThat(((Number) row.get("application_id")).longValue()).isEqualTo(applicationId);
    assertThat(row.get("status")).isEqualTo("RECEIVED");
    assertThat(row.get("scheduled_at")).isNull();
    assertThat(row.get("inspector_user_id")).isNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM application_status_histories WHERE application_id = ?",
                String.class,
                applicationId))
        .isEqualTo("RECEIVED");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM application_inflow_locations", Long.class))
        .isEqualTo(2);
    jdbc.update(
        """
        INSERT INTO users (email, password_hash, name, role, jurisdiction_id)
        VALUES ('flow-admin@example.com', 'hash', '운영자', 'ADMIN',
          (SELECT jurisdiction_id FROM jurisdictions WHERE name = '부천시'))
        """);
    var admin = users.findByEmail("flow-admin@example.com").orElseThrow();
    mvc.perform(
            post("/api/admin/inspections/" + inspectionId + "/assignment")
                .header("Authorization", "Bearer " + jwt.issue(admin).accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"visitDate\":\"2030-01-03\",\"visitTime\":\"14:00\",\"preVisitNotice\":\"방문 안내\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void rejectsOverlongReasonAndNullInflowLocation() throws Exception {
    var body = request();
    body.put("requestReason", "가".repeat(2001));
    mvc.perform(
            post("/api/resident/applications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
    body.remove("requestReason");
    body.put("inflowLocations", java.util.Collections.singletonList(null));
    mvc.perform(
            post("/api/resident/applications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
  }

  // 점검 생성 실패 시 신청 롤백
  @Test
  void inspectionFailureDuringUploadLeavesDraftAndRollsBackPhotos() throws Exception {
    doThrow(new IllegalStateException("simulated inspection persistence failure"))
        .when(inspections)
        .saveAndFlush(any());
    var response =
        submit().andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    long id = mapper.readTree(response).path("result").path("applicationId").asLong();
    mvc.perform(
            multipart("/api/resident/applications/{id}/attachments", id)
                .file(png())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isInternalServerError());
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM inspection_applications WHERE application_id=?",
                String.class,
                id))
        .isEqualTo("DRAFT");
    for (String table :
        List.of("application_attachments", "application_status_histories", "inspections")) {
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class)).isZero();
    }
  }

  @Test
  void duplicateDraftSubmissionReturnsSameDraft() throws Exception {
    var first =
        submit().andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    var second =
        submit().andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    assertThat(mapper.readTree(first).path("result").path("applicationId").asLong())
        .isEqualTo(mapper.readTree(second).path("result").path("applicationId").asLong());
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inspection_applications", Long.class))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inspections", Long.class)).isZero();
    var changed = request();
    changed.put("contact", "010-9999-9999");
    mvc.perform(
            post("/api/resident/applications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(changed)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("APPLICATION_409_4"));
  }

  @Test
  void concurrentFirstUploadsCreateOneReceptionAndOneInspection() throws Exception {
    var response =
        submit().andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    long id = mapper.readTree(response).path("result").path("applicationId").asLong();
    var barrier = new java.util.concurrent.CyclicBarrier(2);
    doAnswer(
            invocation -> {
              barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
              return null;
            })
        .when(storage)
        .upload(anyString(), any(), anyString());
    var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      java.util.concurrent.Callable<Integer> upload =
          () ->
              mvc.perform(
                      multipart("/api/resident/applications/{id}/attachments", id)
                          .file(png())
                          .header("Authorization", "Bearer " + token))
                  .andReturn()
                  .getResponse()
                  .getStatus();
      var first = executor.submit(upload);
      var second = executor.submit(upload);
      assertThat(
              List.of(
                  first.get(30, java.util.concurrent.TimeUnit.SECONDS),
                  second.get(30, java.util.concurrent.TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 201);
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM application_attachments WHERE application_id=?",
                  Long.class,
                  id))
          .isEqualTo(2);
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM application_status_histories WHERE application_id=?",
                  Long.class,
                  id))
          .isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM inspections WHERE application_id=?", Long.class, id))
          .isEqualTo(1);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void storageFailureAndOtherResidentCannotReceiveDraft() throws Exception {
    var response =
        submit().andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    long id = mapper.readTree(response).path("result").path("applicationId").asLong();
    var other = users.saveAndFlush(User.resident("other-flow@example.com", "hash", "다른 주민"));
    mvc.perform(
            multipart("/api/resident/applications/{id}/attachments", id)
                .file(png())
                .header("Authorization", "Bearer " + jwt.issue(other).accessToken()))
        .andExpect(status().isForbidden());
    doThrow(new IllegalStateException("S3 failure"))
        .when(storage)
        .upload(anyString(), any(), anyString());
    mvc.perform(
            multipart("/api/resident/applications/{id}/attachments", id)
                .file(png())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isInternalServerError());
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM inspection_applications WHERE application_id=?",
                String.class,
                id))
        .isEqualTo("DRAFT");
    for (String table :
        List.of("application_attachments", "application_status_histories", "inspections"))
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class)).isZero();
  }

  @Test
  void draftIsExcludedFromAdminDetailAndPriorityCandidates() throws Exception {
    var response =
        submit().andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    long id = mapper.readTree(response).path("result").path("applicationId").asLong();
    mvc.perform(
            get("/api/resident/applications/{id}", id).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.applicationStatus").value("DRAFT"))
        .andExpect(jsonPath("$.result.inspectionStatus").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.result.resultPublished").value(false));
    jdbc.update(
        "INSERT INTO users(email,password_hash,name,role,jurisdiction_id) VALUES ('flow-admin@example.com','hash','운영자','ADMIN',(SELECT jurisdiction_id FROM jurisdictions WHERE name='부천시'))");
    var admin = users.findByEmail("flow-admin@example.com").orElseThrow();
    String adminToken = jwt.issue(admin).accessToken();
    mvc.perform(
            get("/api/admin/applications/{id}", id).header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/admin/priority-inspections/{id}", id)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/admin/priority-inspections").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalElements").value(0));
    jdbc.update(
        "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status) SELECT user_id,address,detail_address,risk_level,status FROM risk_assessments WHERE assessment_id=?",
        assessmentId);
    Long anotherAssessment =
        jdbc.queryForObject("SELECT MAX(assessment_id) FROM risk_assessments", Long.class);
    jdbc.update(
        "INSERT INTO inspection_applications(applicant_name,applicant_user_id,assessment_id,receipt_no,address,detail_address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,status) SELECT '김주민', applicant_user_id,?,'IA-RECEIVED-NEARBY',address,detail_address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,'RECEIVED' FROM inspection_applications WHERE application_id=?",
        anotherAssessment,
        id);
    mvc.perform(
            get("/api/admin/priority-inspections").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalElements").value(1))
        .andExpect(jsonPath("$.result.applications[0].nearbyApplicationCount").value(0));
    jdbc.update("INSERT INTO inspections(application_id,status) VALUES (?, 'RECEIVED')", id);
    mvc.perform(get("/api/admin/inspections").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalElements").value(0));
  }

  @Test
  void concurrentSubmissionsCreateOnlyOneApplication() throws Exception {
    var checked = new java.util.concurrent.CyclicBarrier(2);
    doAnswer(
            invocation -> {
              checked.await(10, java.util.concurrent.TimeUnit.SECONDS);
              return invocation.callRealMethod();
            })
        .when(receiptNumbers)
        .next();
    var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
    var ready = new java.util.concurrent.CountDownLatch(2);
    var start = new java.util.concurrent.CountDownLatch(1);
    try {
      java.util.concurrent.Callable<Integer> task =
          () -> {
            ready.countDown();
            start.await();
            var result = submit().andReturn();
            var response = result.getResponse();
            if (response.getStatus() == 500) {
              throw new AssertionError(
                  "Unexpected response: " + response.getContentAsString(),
                  result.getResolvedException());
            }
            return response.getStatus();
          };
      var first = executor.submit(task);
      var second = executor.submit(task);
      assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(
              List.of(
                  first.get(30, java.util.concurrent.TimeUnit.SECONDS),
                  second.get(30, java.util.concurrent.TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 201);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inspection_applications", Long.class))
          .isEqualTo(1);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inspections", Long.class)).isZero();
    } finally {
      executor.shutdownNow();
    }
  }

  private MockMultipartFile png() throws Exception {
    var bytes = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(
        new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB),
        "png",
        bytes);
    return new MockMultipartFile("files", "photo.png", "image/png", bytes.toByteArray());
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(
      strings = {"SEMI_BASEMENT", "FIRST_FLOOR", "SECOND_FLOOR_OR_ABOVE"})
  void storesOptionalHousingTypeAndChecksItOnDuplicateSubmission(String housing) throws Exception {
    var body = request();
    body.put("housingType", housing);
    var result =
        mvc.perform(
                post("/api/resident/applications")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andReturn();
    long id =
        mapper
            .readTree(result.getResponse().getContentAsString())
            .path("result")
            .path("applicationId")
            .asLong();
    assertThat(
            jdbc.queryForObject(
                "SELECT housing_type FROM inspection_applications WHERE application_id=?",
                String.class,
                id))
        .isEqualTo(housing);
    mvc.perform(
            post("/api/resident/applications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.result.applicationId").value(id));
    body.put("housingType", housing.equals("FIRST_FLOOR") ? "SEMI_BASEMENT" : "FIRST_FLOOR");
    mvc.perform(
            post("/api/resident/applications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
        .andExpect(status().isConflict());
    assertThat(
            jdbc.queryForObject(
                "SELECT housing_type FROM inspection_applications WHERE application_id=?",
                String.class,
                id))
        .isEqualTo(housing);
  }

  @Test
  void omittedOrNullHousingDoesNotDefaultToBasement() throws Exception {
    var body = request();
    body.put("housingType", null);
    mvc.perform(
            post("/api/resident/applications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
        .andExpect(status().isCreated());
    assertThat(
            jdbc.queryForObject("SELECT housing_type FROM inspection_applications", String.class))
        .isNull();
    submit().andExpect(status().isCreated());
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"", "BASEMENT", "반지하", "UNKNOWN", "1"})
  void rejectsUnsupportedHousingTypes(String housing) throws Exception {
    var body = request();
    body.put("housingType", housing);
    mvc.perform(
            post("/api/resident/applications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inspection_applications", Long.class))
        .isZero();
  }

  @Test
  void rejectsNumericAndBooleanHousingWithoutOrdinalCoercion() throws Exception {
    for (var housing : List.of(0, 1, true)) {
      var body = request();
      body.put("housingType", housing);
      mvc.perform(
              post("/api/resident/applications")
                  .header("Authorization", "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(mapper.writeValueAsString(body)))
          .andExpect(status().isBadRequest());
    }
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inspection_applications", Long.class))
        .isZero();
  }
}
