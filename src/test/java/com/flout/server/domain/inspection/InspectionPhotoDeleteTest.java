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
      "spring.datasource.url=jdbc:h2:mem:inspection-photo-delete;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false",
      "app.s3.cleanup-interval-millis=3600000"
    })
@AutoConfigureMockMvc
class InspectionPhotoDeleteTest {
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

  /** Seeds an inspection and operator with isolated photo metadata and cleanup intents. */
  @BeforeEach
  void setup() throws Exception {
    reset(photos);
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
        "INSERT INTO inspection_applications(applicant_name,applicant_user_id,assessment_id,receipt_no,address,detail_address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,status) VALUES('김주민',?,?,'PHOTO-TEST','부천시 원미구 길주로 53','101호',37.5,126.76,'010-0000-0000','OWNER','NO',FALSE,'RECEIVED')",
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

  /** Uploads the draft files through the API with optional authentication. */
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

  long addPhoto() throws Exception {
    upload(token, file("entry.jpg", jpeg)).andExpect(status().isOk());
    return jdbc.queryForObject("SELECT MAX(photo_id) FROM inspection_result_photos", Long.class);
  }

  String key(long id) {
    return jdbc.queryForObject(
        "SELECT object_key FROM inspection_result_photos WHERE photo_id=?", String.class, id);
  }

  /** Exercises deletion with caller-supplied IDs and optional authentication. */
  org.springframework.test.web.servlet.ResultActions remove(
      String inspection, String photo, String access) throws Exception {
    var request = delete("/api/admin/inspections/" + inspection + "/attachments/" + photo);
    if (access != null) request.header("Authorization", "Bearer " + access);
    return mvc.perform(request);
  }

  org.springframework.test.web.servlet.ResultActions remove(long photo) throws Exception {
    return remove(Long.toString(inspectionId), Long.toString(photo), token);
  }

  String publication() {
    return "{\"summary\":\"Completed inspection\",\"vulnerabilities\":[{\"type\":\"ENTRANCE\",\"title\":\"Low entrance\",\"expertOpinion\":\"Water can enter\"}],\"recommendedActions\":[{\"description\":\"Install flood barrier\"}],\"followUpType\":\"INDIVIDUAL_HOUSE\"}";
  }

  org.springframework.test.web.servlet.ResultActions publish() throws Exception {
    return mvc.perform(
        post("/api/admin/inspections/" + inspectionId + "/result")
            .header("Authorization", "Bearer " + token)
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .content(publication()));
  }

  @Test
  void deletesLastDraftPhotoAndKeepsDraftContentAndBusinessState() throws Exception {
    long id = addPhoto();
    String objectKey = key(id);
    jdbc.update(
        "UPDATE inspection_results SET summary='keep summary',internal_memo='keep memo',follow_up_type='INDIVIDUAL_HOUSE' WHERE result_id=?",
        draft());
    remove(id)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("INSPECTION_200_6"))
        .andExpect(jsonPath("$.result.inspectionId").value(inspectionId))
        .andExpect(jsonPath("$.result.photoId").value(id))
        .andExpect(header().string("Cache-Control", "no-store"));
    assertThat(count("inspection_result_photos")).isZero();
    assertThat(objects).doesNotContainKey(objectKey);
    assertThat(jdbc.queryForObject("SELECT summary FROM inspection_results", String.class))
        .isEqualTo("keep summary");
    assertThat(jdbc.queryForObject("SELECT internal_memo FROM inspection_results", String.class))
        .isEqualTo("keep memo");
    assertThat(jdbc.queryForObject("SELECT result_status FROM inspection_results", String.class))
        .isEqualTo("DRAFT");
    unchanged();
    mvc.perform(
            get("/api/admin/inspections/" + inspectionId + "/result")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.photos").isEmpty());
    publish()
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INSPECTION_400_5"));
    remove(id)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INSPECTION_404_2"));
  }

  @Test
  void removesOnlySelectedPhotoAndAllowsReplacingIt() throws Exception {
    long first = addPhoto();
    String firstKey = key(first);
    long second = addPhoto();
    String secondKey = key(second);
    remove(first).andExpect(status().isOk());
    assertThat(objects).containsKey(secondKey).doesNotContainKey(firstKey);
    assertThat(photos.findById(second)).isPresent();
    addPhoto();
    assertThat(count("inspection_result_photos")).isEqualTo(2);
  }

  @Test
  void protectsPublishedPhotosEvenWhenANewDraftExists() throws Exception {
    long publishedPhoto = addPhoto();
    String publicKey = key(publishedPhoto);
    jdbc.update(
        "UPDATE inspection_results SET result_status='PUBLISHED',published_at=CURRENT_TIMESTAMP");
    long draftPhoto = addPhoto();
    remove(publishedPhoto)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INSPECTION_409_5"));
    remove(draftPhoto).andExpect(status().isOk());
    assertThat(photos.findById(publishedPhoto)).isPresent();
    assertThat(objects).containsKey(publicKey);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM inspection_results WHERE result_status='PUBLISHED'",
                Integer.class))
        .isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
  void rejectsInvalidIds(String id) throws Exception {
    remove(id, "1", token)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_400_3"));
    remove(Long.toString(inspectionId), id, token)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_400_3"));
  }

