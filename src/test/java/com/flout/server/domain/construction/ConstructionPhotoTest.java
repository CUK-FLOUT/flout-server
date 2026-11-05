package com.flout.server.domain.construction;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.construction.entity.ConstructionPhoto;
import com.flout.server.domain.construction.repository.ConstructionPhotoRepository;
import com.flout.server.domain.inspectionapplication.service.AttachmentCleanupService;
import com.flout.server.domain.inspectionapplication.service.AttachmentStorage;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:construction-photo;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false",
      "app.s3.cleanup-interval-millis=3600000"
    })
@AutoConfigureMockMvc
class ConstructionPhotoTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired ObjectMapper mapper;
  @Autowired AttachmentCleanupService cleanup;
  @MockitoBean AttachmentStorage storage;
  @MockitoSpyBean ConstructionPhotoRepository photos;
  String token, residentToken;
  long supportId, adminId;
  final long constructionId = 900;
  Map<String, byte[]> objects;
  byte[] png, jpeg;

  @BeforeEach
  void setup() throws Exception {
    reset(photos);
    for (var table :
        List.of(
            "construction_photos",
            "attachment_upload_intents",
            "constructions",
            "support_application_status_histories",
            "support_applications",
            "support_programs",
            "inspection_applications",
            "risk_assessments",
            "refresh_tokens",
            "users")) jdbc.update("DELETE FROM " + table);
    var resident =
        users.saveAndFlush(User.resident("resident@construction-photo.test", "unused", "김주민"));
    residentToken = jwt.issue(resident).accessToken();
    var admin = users.saveAndFlush(User.resident("admin@construction-photo.test", "unused", "박담당"));
    adminId = admin.getId();
    jdbc.update(
        "UPDATE users SET role='ADMIN',jurisdiction_id=(SELECT jurisdiction_id FROM jurisdictions WHERE name='부천시') WHERE user_id=?",
        adminId);
    token = jwt.issue(users.findById(adminId).orElseThrow()).accessToken();
    jdbc.update(
        "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status) VALUES(?,'경기도 부천시 원미구 길주로 210','101호','HIGH','COMPLETED')",
        resident.getId());
    long risk = jdbc.queryForObject("SELECT assessment_id FROM risk_assessments", Long.class);
    jdbc.update(
        "INSERT INTO inspection_applications(applicant_name,applicant_user_id,assessment_id,receipt_no,address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,status) VALUES('김주민',?,?,'PHOTO-CONSTRUCTION','경기도 부천시 원미구 길주로 210',37.5,126.76,'010-1234-5678','OWNER','NO',FALSE,'RECEIVED')",
        resident.getId(),
        risk);
    long application =
        jdbc.queryForObject("SELECT application_id FROM inspection_applications", Long.class);
    jdbc.update(
        "INSERT INTO support_programs(name,created_at) VALUES('물막이판 지원',CURRENT_TIMESTAMP)");
    long program = jdbc.queryForObject("SELECT program_id FROM support_programs", Long.class);
    jdbc.update(
        "INSERT INTO support_applications(application_id,program_id,applicant_user_id,requested_facility,applicant_name,contact,address,occupancy_type,preferred_visit_date,preferred_visit_time,status,submitted_at,landlord_consent_status) VALUES(?,?,?,'물막이판','김주민','010-1234-5678','경기도 부천시 원미구 길주로 210','OWNER',CURRENT_DATE,'ANY','APPROVED',CURRENT_TIMESTAMP,'NOT_REQUIRED')",
        application,
        program,
        resident.getId());
    supportId =
        jdbc.queryForObject("SELECT support_application_id FROM support_applications", Long.class);
    jdbc.update(
        "INSERT INTO constructions(construction_id,support_application_id,status,created_at,updated_at) VALUES(?,?,'SCHEDULED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        constructionId,
        supportId);
    objects = new ConcurrentHashMap<>();
    doAnswer(
            a -> {
              objects.put(a.getArgument(0), a.getArgument(1));
              return null;
            })
        .when(storage)
        .upload(anyString(), any(byte[].class), anyString());
    doAnswer(
            a -> {
              objects.remove(a.getArgument(0));
              return null;
            })
        .when(storage)
        .delete(anyString());
    when(storage.readUrl(anyString()))
        .thenAnswer(a -> "https://private.example.test/" + a.getArgument(0));
    png = image("png");
    jpeg = image("jpeg");
  }

  byte[] image(String format) throws Exception {
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, bytes);
    return bytes.toByteArray();
  }

  MockMultipartFile file(String name, byte[] bytes) {
    return new MockMultipartFile("files", name, "application/octet-stream", bytes);
  }

  ResultActions upload(String id, String access, MockMultipartFile... files) throws Exception {
    var request = multipart("/api/admin/constructions/" + id + "/photos");
    for (var file : files) request.file(file);
    if (access != null) request.header("Authorization", "Bearer " + access);
    return mvc.perform(request);
  }

  ResultActions upload(MockMultipartFile... files) throws Exception {
    return upload(Long.toString(constructionId), token, files);
  }

  int count(String table) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
  }

  @Test
  void requiresAtLeastOneFileAndRejectsEmptyParts() throws Exception {
    upload().andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON_400_3"));
    upload(file("empty.png", new byte[0]))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CONSTRUCTION_400_2"));
    upload(file("valid.png", png), file("empty.png", new byte[0]))
        .andExpect(status().isBadRequest());
    verify(storage, never()).upload(anyString(), any(), anyString());
    assertThat(count("construction_photos")).isZero();
  }

  @Test
  void uploadsSingleAndMultiplePhotosAndPreservesBusinessData() throws Exception {
    var construction = jdbc.queryForList("SELECT * FROM constructions");
    var support = jdbc.queryForList("SELECT * FROM support_applications");
    upload(file("완료.jpg", jpeg))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.code").value("CONSTRUCTION_200_2"))
        .andExpect(jsonPath("$.result.constructionId").value(constructionId))
        .andExpect(jsonPath("$.result.photos[0].photoId").isNumber())
        .andExpect(jsonPath("$.result.photos[0].fileName").value("완료.jpg"))
        .andExpect(jsonPath("$.result.photos[0].size").value(jpeg.length))
        .andExpect(jsonPath("$.result.photos[0].contentType").value("image/jpeg"))
        .andExpect(jsonPath("$.result.photos[0].url").isString())
        .andExpect(jsonPath("$.result.photos[0].objectKey").doesNotExist());
    upload(file("완료.jpg", jpeg), file("배수구.png", png))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.photos.length()").value(2));
    assertThat(count("construction_photos")).isEqualTo(3);
    assertThat(objects).hasSize(3);
    assertThat(count("attachment_upload_intents")).isZero();
    assertThat(jdbc.queryForList("SELECT * FROM constructions")).isEqualTo(construction);
    assertThat(jdbc.queryForList("SELECT * FROM support_applications")).isEqualTo(support);
  }

  @ParameterizedTest
  @ValueSource(strings = {"PENDING", "SCHEDULED", "IN_PROGRESS"})
  void allowsUploadsBeforeWholeCompletion(String state) throws Exception {
    jdbc.update("UPDATE constructions SET status=?", state);
    upload(file("현장.png", png)).andExpect(status().isOk());
    assertThat(jdbc.queryForObject("SELECT status FROM constructions", String.class))
        .isEqualTo(state);
  }

  @ParameterizedTest
  @ValueSource(strings = {"file.pdf", "file.gif", "../a.png", "a\\b.png", ""})
  void rejectsInvalidNamesAndExtensions(String name) throws Exception {
    upload(file(name, png))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CONSTRUCTION_400_3"));
    verify(storage, never()).upload(anyString(), any(), anyString());
  }

  @Test
  void rejectsFakeImagesAndMismatchedExtensions() throws Exception {
    upload(file("fake.jpg", new byte[] {1, 2, 3})).andExpect(status().isBadRequest());
    upload(file("wrong.jpg", png)).andExpect(status().isBadRequest());
    upload(file("wrong.png", jpeg)).andExpect(status().isBadRequest());
    verify(storage, never()).upload(anyString(), any(), anyString());
  }

  @Test
  void rejectsFilesLargerThanTenMiB() throws Exception {
    upload(file("large.png", new byte[10 * 1024 * 1024 + 1]))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.code").value("COMMON_413_1"));
    verify(storage, never()).upload(anyString(), any(), anyString());
  }

  @Test
  void rejectsCompletedOrUnapprovedConstructionWithoutUploading() throws Exception {
    jdbc.update("UPDATE constructions SET status='COMPLETED'");
    upload(file("a.png", png))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONSTRUCTION_409_3"));
    jdbc.update("UPDATE constructions SET status='SCHEDULED'");
    jdbc.update("UPDATE support_applications SET status='REJECTED'");
    upload(file("a.png", png))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONSTRUCTION_409_1"));
    verify(storage, never()).upload(anyString(), any(), anyString());
  }

  @Test
  void rejectsUnauthenticatedInvalidTokenAndResident() throws Exception {
    upload(Long.toString(constructionId), null, file("a.png", png))
        .andExpect(status().isUnauthorized());
    upload(Long.toString(constructionId), "invalid", file("a.png", png))
        .andExpect(status().isUnauthorized());
    upload(Long.toString(constructionId), residentToken, file("a.png", png))
        .andExpect(status().isForbidden());
    verify(storage, never()).upload(anyString(), any(), anyString());
  }

  @Test
  void rejectsOtherJurisdictionAndOperatorWithoutJurisdiction() throws Exception {
    jdbc.update("UPDATE support_applications SET address='서울특별시'");
    upload(file("a.png", png)).andExpect(status().isForbidden());
    jdbc.update("UPDATE users SET jurisdiction_id=NULL WHERE user_id=?", adminId);
    upload(file("a.png", png)).andExpect(status().isForbidden());
    verify(storage, never()).upload(anyString(), any(), anyString());
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
  void rejectsInvalidIds(String id) throws Exception {
    upload(id, token, file("a.png", png)).andExpect(status().isBadRequest());
    verify(storage, never()).upload(anyString(), any(), anyString());
  }

  @Test
  void rejectsMissingConstruction() throws Exception {
    upload("99999", token, file("a.png", png)).andExpect(status().isNotFound());
    verify(storage, never()).upload(anyString(), any(), anyString());
  }

  @Test
  void partialStorageFailureCleansEveryAttemptedObject() throws Exception {
    doAnswer(
            a -> {
              objects.put(a.getArgument(0), a.getArgument(1));
              throw new IllegalStateException("put failed");
            })
        .when(storage)
        .upload(anyString(), any(), eq("image/jpeg"));
    upload(file("a.png", png), file("b.jpg", jpeg))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("CONSTRUCTION_500_1"));
    assertThat(objects).isEmpty();
    assertThat(count("construction_photos")).isZero();
    assertThat(count("attachment_upload_intents")).isEqualTo(2);
  }

  @Test
  void signingFailureCleansUploadedObjectsBeforeMetadataSave() throws Exception {
    when(storage.readUrl(anyString())).thenThrow(new IllegalStateException("signing failed"));
    upload(file("a.png", png)).andExpect(status().isInternalServerError());
    assertThat(objects).isEmpty();
    assertThat(count("construction_photos")).isZero();
  }

  @Test
  void metadataFailureRollsBackAllPhotosAndCleansStorage() throws Exception {
    var writes = new AtomicInteger();
    doAnswer(
            a -> {
              if (writes.incrementAndGet() == 2) throw new IllegalStateException("db failure");
              ConstructionPhoto saved = photos.save(a.getArgument(0));
              photos.flush();
              return saved;
            })
        .when(photos)
        .saveAndFlush(any());
    upload(file("a.png", png), file("b.png", png)).andExpect(status().isInternalServerError());
    assertThat(count("construction_photos")).isZero();
    assertThat(objects).isEmpty();
  }

  @Test
  void rechecksCompletionAfterTheExternalUpload() throws Exception {
    doAnswer(
            a -> {
              objects.put(a.getArgument(0), a.getArgument(1));
              jdbc.update("UPDATE constructions SET status='COMPLETED'");
              return null;
            })
        .when(storage)
        .upload(anyString(), any(), anyString());
    upload(file("a.png", png)).andExpect(status().isConflict());
    assertThat(count("construction_photos")).isZero();
    assertThat(objects).isEmpty();
    assertThat(jdbc.queryForObject("SELECT status FROM constructions", String.class))
        .isEqualTo("COMPLETED");
  }

  @Test
  void cleanupKeepsCommittedPhotosAndRetriesFailedDeletes() throws Exception {
    upload(file("a.png", png)).andExpect(status().isOk());
    String key = jdbc.queryForObject("SELECT object_key FROM construction_photos", String.class);
    cleanup.register(List.of(key));
    cleanup.reconcile(List.of(key));
    assertThat(objects).containsKey(key);
    verify(storage, never()).delete(key);
    cleanup.register(List.of("orphan-construction-photo"));
    objects.put("orphan-construction-photo", png);
    doThrow(new IllegalStateException("delete failed"))
        .doAnswer(
            a -> {
              objects.remove(a.getArgument(0));
              return null;
            })
        .when(storage)
        .delete("orphan-construction-photo");
    cleanup.reconcile(List.of("orphan-construction-photo"));
    assertThat(objects).containsKey("orphan-construction-photo");
    cleanup.reconcile(List.of("orphan-construction-photo"));
    assertThat(objects).doesNotContainKey("orphan-construction-photo");
  }

  @Test
  void concurrentUploadsAppendWithoutLosingExistingPhotos() throws Exception {
    var pool = Executors.newFixedThreadPool(2);
    var start = new CountDownLatch(1);
    try {
      var a =
          pool.submit(
              () -> {
                start.await();
                return upload(file("a.png", png)).andReturn().getResponse().getStatus();
              });
      var b =
          pool.submit(
              () -> {
                start.await();
                return upload(file("b.png", png)).andReturn().getResponse().getStatus();
              });
      start.countDown();
      assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)))
          .containsExactly(200, 200);
      assertThat(count("construction_photos")).isEqualTo(2);
      assertThat(count("attachment_upload_intents")).isZero();
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void swaggerRequiresFilesAndDocumentsMultipartUpload() throws Exception {
    var response =
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse();
    var doc = mapper.readTree(response.getContentAsString());
    var op = doc.get("paths").get("/api/admin/constructions/{constructionId}/photos").get("post");
    assertThat(op.get("summary").asString()).isEqualTo("시공 사진 업로드");
    var body = op.get("requestBody");
    assertThat(body.get("required").asBoolean()).isTrue();
    var schema = body.get("content").get("multipart/form-data").get("schema");
    if (schema.has("$ref"))
      schema =
          doc.get("components")
              .get("schemas")
              .get(schema.get("$ref").asString().substring("#/components/schemas/".length()));
    assertThat(schema.get("required").toString()).contains("files");
    for (var code : List.of("200", "400", "401", "403", "404", "409", "413", "500"))
      assertThat(op.get("responses").has(code)).as("Swagger HTTP %s", code).isTrue();
  }
}
