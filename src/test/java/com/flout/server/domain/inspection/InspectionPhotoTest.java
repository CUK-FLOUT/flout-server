package com.flout.server.domain.inspection;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.inspection.entity.*;
import com.flout.server.domain.inspection.repository.InspectionResultPhotoRepository;
import com.flout.server.domain.inspectionapplication.service.AttachmentCleanupService;
import com.flout.server.domain.inspectionapplication.service.AttachmentStorage;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.*;
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
      "spring.datasource.url=jdbc:h2:mem:inspection-photo;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false",
      "app.s3.cleanup-interval-millis=3600000"
    })
@AutoConfigureMockMvc
class InspectionPhotoTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired AttachmentCleanupService cleanup;
  @MockitoBean AttachmentStorage storage;
  @MockitoSpyBean InspectionResultPhotoRepository photos;
  Map<String, byte[]> objects;
  long inspectionId, applicationId, residentId, adminId;
  String token, residentToken;
  byte[] png, jpeg;

  @BeforeEach
  void setup() throws Exception {
    for (String table :
        List.of(
            "inspection_result_photos",
            "attachment_upload_intents",
            "inspection_vulnerabilities",
            "prevention_recommendations",
            "inspection_results",
            "inspection_status_histories",
            "inspections",
            "application_status_histories",
            "application_attachments",
            "inspection_applications",
            "risk_assessments",
            "refresh_tokens",
            "user_consents",
            "users")) jdbc.update("DELETE FROM " + table);
    var resident = users.saveAndFlush(User.resident("resident-photo@example.com", "hash", "주민"));
    residentId = resident.getId();
    residentToken = jwt.issue(resident).accessToken();
    jdbc.update(
        "INSERT INTO users(email,password_hash,name,role,jurisdiction_id) VALUES('admin-photo@example.com','hash','운영자','ADMIN',(SELECT jurisdiction_id FROM jurisdictions WHERE name='부천시'))");
    var admin = users.findByEmail("admin-photo@example.com").orElseThrow();
    adminId = admin.getId();
    token = jwt.issue(admin).accessToken();
    jdbc.update(
        "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status) VALUES(?,'부천시 원미구 길주로 53','101호','HIGH','COMPLETED')",
        residentId);
    Long assessment =
        jdbc.queryForObject(
            "SELECT assessment_id FROM risk_assessments WHERE user_id=?", Long.class, residentId);
    jdbc.update(
        "INSERT INTO inspection_applications(applicant_user_id,assessment_id,receipt_no,address,detail_address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,status) VALUES(?,?,'PHOTO-TEST','부천시 원미구 길주로 53','101호',37.5,126.76,'010-0000-0000','OWNER','NO',FALSE,'RECEIVED')",
        residentId,
        assessment);
    applicationId =
        jdbc.queryForObject(
            "SELECT application_id FROM inspection_applications WHERE assessment_id=?",
            Long.class,
            assessment);
    jdbc.update(
        "INSERT INTO inspections(application_id,status) VALUES(?,'IN_PROGRESS')", applicationId);
    inspectionId =
        jdbc.queryForObject(
            "SELECT inspection_id FROM inspections WHERE application_id=?",
            Long.class,
            applicationId);
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
        .thenAnswer(a -> "https://example.invalid/private/" + a.getArgument(0));
    png = image("png");
    jpeg = image("jpeg");
  }

  byte[] image(String type) throws Exception {
    var out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), type, out);
    return out.toByteArray();
  }

  MockMultipartFile file(String name, byte[] data) {
    return new MockMultipartFile("files", name, "application/octet-stream", data);
  }

  String path() {
    return "/api/admin/inspections/" + inspectionId + "/attachments";
  }

  org.springframework.test.web.servlet.ResultActions upload(
      String access, MockMultipartFile... files) throws Exception {
    var req = multipart(path());
    for (var f : files) req.file(f);
    if (access != null) req.header("Authorization", "Bearer " + access);
    return mvc.perform(req);
  }

  int count(String table) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
  }

  long draft() {
    return jdbc.queryForObject(
        "SELECT result_id FROM inspection_results WHERE inspection_id=? AND result_status='DRAFT'",
        Long.class,
        inspectionId);
  }

  void unchanged() {
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
  }

  @Test
  void uploadsSingleThenMultipleAppendsAndPreservesDraftContent() throws Exception {
    upload(token, file("입구.jpg", jpeg))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("INSPECTION_200_5"))
        .andExpect(jsonPath("$.result.inspectionId").value(inspectionId))
        .andExpect(jsonPath("$.result.photos[0].photoId").isNumber())
        .andExpect(jsonPath("$.result.photos[0].fileName").value("입구.jpg"))
        .andExpect(jsonPath("$.result.photos[0].url").isString())
        .andExpect(jsonPath("$.result.photos[0].contentType").value("image/jpeg"))
        .andExpect(header().string("Cache-Control", "no-store"));
    long result = draft();
    jdbc.update(
        "UPDATE inspection_results SET summary='유지', internal_memo='비공개', follow_up_type='PUBLIC_AREA' WHERE result_id=?",
        result);
    upload(token, file("창문.PNG", png), file("사진.JPEG", jpeg))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.photos.length()").value(2));
    assertThat(draft()).isEqualTo(result);
    assertThat(count("inspection_results")).isEqualTo(1);
    assertThat(count("inspection_result_photos")).isEqualTo(3);
    assertThat(objects).hasSize(3);
    assertThat(count("attachment_upload_intents")).isZero();
    assertThat(
            jdbc.queryForMap(
                    "SELECT summary,internal_memo,follow_up_type FROM inspection_results WHERE result_id=?",
                    result)
                .values())
        .containsExactly("유지", "비공개", "PUBLIC_AREA");
    var rows =
        jdbc.queryForList("SELECT object_key,file_size,content_type FROM inspection_result_photos");
    for (var row : rows) {
      assertThat(objects).containsKey((String) row.get("object_key"));
      assertThat(((Number) row.get("file_size")).longValue())
          .isEqualTo(objects.get(row.get("object_key")).length);
    }
    unchanged();
    verify(storage, times(3)).readUrl(anyString());
  }

  @Test
  void preservesPublishedResultAndPhotoWhenCreatingNewDraft() throws Exception {
    jdbc.update(
        "INSERT INTO inspection_results(inspection_id,result_status,summary,published_at) VALUES(?,'PUBLISHED','공개본',CURRENT_TIMESTAMP)",
        inspectionId);
    long published =
        jdbc.queryForObject(
            "SELECT result_id FROM inspection_results WHERE inspection_id=?",
            Long.class,
            inspectionId);
    jdbc.update(
        "INSERT INTO inspection_result_photos(result_id,object_key,original_filename,file_size,content_type) VALUES(?,'published-existing','old.png',?,'image/png')",
        published,
        png.length);
    objects.put("published-existing", png);
    var before = jdbc.queryForMap("SELECT * FROM inspection_results WHERE result_id=?", published);
    var photo =
        jdbc.queryForMap("SELECT * FROM inspection_result_photos WHERE result_id=?", published);
    upload(token, file("draft.png", png)).andExpect(status().isOk());
    assertThat(draft()).isNotEqualTo(published);
    assertThat(jdbc.queryForMap("SELECT * FROM inspection_results WHERE result_id=?", published))
        .isEqualTo(before);
    assertThat(
            jdbc.queryForMap("SELECT * FROM inspection_result_photos WHERE result_id=?", published))
        .isEqualTo(photo);
    assertThat(objects.get("published-existing")).isEqualTo(png);
    unchanged();
  }

  @Test
  void uploadedPhotosFollowSameResultWhenFinallyPublished() throws Exception {
    upload(token, file("draft.png", png)).andExpect(status().isOk());
    long result = draft();
    long photo =
        jdbc.queryForObject(
            "SELECT photo_id FROM inspection_result_photos WHERE result_id=?", Long.class, result);
    mvc.perform(
            post("/api/admin/inspections/" + inspectionId + "/result")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(
                    "{\"summary\":\"완료\",\"vulnerabilities\":[{\"type\":\"ENTRANCE\",\"title\":\"입구\",\"expertOpinion\":\"의견\"}],\"recommendedActions\":[{\"title\":\"예방\",\"relatedTarget\":\"ENTRANCE\",\"description\":\"설명\"}],\"followUpType\":\"INDIVIDUAL_HOUSE\",\"internalMemo\":\"내부\"}"))
        .andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "SELECT result_status FROM inspection_results WHERE result_id=?",
                String.class,
                result))
        .isEqualTo("PUBLISHED");
    assertThat(
            jdbc.queryForObject(
                "SELECT result_id FROM inspection_result_photos WHERE photo_id=?",
                Long.class,
                photo))
        .isEqualTo(result);
    verify(storage, times(1)).upload(anyString(), any(), anyString());
  }

  @Test
  void rejectsMissingEmptyUnsupportedAndSpoofedImagesWithoutS3Writes() throws Exception {
    upload(token).andExpect(status().isBadRequest());
    upload(token, file("empty.jpg", new byte[0])).andExpect(status().isBadRequest());
    upload(token, file("a.gif", png)).andExpect(status().isBadRequest());
    upload(token, file("a.jpg", png)).andExpect(status().isBadRequest());
    upload(token, new MockMultipartFile("files", "a.png", "image/png", "not an image".getBytes()))
        .andExpect(status().isBadRequest());
    upload(token, file("../bad.png", png)).andExpect(status().isBadRequest());
    verify(storage, never()).upload(anyString(), any(), anyString());
    assertThat(count("inspection_results")).isZero();
  }

  @Test
  void acceptsExactlyTenMiBRejectsOneByteOverAndKeepsResidentFifteenMiB() throws Exception {
    upload(token, file("ten.png", Arrays.copyOf(png, 10 * 1024 * 1024))).andExpect(status().isOk());
    upload(token, file("large.png", Arrays.copyOf(png, 10 * 1024 * 1024 + 1)))
        .andExpect(status().isPayloadTooLarge());
    mvc.perform(
            multipart("/api/resident/applications/" + applicationId + "/attachments")
                .file(file("resident.png", Arrays.copyOf(png, 15 * 1024 * 1024)))
                .header("Authorization", "Bearer " + residentToken))
        .andExpect(status().isCreated());
    assertThat(count("application_attachments")).isEqualTo(1);
    unchanged();
  }

  @Test
  void rejectsAuthenticationRolesJurisdictionsMissingAndBadIds() throws Exception {
    upload(null, file("a.png", png)).andExpect(status().isUnauthorized());
    upload("invalid", file("a.png", png)).andExpect(status().isUnauthorized());
    upload(residentToken, file("a.png", png)).andExpect(status().isForbidden());
    jdbc.update(
        "INSERT INTO users(email,password_hash,name,role) VALUES('nojur-photo@example.com','hash','무관할','ADMIN')");
    String nojur =
        jwt.issue(users.findByEmail("nojur-photo@example.com").orElseThrow()).accessToken();
    upload(nojur, file("a.png", png)).andExpect(status().isForbidden());
    jdbc.update(
        "UPDATE inspection_applications SET address='서울특별시 중구' WHERE application_id=?",
        applicationId);
    upload(token, file("a.png", png)).andExpect(status().isForbidden());
    for (String id : List.of("0", "-1", "abc", "9223372036854775807")) {
      var r =
          mvc.perform(
              multipart("/api/admin/inspections/" + id + "/attachments")
                  .file(file("a.png", png))
                  .header("Authorization", "Bearer " + token));
      if (id.equals("9223372036854775807")) r.andExpect(status().isNotFound());
      else r.andExpect(status().isBadRequest());
    }
    verify(storage, never()).upload(anyString(), any(), anyString());
  }

  @Test
  void compensatesAllAttemptedObjectsOnPartialS3Failure() throws Exception {
    doAnswer(
            a -> {
              objects.put(a.getArgument(0), a.getArgument(1));
              throw new IllegalStateException("simulated PUT failure");
            })
        .when(storage)
        .upload(anyString(), any(), eq("image/jpeg"));
    upload(token, file("a.png", png), file("b.jpg", jpeg))
        .andExpect(status().isInternalServerError());
    assertThat(objects).isEmpty();
    assertThat(count("inspection_results")).isZero();
    assertThat(count("inspection_result_photos")).isZero();
    assertThat(count("attachment_upload_intents")).isEqualTo(2);
    unchanged();
  }

  @Test
  void rollsBackMetadataAndCleansS3WhenDatabaseFailsAfterFirstPhoto() throws Exception {
    var writes = new java.util.concurrent.atomic.AtomicInteger();
    doAnswer(
            invocation -> {
              if (writes.incrementAndGet() == 2)
                throw new IllegalStateException("simulated DB failure");
              InspectionResultPhoto saved = photos.save(invocation.getArgument(0));
              photos.flush();
              return saved;
            })
        .when(photos)
        .saveAndFlush(any());
    upload(token, file("a.png", png), file("b.png", png))
        .andExpect(status().isInternalServerError());
    assertThat(count("inspection_result_photos")).isZero();
    assertThat(count("inspection_results")).isZero();
    assertThat(objects).isEmpty();
    unchanged();
  }

  @Test
  void cleansUploadsIfSigningFailsBeforeMetadataCommit() throws Exception {
    when(storage.readUrl(anyString()))
        .thenThrow(new IllegalStateException("simulated signing failure"));
    upload(token, file("a.png", png)).andExpect(status().isInternalServerError());
    assertThat(objects).isEmpty();
    assertThat(count("inspection_result_photos")).isZero();
    assertThat(count("inspection_results")).isZero();
  }

  @Test
  void preservesCommittedPhotoWithLeftoverIntentAndRetriesFailedDeletion() throws Exception {
    upload(token, file("a.png", png)).andExpect(status().isOk());
    String key =
        jdbc.queryForObject("SELECT object_key FROM inspection_result_photos", String.class);
    cleanup.register(List.of(key));
    cleanup.reconcile(List.of(key));
    verify(storage, never()).delete(key);
    assertThat(count("attachment_upload_intents")).isZero();
    cleanup.register(List.of("orphan-photo"));
    objects.put("orphan-photo", png);
    doThrow(new IllegalStateException("simulated delete failure"))
        .doAnswer(
            a -> {
              objects.remove(a.getArgument(0));
              return null;
            })
        .when(storage)
        .delete("orphan-photo");
    cleanup.reconcile(List.of("orphan-photo"));
    assertThat(objects).containsKey("orphan-photo");
    cleanup.reconcile(List.of("orphan-photo"));
    assertThat(objects).doesNotContainKey("orphan-photo");
    assertThat(count("attachment_upload_intents")).isEqualTo(1);
  }

  @Test
  void concurrentUploadsAppendToOneDraft() throws Exception {
    var executor = Executors.newFixedThreadPool(2);
    try {
      var gate = new CountDownLatch(1);
      List<Future<Integer>> calls = new ArrayList<>();
      for (int i = 0; i < 2; i++)
        calls.add(
            executor.submit(
                () -> {
                  gate.await();
                  return upload(token, file("a.png", png)).andReturn().getResponse().getStatus();
                }));
      gate.countDown();
      for (var call : calls) assertThat(call.get(20, TimeUnit.SECONDS)).isEqualTo(200);
      assertThat(count("inspection_results")).isEqualTo(1);
      assertThat(count("inspection_result_photos")).isEqualTo(2);
      assertThat(count("attachment_upload_intents")).isZero();
      unchanged();
    } finally {
      executor.shutdownNow();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"RECEIVED", "SCHEDULED", "IN_PROGRESS", "COMPLETED"})
  void preservesEveryInspectionStatus(String state) throws Exception {
    jdbc.update("UPDATE inspections SET status=? WHERE inspection_id=?", state, inspectionId);
    upload(token, file("a.png", png)).andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM inspections WHERE inspection_id=?", String.class, inspectionId))
        .isEqualTo(state);
  }

  @Test
  void documentsMultipartResponses() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/inspections/{inspectionId}/attachments'].post.requestBody.content['multipart/form-data']")
                .exists())
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/inspections/{inspectionId}/attachments'].post.responses['413']")
                .exists())
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/inspections/{inspectionId}/attachments'].post.responses['200']")
                .exists());
  }
}