  @Test
  void validatesAuthenticationAndJurisdictionBeforeChangingAnything() throws Exception {
    long id = addPhoto();
    remove(Long.toString(inspectionId), Long.toString(id), null)
        .andExpect(status().isUnauthorized());
    remove(Long.toString(inspectionId), Long.toString(id), "invalid")
        .andExpect(status().isUnauthorized());
    remove(Long.toString(inspectionId), Long.toString(id), residentToken)
        .andExpect(status().isForbidden());
    jdbc.update("UPDATE users SET jurisdiction_id=NULL WHERE user_id=?", adminId);
    remove(id).andExpect(status().isForbidden());
    assertThat(photos.findById(id)).isPresent();
    assertThat(objects).hasSize(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"Seoul", "Bucheon-road", "Gimpo"})
  void rejectsAnotherJurisdiction(String address) throws Exception {
    long id = addPhoto();
    jdbc.update("UPDATE inspection_applications SET address=?", address);
    remove(id)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("INSPECTION_403_1"));
    assertThat(photos.findById(id)).isPresent();
    assertThat(objects).hasSize(1);
  }

  @Test
  void rejectsMissingInspectionPhotoAndPhotosOfOtherInspections() throws Exception {
    long id = addPhoto();
    remove("999999", Long.toString(id), token)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INSPECTION_404_1"));
    remove(999999)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INSPECTION_404_2"));
    jdbc.update(
        "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status) SELECT user_id,address,detail_address,risk_level,status FROM risk_assessments LIMIT 1");
    long assessment =
        jdbc.queryForObject("SELECT MAX(assessment_id) FROM risk_assessments", Long.class);
    jdbc.update(
        "INSERT INTO inspection_applications(applicant_name,applicant_user_id,assessment_id,receipt_no,address,detail_address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,status) SELECT applicant_name,applicant_user_id,?,'OTHER-PHOTO',address,detail_address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,status FROM inspection_applications WHERE application_id=?",
        assessment,
        applicationId);
    long otherApplication =
        jdbc.queryForObject("SELECT MAX(application_id) FROM inspection_applications", Long.class);
    jdbc.update(
        "INSERT INTO inspections(application_id,status) VALUES(?,'IN_PROGRESS')", otherApplication);
    long otherInspection =
        jdbc.queryForObject("SELECT MAX(inspection_id) FROM inspections", Long.class);
    remove(Long.toString(otherInspection), Long.toString(id), token)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INSPECTION_404_2"));
    assertThat(photos.findById(id)).isPresent();
    assertThat(objects).hasSize(1);
  }

  @Test
  void rollbackKeepsFileAndMetadataAndRemovesDeletionIntent() throws Exception {
    long id = addPhoto();
    String objectKey = key(id);
    doThrow(new IllegalStateException("database failure")).when(photos).flush();
    remove(id).andExpect(status().isInternalServerError());
    assertThat(photos.findById(id)).isPresent();
    assertThat(count("attachment_upload_intents")).isZero();
    assertThat(objects).containsKey(objectKey);
    verify(storage, never()).delete(objectKey);
  }

  @Test
  void storageFailureKeepsDurableRetryAndDoesNotRestoreDeletedPhoto() throws Exception {
    long id = addPhoto();
    String objectKey = key(id);
    doThrow(new IllegalStateException("storage failure")).when(storage).delete(objectKey);
    remove(id).andExpect(status().isOk());
    assertThat(photos.findById(id)).isEmpty();
    assertThat(count("attachment_upload_intents")).isEqualTo(1);
    assertThat(objects).containsKey(objectKey);
    doAnswer(
            a -> {
              objects.remove(a.getArgument(0));
              return null;
            })
        .when(storage)
        .delete(objectKey);
    jdbc.update(
        "UPDATE attachment_upload_intents SET created_at=DATEADD('HOUR',-2,CURRENT_TIMESTAMP)");
    cleanup.reconcileStale();
    assertThat(objects).doesNotContainKey(objectKey);
    assertThat(count("attachment_upload_intents")).isZero();
  }

  @Test
  void concurrentDeleteSucceedsOnlyOnce() throws Exception {
    long id = addPhoto();
    var pool = Executors.newFixedThreadPool(2);
    var start = new CountDownLatch(1);
    try {
      Callable<Integer> task =
          () -> {
            start.await();
            return remove(id).andReturn().getResponse().getStatus();
          };
      var a = pool.submit(task);
      var b = pool.submit(task);
      start.countDown();
      assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 404);
      assertThat(count("inspection_result_photos")).isZero();
    } finally {
      pool.shutdownNow();
    }
  }

  /**
   * Races publication against deletion of the only draft photo to verify the publication invariant.
   */
  @Test
  void concurrentPublicationCannotLoseItsRequiredPhoto() throws Exception {
    long id = addPhoto();
    String objectKey = key(id);
    var pool = Executors.newFixedThreadPool(2);
    var start = new CountDownLatch(1);
    try {
      var deletion =
          pool.submit(
              () -> {
                start.await();
                return remove(id).andReturn().getResponse().getStatus();
              });
      var publication =
          pool.submit(
              () -> {
                start.await();
                return publish().andReturn().getResponse().getStatus();
              });
      start.countDown();
      int d = deletion.get(30, TimeUnit.SECONDS), p = publication.get(30, TimeUnit.SECONDS);
      assertThat(List.of(d, p)).isIn(List.of(200, 400), List.of(409, 200));
      if (p == 200) {
        assertThat(photos.findById(id)).isPresent();
        assertThat(objects).containsKey(objectKey);
      } else {
        assertThat(photos.findById(id)).isEmpty();
        assertThat(objects).doesNotContainKey(objectKey);
      }
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void swaggerDocumentsDeleteAndItsResponses() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/inspections/{inspectionId}/attachments/{photoId}'].delete")
                .exists())
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/inspections/{inspectionId}/attachments/{photoId}'].delete.responses['200']")
                .exists())
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/inspections/{inspectionId}/attachments/{photoId}'].delete.responses['401']")
                .exists());
  }
}
