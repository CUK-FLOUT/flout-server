package com.flout.server.domain.supportapplication;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.inspectionapplication.service.AttachmentStorage;
import com.flout.server.domain.inspectionapplication.service.S3AttachmentStorage;
import com.flout.server.domain.supportapplication.dto.response.SupportAttachmentResponse;
import com.flout.server.domain.supportapplication.repository.SupportAttachmentRepository;
import com.flout.server.domain.supportapplication.service.ResidentSupportDetailService;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:resident-support-progress;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.s3.region=",
      "app.s3.bucket=",
      "app.inspection.auto-start.enabled=false",
      "app.s3.cleanup-interval-millis=3600000"
    })
@AutoConfigureMockMvc
class ResidentSupportProgressTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired ObjectMapper mapper;
  @Autowired SupportAttachmentRepository attachments;
  @MockitoBean AttachmentStorage storage;
  long supportId, adminId, residentId;
  String token, residentToken;

  @BeforeEach
  void setup() {

    for (var table :
        List.of(
            "support_attachments",
            "support_application_status_histories",
            "support_applications",
            "support_programs",
            "prevention_recommendations",
            "inspection_vulnerabilities",
            "inspection_results",
            "inspection_status_histories",
            "inspections",
            "application_status_histories",
            "inspection_applications",
            "risk_assessments",
            "refresh_tokens",
            "users")) jdbc.update("DELETE FROM " + table);
    var resident =
        users.saveAndFlush(
            User.resident("approval-resident@example.test", "unused-test-hash", "테스트주민"));
    residentId = resident.getId();
    residentToken = jwt.issue(resident).accessToken();
    var admin =
        users.saveAndFlush(
            User.resident("approval-admin@example.test", "unused-test-hash", "테스트운영자"));
    adminId = admin.getId();
    jdbc.update(
        "UPDATE users SET role='ADMIN',jurisdiction_id=(SELECT jurisdiction_id FROM jurisdictions WHERE name='부천시') WHERE user_id=?",
        adminId);
    token = jwt.issue(users.findById(adminId).orElseThrow()).accessToken();
    jdbc.update(
        "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status) VALUES(?,'경기도 부천시 원미구 길주로 210','fixture','HIGH','COMPLETED')",
        residentId);
    var risk = jdbc.queryForObject("SELECT assessment_id FROM risk_assessments", Long.class);
    jdbc.update(
        "INSERT INTO inspection_applications(applicant_name,applicant_user_id,assessment_id,receipt_no,address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,status) VALUES('테스트주민',?,?,'APPROVAL-TEST','경기도 부천시 원미구 길주로 210',37.5,126.76,'010-0000-0000','OWNER','NO',FALSE,'RECEIVED')",
        residentId,
        risk);
    var source =
        jdbc.queryForObject("SELECT application_id FROM inspection_applications", Long.class);
    jdbc.update("INSERT INTO inspections(application_id,status) VALUES(?,'IN_PROGRESS')", source);
    var inspection = jdbc.queryForObject("SELECT inspection_id FROM inspections", Long.class);
    jdbc.update(
        "INSERT INTO inspection_results(inspection_id,result_status,summary) VALUES(?,'DRAFT','preserved')",
        inspection);
    jdbc.update(
        "INSERT INTO application_status_histories(application_id,status) VALUES(?,'RECEIVED')",
        source);
    jdbc.update(
        "INSERT INTO support_programs(name,created_at) VALUES('검증용 지원사업',CURRENT_TIMESTAMP)");
    var program = jdbc.queryForObject("SELECT program_id FROM support_programs", Long.class);
    jdbc.update(
        "INSERT INTO support_applications(application_id,program_id,applicant_user_id,requested_facility,applicant_name,contact,address,occupancy_type,preferred_visit_date,preferred_visit_time,status,submitted_at,landlord_consent_status) VALUES(?,?,?,'출입구 물막이판','테스트주민','010-0000-0000','경기도 부천시 원미구 길주로 210','OWNER',CURRENT_DATE,'ANY','REVIEWING',CURRENT_TIMESTAMP,'NOT_REQUIRED')",
        source,
        program,
        residentId);
    supportId =
        jdbc.queryForObject("SELECT support_application_id FROM support_applications", Long.class);
  }

  ResultActions request(String view, String id, String access) throws Exception {
    var call = get("/api/resident/support-applications/" + id + "/" + view);
    if (access != null) call.header("Authorization", "Bearer " + access);
    return mvc.perform(call);
  }

  ResultActions request(String view) throws Exception {
    return request(view, Long.toString(supportId), residentToken);
  }

  void state(String value) {
    jdbc.update("UPDATE support_applications SET status=?", value);
  }

  long attach(String type) {
    jdbc.update(
        "INSERT INTO support_attachments(support_application_id,attachment_type,object_key,original_filename,file_size,content_type,submitted_at) VALUES(?,'LANDLORD_CONSENT','private/progress/consent',?,2048,?,'2026-11-05 10:20:00')",
        supportId,
        type.equals("application/pdf") ? "임대인 동의서.pdf" : "임대인 동의서.png",
        type);
    return jdbc.queryForObject("SELECT attachment_id FROM support_attachments", Long.class);
  }

  @Test
  void scheduleUsesActualScreenFieldsAndNeverUsesPreferredDateAsConfirmedDate() throws Exception {
    state("SCHEDULING");
    jdbc.update(
        "UPDATE support_applications SET applicant_name='일정테스트주민',landlord_consent_status='CONFIRMED',preferred_visit_date='2027-01-01',preferred_visit_time='AFTERNOON'");
    jdbc.update("UPDATE inspection_applications SET detail_address='101호',internal_memo='비공개메모'");
    long source =
        jdbc.queryForObject("SELECT application_id FROM inspection_applications", Long.class);
    request("schedule")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("COMMON_200"))
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.result.length()").value(12))
        .andExpect(jsonPath("$.result.supportApplicationId").value(supportId))
        .andExpect(jsonPath("$.result.applicationId").value(source))
        .andExpect(jsonPath("$.result.receiptNo").value(String.format(Locale.ROOT, "%06d", source)))
        .andExpect(jsonPath("$.result.applicantName").value("일정테스트주민"))
        .andExpect(jsonPath("$.result.address").value("경기도 부천시 원미구 길주로 210"))
        .andExpect(jsonPath("$.result.detailAddress").value("101호"))
        .andExpect(jsonPath("$.result.programName").value("검증용 지원사업"))
        .andExpect(jsonPath("$.result.status").value("SCHEDULING"))
        .andExpect(jsonPath("$.result.requestedFacility").value("출입구 물막이판"))
        .andExpect(jsonPath("$.result.landlordConsentStatus").value("VERIFIED"))
        .andExpect(
            jsonPath("$.result.scheduledConstructionDate").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(
            jsonPath("$.result.scheduledConstructionTime")
                .value(org.hamcrest.Matchers.nullValue()));
    verifyNoInteractions(storage);
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUBMITTED", "REVIEWING", "CONSENT_PENDING", "APPROVED", "REJECTED"})
  void onlySchedulingStateCanUseScheduleView(String value) throws Exception {
    state(value);
    request("schedule")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SUPPORT_DETAIL_409_1"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"SUBMITTED", "REVIEWING", "CONSENT_PENDING", "SCHEDULING", "APPROVED", "REJECTED"})
  void emptyDocumentsWorkInEveryStateWithoutS3(String value) throws Exception {
    state(value);
    request("attachments")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.length()").value(3))
        .andExpect(jsonPath("$.result.supportApplicationId").value(supportId))
        .andExpect(jsonPath("$.result.landlordConsentStatus").value("NOT_REQUIRED"))
        .andExpect(jsonPath("$.result.attachments").isArray())
        .andExpect(jsonPath("$.result.attachments").isEmpty());
    verifyNoInteractions(storage);
  }

  @ParameterizedTest
  @ValueSource(strings = {"application/pdf", "image/png"})
  void actualPdfAndImageMetadataUseExistingStorageUrls(String contentType) throws Exception {
    long id = attach(contentType);
    when(storage.readUrl("private/progress/consent"))
        .thenReturn("https://files.example.test/signed-test-url");
    request("attachments")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.attachments.length()").value(1))
        .andExpect(jsonPath("$.result.attachments[0].length()").value(7))
        .andExpect(jsonPath("$.result.attachments[0].attachmentId").value(id))
        .andExpect(jsonPath("$.result.attachments[0].attachmentType").value("LANDLORD_CONSENT"))
        .andExpect(
            jsonPath("$.result.attachments[0].fileName")
                .value(contentType.equals("application/pdf") ? "임대인 동의서.pdf" : "임대인 동의서.png"))
        .andExpect(jsonPath("$.result.attachments[0].size").value(2048))
        .andExpect(jsonPath("$.result.attachments[0].contentType").value(contentType))
        .andExpect(
            result -> {
              var timestamp =
                  mapper
                      .readTree(result.getResponse().getContentAsString())
                      .get("result")
                      .get("attachments")
                      .get(0)
                      .get("submittedAt")
                      .asString();
              var uploadTime =
                  SupportAttachmentResponse.from(attachments.findById(id).orElseThrow())
                      .submittedAt();
              assertThat(java.time.OffsetDateTime.parse(timestamp)).isEqualTo(uploadTime);
              assertThat(timestamp).endsWith("+09:00");
            })
        .andExpect(
            jsonPath("$.result.attachments[0].fileUrl")
                .value("https://files.example.test/signed-test-url"));
    verify(storage).readUrl("private/progress/consent");
  }

  @ParameterizedTest
  @ValueSource(strings = {"AWS configuration missing", "Credentials unavailable", "Signing failed"})
  void signingFailureIsNotHiddenAsAnEmptyListOrFakeUrl(String reason) throws Exception {
    attach("application/pdf");
    when(storage.readUrl(anyString())).thenThrow(new IllegalStateException(reason));
    request("attachments")
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("SUPPORT_DETAIL_500_1"))
        .andExpect(jsonPath("$.result").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  void failureLogIncludesApplicationAndOriginalStackWithoutKeysOrUrls() throws Exception {
    attach("application/pdf");
    var failure =
        new IllegalStateException(
            "private/progress/consent https://files.example.test/signed?token=secret");
    when(storage.readUrl(anyString())).thenThrow(failure);
    var logger =
        (ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(ResidentSupportDetailService.class);
    var appender =
        new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      request("attachments")
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.code").value("SUPPORT_DETAIL_500_1"));
      assertThat(appender.list).hasSize(1);
      var event = appender.list.getFirst();
      assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.ERROR);
      assertThat(event.getFormattedMessage())
          .contains(
              "supportApplicationId=" + supportId,
              failure.getClass().getName(),
              failure.getStackTrace()[0].toString())
          .doesNotContain("private/progress/consent", "https://", "token=secret");
      assertThat(event.getThrowableProxy()).isNull();
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void realStorageConstructionNeedsNoAwsButUrlIssuanceRequiresConfiguration() {
    var unconfigured = new S3AttachmentStorage("", "");
    assertThatThrownBy(() -> unconfigured.readUrl("private/key"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("AWS_REGION and AWS_S3_BUCKET");
  }

  @ParameterizedTest
  @CsvSource({"PENDING,PENDING", "CONFIRMED,VERIFIED", "NOT_REQUIRED,NOT_REQUIRED"})
  void consentIsActualSavedStateRatherThanAssumedCompleted(String saved, String expected)
      throws Exception {
    state("SCHEDULING");
    jdbc.update("UPDATE support_applications SET landlord_consent_status=?", saved);
    for (String view : List.of("schedule", "attachments"))
      request(view)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result.landlordConsentStatus").value(expected));
  }

  @ParameterizedTest
  @ValueSource(strings = {"schedule", "attachments"})
  void authorizationAlwaysPrecedesDocumentAccessAndStateValidation(String view) throws Exception {
    state("SCHEDULING");
    attach("application/pdf");
    var other =
        users.saveAndFlush(User.resident("progress-other@example.test", "test-hash", "다른주민"));
    String otherToken = jwt.issue(other).accessToken();
    request(view, Long.toString(supportId), null)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("COMMON_401_1"));
    request(view, Long.toString(supportId), "invalid").andExpect(status().isUnauthorized());
    request(view, Long.toString(supportId), token).andExpect(status().isForbidden());
    request(view, Long.toString(supportId), otherToken)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("COMMON_403_1"));
    state("REJECTED");
    request(view, Long.toString(supportId), otherToken).andExpect(status().isForbidden());
    request(view, Long.toString(Long.MAX_VALUE), residentToken)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SUPPORT_404_1"));
    for (String id : List.of("0", "-1", "abc", "1.1", "9223372036854775808"))
      request(view, id, residentToken)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("COMMON_400_3"));
    verifyNoInteractions(storage);
  }

  @ParameterizedTest
  @ValueSource(strings = {"schedule", "attachments"})
  void queriesDoNotWriteOrExposeInternalKeys(String view) throws Exception {
    state("SCHEDULING");
    var tables =
        List.of(
            "support_applications",
            "support_application_status_histories",
            "support_attachments",
            "inspection_applications",
            "support_programs");
    var before = tables.stream().map(t -> jdbc.queryForList("SELECT * FROM " + t)).toList();
    var payload =
        request(view).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    for (String field :
        List.of("objectKey", "internalMemo", "approvedBy", "contact", "preferredConstructionDate"))
      assertThat(mapper.readTree(payload).get("result").has(field)).as(field).isFalse();
    assertThat(tables.stream().map(t -> jdbc.queryForList("SELECT * FROM " + t)).toList())
        .isEqualTo(before);
  }

  @Test
  void swaggerIncludesBothContractsAlongsideExistingUploadAnd132Views() throws Exception {
    var payload =
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var paths = mapper.readTree(payload).get("paths");
    for (String view : List.of("review", "approval", "rejection", "schedule", "attachments")) {
      var op =
          paths.get("/api/resident/support-applications/{supportApplicationId}/" + view).get("get");
      assertThat(op.get("responses").has("200")).isTrue();
      assertThat(
              op.get("responses").get("200").get("content").get("application/json").has("schema"))
          .isTrue();
    }
    assertThat(
            paths
                .get("/api/resident/support-applications/{supportApplicationId}/attachments")
                .has("post"))
        .isTrue();
    assertThat(
            paths
                .get("/api/resident/support-applications/{supportApplicationId}/schedule")
                .get("get")
                .get("responses")
                .has("409"))
        .isTrue();
    assertThat(
            paths
                .get("/api/resident/support-applications/{supportApplicationId}/attachments")
                .get("get")
                .get("responses")
                .has("500"))
        .isTrue();
  }
}
