package com.flout.server.domain.supportapplication;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.inspectionapplication.service.*;
import com.flout.server.domain.supportapplication.entity.SupportAttachment;
import com.flout.server.domain.supportapplication.repository.SupportAttachmentRepository;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.encryption.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:support-documents;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false",
      "app.s3.cleanup-interval-millis=3600000"
    })
@AutoConfigureMockMvc
class SupportAttachmentTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired JwtEncoder encoder;
  @Autowired ObjectMapper mapper;
  @Autowired AttachmentCleanupService cleanup;
  @MockitoBean AttachmentStorage storage;
  @MockitoSpyBean SupportAttachmentRepository attachments;
  Map<String, byte[]> objects;
  long uid, app, support;
  String token;

  @BeforeEach
  void setup() throws Exception {
    for (String t :
        List.of(
            "support_attachments",
            "attachment_upload_intents",
            "support_application_status_histories",
            "support_applications",
            "support_programs",
            "inspection_results",
            "inspection_status_histories",
            "inspections",
            "application_status_histories",
            "inspection_applications",
            "risk_assessments",
            "refresh_tokens",
            "users")) jdbc.update("DELETE FROM " + t);
    var resident =
        users.saveAndFlush(
            User.resident("proof-fixture@example.test", "unused-test-hash", "문서테스트"));
    uid = resident.getId();
    token = jwt.issue(resident).accessToken();
    jdbc.update(
        "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status) VALUES(?,'경기도 부천시 원미구 길주로 210','fixture','HIGH','COMPLETED')",
        uid);
    long risk = jdbc.queryForObject("SELECT assessment_id FROM risk_assessments", Long.class);
    jdbc.update(
        "INSERT INTO inspection_applications(applicant_name,applicant_user_id,assessment_id,receipt_no,address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,status) VALUES('김주민',?,?,'PROOF-TEST','경기도 부천시 원미구 길주로 210',37.5,126.76,'010-0000-0000','TENANT','NO',FALSE,'RECEIVED')",
        uid,
        risk);
    app = jdbc.queryForObject("SELECT application_id FROM inspection_applications", Long.class);
    jdbc.update("INSERT INTO inspections(application_id,status) VALUES(?,'IN_PROGRESS')", app);
    long inspection = jdbc.queryForObject("SELECT inspection_id FROM inspections", Long.class);
    jdbc.update(
        "INSERT INTO inspection_results(inspection_id,result_status,summary,internal_memo,published_at) VALUES(?,'PUBLISHED','Keep source','Keep private',CURRENT_TIMESTAMP)",
        inspection);
    jdbc.update(
        "INSERT INTO inspection_status_histories(inspection_id,previous_status,status,changed_by_user_id,change_source,changed_at) VALUES(?,'RECEIVED','IN_PROGRESS',?,'MANUAL',CURRENT_TIMESTAMP)",
        inspection,
        uid);
    jdbc.update(
        "INSERT INTO application_status_histories(application_id,status) VALUES(?,'RECEIVED')",
        app);
    jdbc.update(
        "INSERT INTO support_programs(name,created_at) VALUES('증빙 테스트 전용',CURRENT_TIMESTAMP)");
    long program = jdbc.queryForObject("SELECT program_id FROM support_programs", Long.class);
    var response =
        mvc.perform(
                post("/api/resident/support-applications")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        mapper.writeValueAsString(
                            Map.of(
                                "applicationId",
                                app,
                                "programId",
                                program,
                                "requestedFacility",
                                "Test barrier",
                                "preferredVisitDate",
                                LocalDate.now().plusDays(7).toString(),
                                "preferredVisitTime",
                                "ANY"))))
            .andExpect(status().isCreated())
            .andReturn();
    support =
        mapper
            .readTree(response.getResponse().getContentAsString())
            .path("result")
            .path("supportApplicationId")
            .asLong();
    objects = new ConcurrentHashMap<>();
    doAnswer(
            i -> {
              objects.put(i.getArgument(0), i.getArgument(1));
              return null;
            })
        .when(storage)
        .upload(anyString(), any(byte[].class), anyString());
    doAnswer(
            i -> {
              objects.remove(i.getArgument(0));
              return null;
            })
        .when(storage)
        .delete(anyString());
  }

  byte[] image(String format) throws Exception {
    var out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, out);
    return out.toByteArray();
  }

  byte[] pdf() throws Exception {
    try (var d = new PDDocument();
        var out = new ByteArrayOutputStream()) {
      d.addPage(new PDPage());
      d.save(out);
      return out.toByteArray();
    }
  }

  MockMultipartFile file(String name, byte[] bytes) {
    return new MockMultipartFile("file", name, "application/octet-stream", bytes);
  }

  ResultActions upload(String access, long id, MockMultipartFile... files) throws Exception {
    var r = multipart("/api/resident/support-applications/" + id + "/attachments");
    if (access != null) r.header("Authorization", "Bearer " + access);
    for (var f : files) r.file(f);
    return mvc.perform(r);
  }

  ResultActions upload(MockMultipartFile file) throws Exception {
    return upload(token, support, file);
  }

  int count() {
    return jdbc.queryForObject("SELECT COUNT(*) FROM support_attachments", Integer.class);
  }

  List<Object> snapshot() {
    return List.of(
        jdbc.queryForList("SELECT * FROM support_applications"),
        jdbc.queryForList("SELECT * FROM inspection_applications"),
        jdbc.queryForList("SELECT * FROM inspections"),
        jdbc.queryForList("SELECT * FROM inspection_results"),
        jdbc.queryForList("SELECT * FROM application_status_histories"),
        jdbc.queryForList("SELECT * FROM inspection_status_histories"),
        jdbc.queryForList("SELECT * FROM support_application_status_histories"));
  }

  void expireIntents() {
    jdbc.update(
        "UPDATE attachment_upload_intents SET created_at=?",
        java.sql.Timestamp.valueOf(LocalDateTime.now().minusHours(2)));
    cleanup.reconcileStale();
  }

  @ParameterizedTest
  @CsvSource({
    "TENANT,LANDLORD_CONSENT,pdf",
    "TENANT,LANDLORD_CONSENT,jpg",
    "TENANT,LANDLORD_CONSENT,jpeg",
    "TENANT,LANDLORD_CONSENT,png"
  })
  void submitsEachDocumentWithoutApprovalOrSourceChanges(
      String occupancy, String type, String extension) throws Exception {
    jdbc.update(
        "UPDATE support_applications SET occupancy_type=? WHERE support_application_id=?",
        occupancy,
        support);
    var before = snapshot();
    byte[] bytes =
        extension.equals("pdf") ? pdf() : image(extension.equals("png") ? "png" : "jpeg");
    String mime =
        extension.equals("pdf")
            ? "application/pdf"
            : extension.equals("png") ? "image/png" : "image/jpeg";
    upload(token, support, file("evidence." + extension, bytes))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value("SUPPORT_201_2"))
        .andExpect(jsonPath("$.result.supportApplicationId").value(support))
        .andExpect(jsonPath("$.result.attachmentType").value(type))
        .andExpect(jsonPath("$.result.submittedAt").value(org.hamcrest.Matchers.endsWith("+09:00")))
        .andExpect(jsonPath("$.result.contentType").value(mime))
        .andExpect(jsonPath("$.result.url").doesNotExist())
        .andExpect(jsonPath("$.result.status").doesNotExist());
    assertThat(count()).isEqualTo(1);
    assertThat(objects).hasSize(1);
    assertThat(objects.values().iterator().next()).isEqualTo(bytes);
    var row = jdbc.queryForMap("SELECT * FROM support_attachments");
    assertThat(row.get("CONTENT_TYPE")).isEqualTo(mime);
    assertThat(row.get("FILE_SIZE")).isEqualTo((long) bytes.length);
    assertThat(row.get("OBJECT_KEY").toString())
        .startsWith("support-applications/" + support + "/attachments/" + type + "/")
        .doesNotContain("evidence");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attachment_upload_intents", Integer.class))
        .isZero();
    assertThat(snapshot()).isEqualTo(before);
    assertThat(
            jdbc.queryForObject(
                "SELECT landlord_consent_status FROM support_applications WHERE support_application_id=?",
                String.class,
                support))
        .isEqualTo("PENDING");
    verify(storage, never()).readUrl(anyString());
  }

  @ParameterizedTest
  @CsvSource({
    "TENANT,LANDLORD_CONSENT,LANDLORD_CONSENT",
    "TENANT,OWNERSHIP_PROOF,LANDLORD_CONSENT",
    "TENANT,UNKNOWN,LANDLORD_CONSENT"
  })
  void ignoresClientTypeAndDeterminesFromDatabase(
      String occupancy, String suppliedType, String expectedType) throws Exception {
    jdbc.update(
        "UPDATE support_applications SET occupancy_type=? WHERE support_application_id=?",
        occupancy,
        support);
    mvc.perform(
            multipart("/api/resident/support-applications/" + support + "/attachments")
                .header("Authorization", "Bearer " + token)
                .param("attachmentType", suppliedType)
                .file(file("doc.pdf", pdf())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.result.attachmentType").value(expectedType));
    assertThat(jdbc.queryForObject("SELECT attachment_type FROM support_attachments", String.class))
        .isEqualTo(expectedType);
  }

  @ParameterizedTest
  @ValueSource(strings = {"OWNER", "ETC"})
  void rejectsNonTenantsBeforeS3OrMetadataCreation(String occupancy) throws Exception {
    jdbc.update(
        "UPDATE support_applications SET occupancy_type=? WHERE support_application_id=?",
        occupancy,
        support);
    var before = snapshot();
    upload(token, support, file("doc.pdf", pdf()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SUPPORT_ATTACHMENT_400_2"))
        .andExpect(jsonPath("$.message").value("세입자만 임대인 동의서를 제출할 수 있습니다."));
    assertThat(count()).isZero();
    verifyNoInteractions(storage);
    assertThat(snapshot()).isEqualTo(before);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attachment_upload_intents", Integer.class))
        .isZero();
  }

  @Test
  void checksAuthenticationRolesOwnershipAndMissingSupport() throws Exception {
    var f = file("doc.pdf", pdf());
    upload(null, support, f).andExpect(status().isUnauthorized());
    upload("invalid-test-token", support, f).andExpect(status().isUnauthorized());
    var claims =
        JwtClaimsSet.builder()
            .issuer("flout")
            .subject(Long.toString(uid))
            .claim("role", "RESIDENT")
            .issuedAt(Instant.now().minusSeconds(120))
            .expiresAt(Instant.now().minusSeconds(90))
            .build();
    String expired =
        encoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    upload(expired, support, f).andExpect(status().isUnauthorized());
    var other =
        users.saveAndFlush(User.resident("proof-other@example.test", "unused-test-hash", "다른테스트"));
    upload(jwt.issue(other).accessToken(), support, f).andExpect(status().isForbidden());
    upload(token, Long.MAX_VALUE, f).andExpect(status().isNotFound());
    jdbc.update("UPDATE users SET role='ADMIN' WHERE user_id=?", uid);
    upload(jwt.issue(users.findById(uid).orElseThrow()).accessToken(), support, f)
        .andExpect(status().isForbidden());
    assertThat(count()).isZero();
    verifyNoInteractions(storage);
  }

  @Test
  void ownerCannotEnableUploadsBySupplyingAttachmentType() throws Exception {
    jdbc.update(
        "UPDATE support_applications SET occupancy_type='OWNER' WHERE support_application_id=?",
        support);
    for (String type : List.of("LANDLORD_CONSENT", "OWNERSHIP_PROOF")) {
      mvc.perform(
              multipart("/api/resident/support-applications/" + support + "/attachments")
                  .header("Authorization", "Bearer " + token)
                  .param("attachmentType", type)
                  .file(file("proof.pdf", pdf())))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("SUPPORT_ATTACHMENT_400_2"));
    }
    assertThat(count()).isZero();
    verifyNoInteractions(storage);
  }

  @Test
  void onlyLandlordConsentRemainsInJavaAndDatabaseEnum() {
    assertThat(com.flout.server.domain.supportapplication.entity.SupportAttachmentType.values())
        .containsExactly(
            com.flout.server.domain.supportapplication.entity.SupportAttachmentType
                .LANDLORD_CONSENT);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO support_attachments(support_application_id,attachment_type,object_key,original_filename,file_size,content_type,submitted_at) VALUES(?,'OWNERSHIP_PROOF','invalid-fixture/no-upload','fixture.pdf',1,'application/pdf',CURRENT_TIMESTAMP)",
                    support))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(count()).isZero();
    verifyNoInteractions(storage);
  }

  @Test
  void ownerCanSubmitAndBeApprovedWithoutAnyDocument() throws Exception {
    jdbc.update(
        "UPDATE inspection_applications SET occupancy_type='OWNER' WHERE application_id=?", app);
    jdbc.update(
        "INSERT INTO support_programs(name,created_at) VALUES('소유자 무서류 검증 전용',CURRENT_TIMESTAMP)");
    long program = jdbc.queryForObject("SELECT MAX(program_id) FROM support_programs", Long.class);
    var submitted =
        mvc.perform(
                post("/api/resident/support-applications")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        mapper.writeValueAsString(
                            Map.of(
                                "applicationId",
                                app,
                                "programId",
                                program,
                                "requestedFacility",
                                "출입구 물막이판",
                                "preferredVisitDate",
                                LocalDate.now().plusDays(7).toString(),
                                "preferredVisitTime",
                                "ANY"))))
            .andExpect(status().isCreated())
            .andReturn();
    long ownerSupport =
        mapper
            .readTree(submitted.getResponse().getContentAsString())
            .path("result")
            .path("supportApplicationId")
            .asLong();
    assertThat(
            jdbc.queryForObject(
                "SELECT landlord_consent_status FROM support_applications WHERE support_application_id=?",
                String.class,
                ownerSupport))
        .isEqualTo("NOT_REQUIRED");
    var operator =
        users.saveAndFlush(User.resident("proof-admin@example.test", "unused-test-hash", "검증운영자"));
    jdbc.update(
        "UPDATE users SET role='ADMIN',jurisdiction_id=(SELECT jurisdiction_id FROM jurisdictions WHERE name='부천시') WHERE user_id=?",
        operator.getId());
    var access = jwt.issue(users.findById(operator.getId()).orElseThrow()).accessToken();
    mvc.perform(
            post("/api/admin/support-applications/" + ownerSupport + "/approve")
                .header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        Map.of(
                            "supportScope",
                            "출입구 물막이판 설치 지원",
                            "applicantHousingReviewed",
                            true,
                            "expertInspectionReviewed",
                            true,
                            "eligibilityDocumentsReviewed",
                            true))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.status").value("APPROVED"));
    assertThat(count()).isZero();
    verifyNoInteractions(storage);
  }

  @Test
  void rejectsInvalidIdMissingFileAndMultipleFiles() throws Exception {
    var f = file("doc.pdf", pdf());
    for (long id : new long[] {0, -1}) upload(token, id, f).andExpect(status().isBadRequest());
    upload(token, support).andExpect(status().isBadRequest());
    upload(token, support, f, f).andExpect(status().isBadRequest());
    upload(
            token,
            support,
            f,
            new MockMultipartFile("another", "extra.pdf", "application/pdf", pdf()))
        .andExpect(status().isBadRequest());
    mvc.perform(
            multipart("/api/resident/support-applications/abc/attachments")
                .header("Authorization", "Bearer " + token)
                .file(f))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(storage);
  }

  @Test
  void rejectsEmptyCorruptMismatchedAndUnsafeFiles() throws Exception {
    for (var f :
        List.of(
            file("empty.pdf", new byte[0]),
            file("fake.pdf", "%PDF-1.7 not a document".getBytes()),
            file("fake.jpg", pdf()),
            file("fake.pdf", image("png")),
            file("bad.gif", image("png")),
            file("../x.pdf", pdf()),
            file("x\\a.pdf", pdf()),
            file("a\n.pdf", pdf()),
            file("a".repeat(256) + ".pdf", pdf()),
            file("", pdf()),
            file("corrupt.png", "not an image".getBytes())))
      upload(f).andExpect(status().isBadRequest());
    verifyNoInteractions(storage);
    assertThat(count()).isZero();
  }

  @Test
  void rejectsPasswordProtectedPdf() throws Exception {
    byte[] bytes;
    try (var d = new PDDocument();
        var out = new ByteArrayOutputStream()) {
      d.addPage(new PDPage());
      d.protect(
          new StandardProtectionPolicy(
              UUID.randomUUID().toString(), UUID.randomUUID().toString(), new AccessPermission()));
      d.save(out);
      bytes = out.toByteArray();
    }
    upload(file("locked.pdf", bytes)).andExpect(status().isBadRequest());
    verifyNoInteractions(storage);
  }

  @Test
  void enforces15MiBIncludingRealStreamAndAcceptsBoundary() throws Exception {
    upload(file("large.png", new byte[15 * 1024 * 1024 + 1]))
        .andExpect(status().isPayloadTooLarge());
    upload(
            new MockMultipartFile(
                "file", "large.png", "image/png", new byte[15 * 1024 * 1024 + 1]) {
              @Override
              public long getSize() {
                return 1;
              }
            })
        .andExpect(status().isPayloadTooLarge());
    var boundary = Arrays.copyOf(image("png"), 15 * 1024 * 1024);
    upload(file("boundary.png", boundary)).andExpect(status().isCreated());
    assertThat(count()).isEqualTo(1);
  }

  @Test
  void rejectsRepeatedSubmissionIncludingDifferentContent() throws Exception {
    var f = file("first.pdf", pdf());
    upload(f).andExpect(status().isCreated());
    upload(f).andExpect(status().isConflict());
    upload(file("second.png", image("png"))).andExpect(status().isConflict());
    assertThat(count()).isEqualTo(1);
    verify(storage, times(1)).upload(anyString(), any(byte[].class), anyString());
  }

  @Test
  void concurrentPrecheckMissStillStoresOnlyOneAndCleansLoser() throws Exception {
    doReturn(false).when(attachments).existsByApplicationIdAndAttachmentType(anyLong(), any());
    var barrier = new CountDownLatch(2);
    doAnswer(
            i -> {
              objects.put(i.getArgument(0), i.getArgument(1));
              barrier.countDown();
              if (!barrier.await(10, TimeUnit.SECONDS))
                throw new IllegalStateException("upload barrier");
              return null;
            })
        .when(storage)
        .upload(anyString(), any(byte[].class), anyString());
    var pool = Executors.newFixedThreadPool(2);
    byte[] bytes = pdf();
    try {
      Callable<Integer> task =
          () -> upload(file("race.pdf", bytes)).andReturn().getResponse().getStatus();
      var a = pool.submit(task);
      var b = pool.submit(task);
      assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 409);
    } finally {
      pool.shutdownNow();
    }
    assertThat(count()).isEqualTo(1);
    assertThat(objects).hasSize(1);
    expireIntents();
    assertThat(objects).hasSize(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attachment_upload_intents", Integer.class))
        .isZero();
  }

  @Test
  void handlesPutTimeoutAndLateCompletionWithDurableCleanup() throws Exception {
    var before = snapshot();
    doAnswer(
            i -> {
              objects.put(i.getArgument(0), i.getArgument(1));
              throw new IllegalStateException("simulated timeout");
            })
        .when(storage)
        .upload(anyString(), any(byte[].class), anyString());
    upload(file("timeout.pdf", pdf())).andExpect(status().isInternalServerError());
    assertThat(objects).isEmpty();
    assertThat(count()).isZero();
    String key =
        jdbc.queryForObject("SELECT object_key FROM attachment_upload_intents", String.class);
    objects.put(key, new byte[] {1});
    expireIntents();
    assertThat(objects).isEmpty();
    assertThat(snapshot()).isEqualTo(before);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attachment_upload_intents", Integer.class))
        .isZero();
  }

  @Test
  void rollsBackMetadataAndCleansFileAfterFlushFailure() throws Exception {
    var before = snapshot();
    doAnswer(
            i -> {
              i.callRealMethod();
              throw new IllegalStateException("simulated persistence failure");
            })
        .when(attachments)
        .saveAndFlush(any(SupportAttachment.class));
    upload(file("db-failure.pdf", pdf())).andExpect(status().isInternalServerError());
    assertThat(count()).isZero();
    assertThat(objects).isEmpty();
    assertThat(snapshot()).isEqualTo(before);
    expireIntents();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attachment_upload_intents", Integer.class))
        .isZero();
  }

  @Test
  void unrelatedIntegrityFailureIsNotReportedAsDuplicate() throws Exception {
    doThrow(new DataIntegrityViolationException("unrelated constraint"))
        .when(attachments)
        .saveAndFlush(any());
    upload(file("failure.pdf", pdf())).andExpect(status().isInternalServerError());
    assertThat(objects).isEmpty();
    assertThat(count()).isZero();
  }

  @Test
  void retriesS3DeletionFailureWithoutLosingIntent() throws Exception {
    doThrow(new IllegalStateException("put failed"))
        .when(storage)
        .upload(anyString(), any(byte[].class), anyString());
    var calls = new AtomicInteger();
    doAnswer(
            i -> {
              if (calls.getAndIncrement() == 0)
                throw new IllegalStateException("delete unavailable");
              objects.remove(i.getArgument(0));
              return null;
            })
        .when(storage)
        .delete(anyString());
    upload(file("retry.pdf", pdf())).andExpect(status().isInternalServerError());
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attachment_upload_intents", Integer.class))
        .isEqualTo(1);
    expireIntents();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attachment_upload_intents", Integer.class))
        .isZero();
  }

  @Test
  void cleanupProtectsCommittedSupportReference() throws Exception {
    upload(file("saved.pdf", pdf())).andExpect(status().isCreated());
    String key = objects.keySet().iterator().next();
    cleanup.register(List.of(key));
    expireIntents();
    assertThat(objects).containsKey(key);
    verify(storage, never()).delete(anyString());
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attachment_upload_intents", Integer.class))
        .isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"ETC", "OWNER"})
  void rechecksOccupancyAfterS3AndCleansRejectedFile(String occupancy) throws Exception {
    doAnswer(
            i -> {
              objects.put(i.getArgument(0), i.getArgument(1));
              jdbc.update(
                  "UPDATE support_applications SET occupancy_type=? WHERE support_application_id=?",
                  occupancy,
                  support);
              return null;
            })
        .when(storage)
        .upload(anyString(), any(byte[].class), anyString());
    upload(file("changed.pdf", pdf())).andExpect(status().isBadRequest());
    assertThat(count()).isZero();
    assertThat(objects).isEmpty();
  }

  @Test
  void documentsMultipartSuccessErrorsAlongsideResidentGet() throws Exception {
    var documentation =
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(
                jsonPath(
                        "$.paths['/api/resident/support-applications/{supportApplicationId}/attachments'].post.requestBody.content['multipart/form-data'].schema.required")
                    .value(org.hamcrest.Matchers.contains("file")))
            .andExpect(
                jsonPath(
                        "$.paths['/api/resident/support-applications/{supportApplicationId}/attachments'].post.responses['201']")
                    .exists())
            .andExpect(
                jsonPath(
                        "$.paths['/api/resident/support-applications/{supportApplicationId}/attachments'].post.responses['409']")
                    .exists())
            .andExpect(
                jsonPath(
                        "$.paths['/api/resident/support-applications/{supportApplicationId}/attachments'].post.responses['413']")
                    .exists())
            .andExpect(
                jsonPath(
                        "$.paths['/api/resident/support-applications/{supportApplicationId}/attachments'].get.responses['200']")
                    .exists());
    String operation =
        "$.paths['/api/resident/support-applications/{supportApplicationId}/attachments'].post";
    String properties = operation + ".requestBody.content['multipart/form-data'].schema.properties";
    documentation
        .andExpect(
            jsonPath(
                    "$.components.schemas.SupportAttachmentResponse.properties.attachmentType.enum")
                .value(org.hamcrest.Matchers.contains("LANDLORD_CONSENT")))
        .andExpect(
            jsonPath(operation + ".description")
                .value(org.hamcrest.Matchers.containsString("TENANT인 경우에만")))
        .andExpect(
            jsonPath(operation + ".description")
                .value(org.hamcrest.Matchers.containsString("OWNER는 별도 증빙서류가 불필요합니다.")))
        .andExpect(
            jsonPath(operation + ".description")
                .value(org.hamcrest.Matchers.containsString("OWNER·ETC의 업로드 요청은 400")))
        .andExpect(
            jsonPath(operation + ".description")
                .value(org.hamcrest.Matchers.containsString("CONFIRMED로 변경하지 않")))
        .andExpect(
            jsonPath(
                    operation
                        + ".responses['201'].content['application/json'].examples.evidenceSubmitted.value.result.attachmentType")
                .value("LANDLORD_CONSENT"))
        .andExpect(
            jsonPath(
                    operation
                        + ".responses['400'].content['application/json'].examples.INVALID_OCCUPANCY.value.message")
                .value("세입자만 임대인 동의서를 제출할 수 있습니다."))
        .andExpect(jsonPath(properties + ".file.type").value("string"))
        .andExpect(jsonPath(properties + ".file.format").value("binary"))
        .andExpect(jsonPath(properties + ".attachmentType").doesNotExist())
        .andExpect(jsonPath(operation + ".parameters[?(@.name == 'attachmentType')]").isEmpty())
        .andExpect(
            jsonPath(
                    operation
                        + ".responses['400'].content['application/json'].examples.INVALID_OCCUPANCY")
                .exists());
    for (String code : List.of("201", "400", "401", "403", "404", "409", "413"))
      documentation
          .andExpect(jsonPath(operation + ".responses['" + code + "'].description").isNotEmpty())
          .andExpect(
              jsonPath(operation + ".responses['" + code + "'].content['application/json']")
                  .exists());
    documentation.andExpect(
        jsonPath(operation + ".responses['413'].description")
            .value("업로드 허용 용량 초과(파일당 최대 15 MiB)."));
    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    mvc.perform(get("/v3/api-docs/swagger-config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").value("/v3/api-docs"));
  }
}
