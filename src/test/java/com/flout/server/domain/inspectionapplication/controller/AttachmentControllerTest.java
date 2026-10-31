package com.flout.server.domain.inspectionapplication.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.inspectionapplication.dto.request.CreateInspectionApplicationRequest;
import com.flout.server.domain.inspectionapplication.entity.*;
import com.flout.server.domain.inspectionapplication.repository.ApplicationAttachmentRepository;
import com.flout.server.domain.inspectionapplication.repository.InspectionApplicationRepository;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationErrorCode;
import com.flout.server.domain.inspectionapplication.service.AttachmentCleanupService;
import com.flout.server.domain.inspectionapplication.service.AttachmentService;
import com.flout.server.domain.inspectionapplication.service.AttachmentStorage;
import com.flout.server.domain.riskassessment.entity.*;
import com.flout.server.domain.riskassessment.repository.RiskAssessmentRepository;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.response.code.BaseResponseCode;
import com.flout.server.global.response.code.GeneralErrorCode;
import com.flout.server.global.security.JwtService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:attachment-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false"
    })
@AutoConfigureMockMvc
class AttachmentControllerTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired RiskAssessmentRepository assessments;
  @Autowired InspectionApplicationRepository applications;
  @Autowired JwtService jwt;
  @Autowired AttachmentCleanupService cleanup;
  @MockitoBean AttachmentStorage storage;
  @MockitoSpyBean ApplicationAttachmentRepository attachments;
  @MockitoSpyBean AttachmentService attachmentService;
  Long applicationId;
  String token;
  String otherToken;
  byte[] png;
  byte[] jpeg;

  @BeforeEach
  void setup() throws Exception {
    for (String table :
        List.of(
            "attachment_upload_intents",
            "application_attachments",
            "inspection_applications",
            "risk_assessments",
            "users")) jdbc.update("DELETE FROM " + table);
    User user = users.saveAndFlush(User.resident("owner@example.com", "hash", "주민"));
    User other = users.saveAndFlush(User.resident("other@example.com", "hash", "다른 주민"));
    token = jwt.issue(user).accessToken();
    otherToken = jwt.issue(other).accessToken();
    var assessment =
        assessments.saveAndFlush(
            RiskAssessment.completedDemo(user, "경기도 부천시 원미구 길주로 210", "101호", RiskLevel.HIGH));
    var request =
        new CreateInspectionApplicationRequest(
            assessment.getId(),
            null,
            assessment.getAddress(),
            assessment.getDetailAddress(),
            new BigDecimal("37.5"),
            new BigDecimal("126.7"),
            "010-1234-5678",
            OccupancyType.OWNER,
            FloodExperience.NO,
            false,
            null,
            null,
            null,
            null);
    applicationId =
        applications
            .saveAndFlush(InspectionApplication.received(user, assessment, request, "ATTACH-TEST"))
            .getId();
    png = image("png");
    jpeg = image("jpeg");
  }

  private byte[] image(String format) throws Exception {
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, bytes);
    return bytes.toByteArray();
  }

  private MockMultipartFile file(String filename, byte[] bytes) {
    return new MockMultipartFile("files", filename, "application/octet-stream", bytes);
  }

  private org.springframework.test.web.servlet.ResultActions submit(
      String accessToken, long id, MockMultipartFile... files) throws Exception {
    var request = multipart("/api/resident/applications/{applicationId}/attachments", id);
    for (var file : files) request.file(file);
    return mvc.perform(request.header("Authorization", "Bearer " + accessToken));
  }

  private void expectError(
      org.springframework.test.web.servlet.ResultActions response, BaseResponseCode code)
      throws Exception {
    response
        .andExpect(status().is(code.getStatus()))
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.status").value(code.getStatus()))
        .andExpect(jsonPath("$.code").value(code.getCode()))
        .andExpect(jsonPath("$.message").value(code.getMessage()))
        .andExpect(jsonPath("$.result").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.timeStamp").isString());
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 3, 4, 5})
  void acceptsEachAllowedBatchSize(int count) throws Exception {
    MockMultipartFile[] files = new MockMultipartFile[count];
    for (int i = 0; i < count; i++) files[i] = file("photo" + i + ".png", png);
    submit(token, applicationId, files).andExpect(status().isCreated());
    assertThat(attachments.countByApplicationId(applicationId)).isEqualTo(count);
  }

  @Test
  void uploadsOneThenFourAndEnforcesTotalLimit() throws Exception {
    submit(token, applicationId, file("one.png", png))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value("APPLICATION_201_2"))
        .andExpect(jsonPath("$.result[0].contentType").value("image/png"));
    submit(
            token,
            applicationId,
            file("two.jpg", jpeg),
            file("three.jpeg", jpeg),
            file("four.png", png),
            file("five.png", png))
        .andExpect(status().isCreated());
    assertThat(attachments.countByApplicationId(applicationId)).isEqualTo(5);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM application_attachments WHERE object_key LIKE ?",
                Long.class,
                "applications/" + applicationId + "/% ".strip()))
        .isEqualTo(5);
    expectError(
        submit(token, applicationId, file("six.png", png)),
        InspectionApplicationErrorCode.INVALID_ATTACHMENTS);
    verify(storage, times(5)).upload(anyString(), any(), anyString());
  }

  @Test
  void rejectsInvalidFilesBeforeUploadingAnything() throws Exception {
    expectError(
        submit(
            token,
            applicationId,
            file("a.png", png),
            file("b.png", png),
            file("c.png", png),
            file("d.png", png),
            file("e.png", png),
            file("f.png", png)),
        InspectionApplicationErrorCode.INVALID_ATTACHMENTS);
    expectError(
        submit(token, applicationId, file("big.png", new byte[15 * 1024 * 1024 + 1])),
        InspectionApplicationErrorCode.INVALID_ATTACHMENTS);
    expectError(
        submit(token, applicationId, file("a.gif", png)),
        InspectionApplicationErrorCode.INVALID_ATTACHMENTS);
    expectError(
        submit(token, applicationId, file("a.jpg", png)),
        InspectionApplicationErrorCode.INVALID_ATTACHMENTS);
    expectError(
        submit(token, applicationId, file("a.png", new byte[0])),
        InspectionApplicationErrorCode.INVALID_ATTACHMENTS);
    expectError(
        submit(token, applicationId, file("a.png", new byte[] {1, 2, 3})),
        InspectionApplicationErrorCode.INVALID_ATTACHMENTS);
    verifyNoInteractions(storage);
  }

  @Test
  void checksAuthenticationOwnershipAndApplicationExistence() throws Exception {
    expectError(
        mvc.perform(
            multipart("/api/resident/applications/{applicationId}/attachments", applicationId)
                .file(file("a.png", png))),
        GeneralErrorCode.UNAUTHORIZED_ERROR);
    expectError(
        submit("invalid-token", applicationId, file("a.png", png)),
        GeneralErrorCode.INVALID_ACCESS_TOKEN);
    expectError(
        submit(otherToken, applicationId, file("a.png", png)),
        InspectionApplicationErrorCode.APPLICATION_ACCESS_DENIED);
    expectError(
        submit(token, Long.MAX_VALUE, file("a.png", png)),
        InspectionApplicationErrorCode.APPLICATION_NOT_FOUND);
    verifyNoInteractions(storage);
  }

  @Test
  void cleansUpAllAttemptedObjectsAndRollsBackOnS3Failure() throws Exception {
    doNothing()
        .doThrow(new IllegalStateException("S3 failed"))
        .when(storage)
        .upload(anyString(), any(), anyString());
    expectError(
        submit(token, applicationId, file("a.png", png), file("b.png", png)),
        InspectionApplicationErrorCode.ATTACHMENT_UPLOAD_FAILED);
    assertThat(attachments.countByApplicationId(applicationId)).isZero();
    verify(storage, times(2)).delete(anyString());
  }

  @Test
  void cleansUpObjectOnDatabaseFailure() throws Exception {
    doThrow(new IllegalStateException("DB failed")).when(attachments).saveAndFlush(any());
    submit(token, applicationId, file("a.png", png)).andExpect(status().isInternalServerError());
    assertThat(attachments.countByApplicationId(applicationId)).isZero();
    verify(storage).delete(anyString());
  }

  @Test
  void concurrentRequestsCannotExceedFivePhotos() throws Exception {
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var task =
          (java.util.concurrent.Callable<Integer>)
              () -> {
                start.await();
                return submit(
                        token,
                        applicationId,
                        file("a.png", png),
                        file("b.png", png),
                        file("c.png", png))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              };
      var first = executor.submit(task);
      var second = executor.submit(task);
      start.countDown();
      assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 400);
    }
    assertThat(attachments.countByApplicationId(applicationId)).isEqualTo(3);
    verify(storage, atLeast(3)).upload(anyString(), any(), anyString());
  }

  @Test
  void retriesFailedCleanupAndKeepsIntentUntilDeletionSucceeds() throws Exception {
    doThrow(new IllegalStateException("upload failed"))
        .when(storage)
        .upload(anyString(), any(), anyString());
    doThrow(new IllegalStateException("delete failed"))
        .doNothing()
        .when(storage)
        .delete(anyString());
    submit(token, applicationId, file("a.png", png)).andExpect(status().isInternalServerError());
    var keys = jdbc.queryForList("SELECT object_key FROM attachment_upload_intents", String.class);
    assertThat(keys).hasSize(1);
    jdbc.update(
        "UPDATE attachment_upload_intents SET created_at = ?",
        java.time.LocalDateTime.now().minusHours(2));
    cleanup.reconcileStale();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attachment_upload_intents", Long.class))
        .isZero();
    verify(storage, times(2)).delete(keys.getFirst());
  }

  @Test
  void repeatsCleanupAfterGracePeriodForTimedOutUploads() throws Exception {
    doThrow(new IllegalStateException("PUT timeout"))
        .when(storage)
        .upload(anyString(), any(), anyString());
    submit(token, applicationId, file("a.png", png)).andExpect(status().isInternalServerError());
    var keys = jdbc.queryForList("SELECT object_key FROM attachment_upload_intents", String.class);
    assertThat(keys).hasSize(1);
    verify(storage).delete(keys.getFirst());
    jdbc.update(
        "UPDATE attachment_upload_intents SET created_at = ?",
        java.time.LocalDateTime.now().minusHours(2));
    cleanup.reconcileStale();
    verify(storage, times(2)).delete(keys.getFirst());
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attachment_upload_intents", Long.class))
        .isZero();
  }

  @Test
  void idempotencyKeyPreventsDuplicatesAndRejectsDifferentContents() throws Exception {
    for (int i = 0; i < 2; i++) {
      mvc.perform(
              multipart("/api/resident/applications/{applicationId}/attachments", applicationId)
                  .file(file("a.png", png))
                  .header("Idempotency-Key", "retry-key")
                  .header("Authorization", "Bearer " + token))
          .andExpect(status().isCreated());
    }
    expectError(
        mvc.perform(
            multipart("/api/resident/applications/{applicationId}/attachments", applicationId)
                .file(file("different.jpeg", jpeg))
                .header("Idempotency-Key", "retry-key")
                .header("Authorization", "Bearer " + token)),
        InspectionApplicationErrorCode.ATTACHMENT_IDEMPOTENCY_CONFLICT);
    assertThat(attachments.countByApplicationId(applicationId)).isEqualTo(1);
    verify(storage, times(1)).upload(anyString(), any(), anyString());
  }

  @Test
  void mapsMultipartLimitAndMissingPartToCommonErrors() throws Exception {
    expectError(
        mvc.perform(
            multipart("/api/resident/applications/{applicationId}/attachments", applicationId)
                .header("Authorization", "Bearer " + token)),
        GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    doThrow(new org.springframework.web.multipart.MaxUploadSizeExceededException(15L * 1024 * 1024))
        .when(attachmentService)
        .upload(anyLong(), anyLong(), anyList(), any());
    expectError(
        submit(token, applicationId, file("a.png", png)), GeneralErrorCode.PAYLOAD_TOO_LARGE);
  }

  @Test
  void samePhotoWithoutRequestKeyCanBeUploadedAgain() throws Exception {
    submit(token, applicationId, file("a.png", png)).andExpect(status().isCreated());
    submit(token, applicationId, file("a.png", png)).andExpect(status().isCreated());
    assertThat(attachments.countByApplicationId(applicationId)).isEqualTo(2);
  }

  @Test
  void concurrentRetriesWithTheSameKeySaveOnlyOneBatch() throws Exception {
    var uploads = new java.util.concurrent.CyclicBarrier(2);
    doAnswer(
            invocation -> {
              uploads.await(15, TimeUnit.SECONDS);
              return null;
            })
        .when(storage)
        .upload(anyString(), any(), anyString());
    try (var executor = Executors.newFixedThreadPool(2)) {
      var task =
          (java.util.concurrent.Callable<Integer>)
              () ->
                  mvc.perform(
                          multipart(
                                  "/api/resident/applications/{applicationId}/attachments",
                                  applicationId)
                              .file(file("a.png", png))
                              .header("Idempotency-Key", "concurrent-key")
                              .header("Authorization", "Bearer " + token))
                      .andReturn()
                      .getResponse()
                      .getStatus();
      var first = executor.submit(task);
      var second = executor.submit(task);
      assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 201);
    }
    assertThat(attachments.countByApplicationId(applicationId)).isEqualTo(1);
    verify(storage, times(2)).upload(anyString(), any(), anyString());
    verify(storage).delete(anyString());
  }

  @Test
  void s3UploadDoesNotHoldApplicationRowLock() throws Exception {
    var started = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              assertThat(
                      org.springframework.transaction.support.TransactionSynchronizationManager
                          .isActualTransactionActive())
                  .isFalse();
              started.countDown();
              if (!release.await(15, TimeUnit.SECONDS))
                throw new IllegalStateException("upload timed out");
              return null;
            })
        .when(storage)
        .upload(anyString(), any(), anyString());
    try (var executor = Executors.newFixedThreadPool(2)) {
      var uploading =
          executor.submit(
              () ->
                  submit(token, applicationId, file("a.png", png))
                      .andReturn()
                      .getResponse()
                      .getStatus());
      try {
        assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
        var update =
            executor.submit(
                () ->
                    jdbc.update(
                        "UPDATE inspection_applications SET contact = contact WHERE application_id = ?",
                        applicationId));
        assertThat(update.get(5, TimeUnit.SECONDS)).isEqualTo(1);
      } finally {
        release.countDown();
      }
      assertThat(uploading.get(20, TimeUnit.SECONDS)).isEqualTo(201);
    }
  }

  @Test
  void doesNotDeleteCommittedObjectEvenIfIntentRemains() throws Exception {
    submit(token, applicationId, file("a.png", png)).andExpect(status().isCreated());
    String key =
        jdbc.queryForObject("SELECT object_key FROM application_attachments", String.class);
    jdbc.update("INSERT INTO attachment_upload_intents (object_key) VALUES (?)", key);
    cleanup.reconcile(List.of(key));
    verify(storage, never()).delete(key);
    assertThat(attachments.countByApplicationId(applicationId)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attachment_upload_intents", Long.class))
        .isZero();
  }

  @Test
  void documentsMultipartUpload() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                    "$.paths['/api/resident/applications/{applicationId}/attachments'].post.requestBody.content['multipart/form-data'].schema")
                .exists())
        .andExpect(
            jsonPath(
                    "$.paths['/api/resident/applications/{applicationId}/attachments'].post.requestBody.content['multipart/form-data'].schema.properties.files")
                .exists())
        .andExpect(
            jsonPath(
                    "$.paths['/api/resident/applications/{applicationId}/attachments'].post.parameters[?(@.name == 'Idempotency-Key')]")
                .exists())
        .andExpect(
            jsonPath(
                    "$.paths['/api/resident/applications/{applicationId}/attachments'].post.responses['201']")
                .exists())
        .andExpect(
            jsonPath(
                    "$.paths['/api/resident/applications/{applicationId}/attachments'].post.responses['201'].content['application/json'].examples.attachmentsCreated.value.code")
                .value("APPLICATION_201_2"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/resident/applications/{applicationId}/attachments'].post.responses['400'].content['application/json'].examples.invalidAttachments.value.code")
                .value("APPLICATION_400_4"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/resident/applications/{applicationId}/attachments'].post.responses['400'].content['application/json'].examples.missingFiles.value.code")
                .value("COMMON_400_3"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/resident/applications/{applicationId}/attachments'].post.responses['401'].content['application/json'].examples.authenticationRequired.value.code")
                .value("COMMON_401_1"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/resident/applications/{applicationId}/attachments'].post.responses['401'].content['application/json'].examples.invalidOrExpiredToken.value.code")
                .value("COMMON_401_3"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/resident/applications/{applicationId}/attachments'].post.responses['403'].content['application/json'].examples.applicationAccessDenied.value.code")
                .value("APPLICATION_403_5"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/resident/applications/{applicationId}/attachments'].post.responses['404'].content['application/json'].examples.applicationNotFound.value.code")
                .value("APPLICATION_404_2"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/resident/applications/{applicationId}/attachments'].post.responses['409'].content['application/json'].examples.idempotencyConflict.value.code")
                .value("APPLICATION_409_2"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/resident/applications/{applicationId}/attachments'].post.responses['413'].content['application/json'].examples.payloadTooLarge.value.code")
                .value("COMMON_413_1"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/resident/applications/{applicationId}/attachments'].post.responses['500'].content['application/json'].examples.attachmentUploadFailed.value.code")
                .value("APPLICATION_500_2"));
  }
}
