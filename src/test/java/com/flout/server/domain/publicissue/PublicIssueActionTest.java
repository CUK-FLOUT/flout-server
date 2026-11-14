package com.flout.server.domain.publicissue;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.inspectionapplication.service.AttachmentStorage;
import com.flout.server.domain.publicissue.entity.PublicIssueAction;
import com.flout.server.domain.publicissue.repository.PublicIssueActionRepository;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:public-issue-actions;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false",
      "app.s3.cleanup-interval-millis=3600000"
    })
@AutoConfigureMockMvc
class PublicIssueActionTest {
  private static final String ADDRESS = "경기도 부천시 원미구 길주로 53";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired ObjectMapper mapper;
  @MockitoSpyBean PublicIssueActionRepository actions;
  @MockitoBean AttachmentStorage storage;

  long publicIssueId, reviewId, inspectionId, adminId;
  String token, residentToken;
  Map<String, byte[]> storedObjects;
  byte[] png;

  @BeforeEach
  void setup() {
    for (String table :
        List.of(
            "facility_action_image",
            "facility_action",
            "facility_review",
            "public_facility",
            "public_issues",
            "inspection_result_photos",
            "inspection_vulnerabilities",
            "prevention_recommendations",
            "inspection_results",
            "inspection_status_histories",
            "inspections",
            "application_status_histories",
            "application_inflow_locations",
            "application_attachments",
            "attachment_upload_intents",
            "inspection_applications",
            "prevention_guides",
            "risk_assessments",
            "refresh_tokens",
            "user_consents",
            "users")) jdbc.update("DELETE FROM " + table);

    var resident = users.saveAndFlush(User.resident("action-resident@example.com", "hash", "주민"));
    residentToken = jwt.issue(resident).accessToken();
    jdbc.update(
        "INSERT INTO users(email,password_hash,name,role,jurisdiction_id) "
            + "VALUES('action-admin@example.com','hash','운영자','ADMIN',"
            + "(SELECT jurisdiction_id FROM jurisdictions WHERE name='부천시'))");
    var admin = users.findByEmail("action-admin@example.com").orElseThrow();
    adminId = admin.getId();
    token = jwt.issue(admin).accessToken();

    jdbc.update(
        "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status) "
            + "VALUES(?,?,'101호','HIGH','COMPLETED')",
        resident.getId(),
        ADDRESS);
    long assessmentId =
        jdbc.queryForObject(
            "SELECT assessment_id FROM risk_assessments WHERE user_id=?",
            Long.class,
            resident.getId());
    jdbc.update(
        "INSERT INTO inspection_applications(applicant_name,applicant_user_id,assessment_id,"
            + "receipt_no,address,detail_address,latitude,longitude,contact,occupancy_type,"
            + "flood_experience,existing_facility,status) "
            + "VALUES('김주민',?,?,'ACTION-TEST',?,'101호',37.5000000,126.7600000,"
            + "'010-0000-0000','OWNER','NO',FALSE,'RECEIVED')",
        resident.getId(),
        assessmentId,
        ADDRESS);
    long applicationId =
        jdbc.queryForObject(
            "SELECT application_id FROM inspection_applications WHERE assessment_id=?",
            Long.class,
            assessmentId);
    jdbc.update(
        "INSERT INTO inspections(application_id,status) VALUES(?,'COMPLETED')", applicationId);
    inspectionId =
        jdbc.queryForObject(
            "SELECT inspection_id FROM inspections WHERE application_id=?",
            Long.class,
            applicationId);
    jdbc.update(
        "INSERT INTO public_issues(source_inspection_id,address,latitude,longitude,issue_type,"
            + "description,field_findings,status) "
            + "VALUES(?,?,37.5000000,126.7600000,'BLOCKED_STORM_DRAIN',"
            + "'빗물받이 막힘','현장 확인 필요','RECEIVED')",
        inspectionId,
        ADDRESS);
    publicIssueId =
        jdbc.queryForObject(
            "SELECT public_issue_id FROM public_issues WHERE source_inspection_id=?",
            Long.class,
            inspectionId);
    jdbc.update(
        "INSERT INTO facility_review(user_id,local_problem_id,review_target,inspection_note,"
            + "review_status,review_reason,reviewed_at) "
            + "VALUES(?,?,'빗물받이','현장 확인 필요','UNDER_REVIEW',"
            + "'반복 침수 이력 확인',CURRENT_TIMESTAMP)",
        adminId,
        publicIssueId);
    reviewId = jdbc.queryForObject("SELECT review_id FROM facility_review", Long.class);

    storedObjects = new ConcurrentHashMap<>();
    png = pngBytes();
    doAnswer(
            invocation -> {
              storedObjects.put(invocation.getArgument(0), invocation.getArgument(1));
              return null;
            })
        .when(storage)
        .upload(anyString(), any(byte[].class), anyString());
    doAnswer(
            invocation -> {
              storedObjects.remove(invocation.getArgument(0));
              return null;
            })
        .when(storage)
        .delete(anyString());
    when(storage.readUrl(anyString()))
        .thenAnswer(invocation -> "https://example.invalid/private/" + invocation.getArgument(0));
  }

  private MockMultipartFile jsonPart(Object body) throws Exception {
    return new MockMultipartFile(
        "request", "request.json", "application/json", mapper.writeValueAsBytes(body));
  }

  private ResultActions postAction(long issueId, String accessToken, Object body) throws Exception {
    var request =
        multipart("/api/admin/public-issues/{publicIssueId}/actions", issueId)
            .file(jsonPart(body))
            .file(new MockMultipartFile("files", "완료.png", "image/png", png));
    if (accessToken != null) request.header("Authorization", "Bearer " + accessToken);
    return mvc.perform(request);
  }

  private ResultActions postReview(Object body) throws Exception {
    return mvc.perform(
        post("/api/admin/public-issues/{publicIssueId}/review", publicIssueId)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(body)));
  }

  private Map<String, Object> body(Object description) {
    var body = new LinkedHashMap<String, Object>();
    body.put("actionDescription", description);
    return body;
  }

  private int actionCount() {
    return jdbc.queryForObject("SELECT COUNT(*) FROM facility_action", Integer.class);
  }

  private String issueStatus() {
    return jdbc.queryForObject(
        "SELECT status FROM public_issues WHERE public_issue_id=?", String.class, publicIssueId);
  }

  private String reviewStatus() {
    return jdbc.queryForObject(
        "SELECT review_status FROM facility_review WHERE review_id=?", String.class, reviewId);
  }

  private int imageCount() {
    return jdbc.queryForObject("SELECT COUNT(*) FROM facility_action_image", Integer.class);
  }

  private static byte[] pngBytes() {
    try {
      var output = new ByteArrayOutputStream();
      ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
      return output.toByteArray();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private Map<String, Object> reviewBody(Object status) {
    var body = new LinkedHashMap<String, Object>();
    body.put("reviewTarget", " 빗물받이 ");
    body.put("reviewStatus", status);
    body.put("inspectionNotes", " 빗물받이 막힘 여부와 주변 도로 배수 상태 현장 확인 ");
    body.put("reviewReason", " 인근 신청 3건과 침수 이력 2건 확인 ");
    return body;
  }

  @Test
  void createsAndUpdatesFacilityReviewWithSameId() throws Exception {
    jdbc.update("DELETE FROM facility_review WHERE review_id=?", reviewId);
    postReview(reviewBody("UNDER_REVIEW"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("PUBLIC_ISSUE_200_3"))
        .andExpect(jsonPath("$.result.reviewId").isNumber())
        .andExpect(jsonPath("$.result.publicIssueId").value(publicIssueId))
        .andExpect(jsonPath("$.result.reviewTarget").value("빗물받이"))
        .andExpect(jsonPath("$.result.reviewStatus").value("UNDER_REVIEW"))
        .andExpect(jsonPath("$.result.publicIssueStatus").value("UNDER_REVIEW"))
        .andExpect(jsonPath("$.result.inspectionNotes").value("빗물받이 막힘 여부와 주변 도로 배수 상태 현장 확인"))
        .andExpect(jsonPath("$.result.reviewReason").value("인근 신청 3건과 침수 이력 2건 확인"))
        .andExpect(jsonPath("$.result.reviewerUserId").value(adminId))
        .andExpect(jsonPath("$.result.reviewedAt").value(org.hamcrest.Matchers.endsWith("+09:00")));
    long savedReviewId = jdbc.queryForObject("SELECT review_id FROM facility_review", Long.class);
    var update = reviewBody("INSPECTION_REQUIRED");
    update.put("inspectionNotes", " 현장 재확인 필요 ");
    update.put("reviewReason", null);
    postReview(update)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.reviewId").value(savedReviewId))
        .andExpect(jsonPath("$.result.reviewStatus").value("INSPECTION_REQUIRED"))
        .andExpect(jsonPath("$.result.publicIssueStatus").value("INSPECTION_REQUIRED"))
        .andExpect(jsonPath("$.result.inspectionNotes").value("현장 재확인 필요"))
        .andExpect(jsonPath("$.result.reviewReason").doesNotExist());
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM facility_review", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void validatesFacilityReviewRequest() throws Exception {
    var request = reviewBody("COMPLETED");
    postReview(request)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PUBLIC_ISSUE_400_3"));
    request = reviewBody("UNDER_REVIEW");
    request.put("inspectionNotes", "   ");
    postReview(request).andExpect(status().isBadRequest());
    request = reviewBody("UNDER_REVIEW");
    request.put("reviewTarget", 1);
    postReview(request).andExpect(status().isBadRequest());
    request = reviewBody("UNDER_REVIEW");
    request.put("reviewTarget", "가".repeat(101));
    postReview(request).andExpect(status().isBadRequest());
    jdbc.update(
        "UPDATE public_issues SET status='COMPLETED' WHERE public_issue_id=?", publicIssueId);
    postReview(reviewBody("UNDER_REVIEW"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PUBLIC_ISSUE_409_3"));
  }

  @Test
  void completesActionReviewAndIssueAtomicallyWithImage() throws Exception {
    postAction(publicIssueId, token, body("  빗물받이 퇴적물 제거 및 주변 배수로 정비  "))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.code").value("PUBLIC_ISSUE_200_2"))
        .andExpect(jsonPath("$.result.actionId").isNumber())
        .andExpect(jsonPath("$.result.reviewId").value(reviewId))
        .andExpect(jsonPath("$.result.publicIssueId").value(publicIssueId))
        .andExpect(jsonPath("$.result.operatorUserId").value(adminId))
        .andExpect(jsonPath("$.result.actionDescription").value("빗물받이 퇴적물 제거 및 주변 배수로 정비"))
        .andExpect(jsonPath("$.result.actionStatus").value("COMPLETED"))
        .andExpect(jsonPath("$.result.reviewStatus").value("COMPLETED"))
        .andExpect(jsonPath("$.result.publicIssueStatus").value("COMPLETED"))
        .andExpect(jsonPath("$.result.images.length()").value(1))
        .andExpect(jsonPath("$.result.images[0].imageId").isNumber())
        .andExpect(
            jsonPath("$.result.images[0].imageUrl")
                .value(org.hamcrest.Matchers.startsWith("https://example.invalid/private/")))
        .andExpect(
            jsonPath("$.result.images[0].uploadedAt")
                .value(org.hamcrest.Matchers.endsWith("+09:00")))
        .andExpect(
            jsonPath("$.result.completedAt").value(org.hamcrest.Matchers.endsWith("+09:00")));
    var action = jdbc.queryForMap("SELECT * FROM facility_action");
    assertThat(action.get("ACTION_DESCRIPTION")).isEqualTo("빗물받이 퇴적물 제거 및 주변 배수로 정비");
    assertThat(action.get("ACTION_STATUS").toString()).isEqualTo("COMPLETED");
    assertThat(((Number) action.get("REVIEW_ID")).longValue()).isEqualTo(reviewId);
    assertThat(((Number) action.get("USER_ID")).longValue()).isEqualTo(adminId);
    assertThat(action.get("COMPLETED_AT")).isNotNull();
    assertThat(reviewStatus()).isEqualTo("COMPLETED");
    assertThat(issueStatus()).isEqualTo("COMPLETED");
    assertThat(imageCount()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM inspections WHERE inspection_id=?", String.class, inspectionId))
        .isEqualTo("COMPLETED");
    var keys =
        jdbc.queryForList(
            "SELECT image_url FROM facility_action_image ORDER BY image_id", String.class);
    assertThat(keys).hasSize(1).allMatch(key -> key.startsWith("public-issues/"));
    assertThat(storedObjects.keySet()).containsExactlyInAnyOrderElementsOf(keys);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attachment_upload_intents", Integer.class))
        .isZero();
  }

  static Stream<Object> invalidDescriptions() {
    return Stream.of(null, "", " \t\n ", 123, true, List.of("조치"), Map.of("text", "조치"));
  }

  @ParameterizedTest
  @MethodSource("invalidDescriptions")
  void rejectsMissingBlankAndNonStringDescription(Object value) throws Exception {
    postAction(publicIssueId, token, body(value)).andExpect(status().isBadRequest());
    assertThat(actionCount()).isZero();
    assertThat(imageCount()).isZero();
    assertThat(issueStatus()).isEqualTo("RECEIVED");
  }

  @Test
  void rejectsMissingDescriptionAndLengthOverflowButAcceptsExactLimit() throws Exception {
    postAction(publicIssueId, token, Map.of()).andExpect(status().isBadRequest());
    postAction(publicIssueId, token, body("가".repeat(4001))).andExpect(status().isBadRequest());
    assertThat(actionCount()).isZero();
    postAction(publicIssueId, token, body("  " + "가".repeat(4000) + "  "))
        .andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "SELECT CHAR_LENGTH(action_description) FROM facility_action", Integer.class))
        .isEqualTo(4000);
  }

  @Test
  void requiresSavedFacilityReview() throws Exception {
    jdbc.update("DELETE FROM facility_review WHERE review_id=?", reviewId);
    postAction(publicIssueId, token, body("조치"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PUBLIC_ISSUE_409_2"));
    assertThat(actionCount()).isZero();
    assertThat(issueStatus()).isEqualTo("RECEIVED");
  }

  @Test
  void rejectsInvalidAndMissingIssueIds() throws Exception {
    postAction(0, token, body("조치"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_400_3"));
    postAction(Long.MAX_VALUE, token, body("조치"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PUBLIC_ISSUE_404_1"));
    mvc.perform(
            multipart("/api/admin/public-issues/not-a-number/actions")
                .file(jsonPart(body("조치")))
                .file(new MockMultipartFile("files", "완료.png", "image/png", png))
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest());
    assertThat(actionCount()).isZero();
  }

  @Test
  void requiresAuthenticationAdminRoleAndJurisdiction() throws Exception {
    postAction(publicIssueId, null, body("조치")).andExpect(status().isUnauthorized());
    postAction(publicIssueId, residentToken, body("조치")).andExpect(status().isForbidden());
    jdbc.update(
        "INSERT INTO users(email,password_hash,name,role) "
            + "VALUES('action-nojur@example.com','hash','무관할','ADMIN')");
    String noJurisdiction =
        jwt.issue(users.findByEmail("action-nojur@example.com").orElseThrow()).accessToken();
    postAction(publicIssueId, noJurisdiction, body("조치"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("INSPECTION_403_1"));
    assertThat(actionCount()).isZero();
  }

  @Test
  void rejectsIssueOutsideJurisdictionAndCompletedIssue() throws Exception {
    jdbc.update(
        "UPDATE public_issues SET address='서울특별시 중구 세종대로 1' WHERE public_issue_id=?",
        publicIssueId);
    postAction(publicIssueId, token, body("조치")).andExpect(status().isForbidden());
    jdbc.update(
        "UPDATE public_issues SET address=?,status='COMPLETED' WHERE public_issue_id=?",
        ADDRESS,
        publicIssueId);
    postAction(publicIssueId, token, body("조치"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PUBLIC_ISSUE_409_1"));
    assertThat(actionCount()).isZero();
  }

  @Test
  void rollsBackIssueStatusWhenActionPersistenceFails() throws Exception {
    doAnswer(
            invocation -> {
              PublicIssueAction action = actions.save(invocation.getArgument(0));
              actions.flush();
              throw new IllegalStateException("simulated persistence failure");
            })
        .when(actions)
        .saveAndFlush(any());
    postAction(publicIssueId, token, body("조치")).andExpect(status().isInternalServerError());
    assertThat(actionCount()).isZero();
    assertThat(imageCount()).isZero();
    assertThat(issueStatus()).isEqualTo("RECEIVED");
    assertThat(reviewStatus()).isEqualTo("UNDER_REVIEW");
    assertThat(storedObjects).isEmpty();
  }

  @Test
  void requiresImagesAndDoesNotCompleteWithoutThem() throws Exception {
    mvc.perform(
            multipart("/api/admin/public-issues/{publicIssueId}/actions", publicIssueId)
                .file(jsonPart(body("빗물받이 정비")))
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PUBLIC_ISSUE_400_4"));
    assertThat(actionCount()).isZero();
    assertThat(imageCount()).isZero();
    assertThat(issueStatus()).isEqualTo("RECEIVED");
    assertThat(reviewStatus()).isEqualTo("UNDER_REVIEW");
  }

  @Test
  void rejectsInvalidImageAndDoesNotComplete() throws Exception {
    mvc.perform(
            multipart("/api/admin/public-issues/{publicIssueId}/actions", publicIssueId)
                .file(jsonPart(body("빗물받이 정비")))
                .file(new MockMultipartFile("files", "가짜.png", "image/png", "not-image".getBytes()))
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PUBLIC_ISSUE_400_5"));
    assertThat(actionCount()).isZero();
    assertThat(imageCount()).isZero();
    assertThat(issueStatus()).isEqualTo("RECEIVED");
  }

  @Test
  void rollsBackCompletionWhenS3UploadFails() throws Exception {
    doThrow(
            software.amazon.awssdk.services.s3.model.S3Exception.builder()
                .message("simulated S3 failure")
                .build())
        .when(storage)
        .upload(anyString(), any(byte[].class), anyString());
    postAction(publicIssueId, token, body("빗물받이 정비"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("PUBLIC_ISSUE_500_1"));
    assertThat(actionCount()).isZero();
    assertThat(imageCount()).isZero();
    assertThat(issueStatus()).isEqualTo("RECEIVED");
    assertThat(reviewStatus()).isEqualTo("UNDER_REVIEW");
  }

  @Test
  void documentsCombinedRequestAndRemovesOldEndpoint() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/public-issues/{publicIssueId}/review'].post.responses['200'].content['application/json'].examples.facilityReviewSaved.value.code")
                .value("PUBLIC_ISSUE_200_3"))
        .andExpect(
            jsonPath("$.paths['/api/admin/public-issues/{publicIssueId}/review'].put")
                .doesNotExist())
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/public-issues/{publicIssueId}/actions'].post.requestBody.content['multipart/form-data']")
                .exists())
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/public-issues/{publicIssueId}/actions'].post.responses['200'].content['application/json'].examples.publicIssueActionCompleted.value.code")
                .value("PUBLIC_ISSUE_200_2"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/public-issues/{publicIssueId}/actions'].post.responses['413']")
                .exists())
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/public-issues/{publicIssueId}/actions'].post.responses['404'].description")
                .value("공공시설 문제를 찾을 수 없습니다."))
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/public-issues/{publicIssueId}/actions'].post.responses['409'].description")
                .value("이미 완료된 문제이거나 검토 내용이 저장되지 않았습니다."))
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/public-issues/{publicIssueId}/actions'].post.responses['401']")
                .exists())
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/public-issues/{publicIssueId}/actions'].post.responses['403']")
                .exists())
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/public-issues/{publicIssueId}/actions/{actionId}/images']")
                .doesNotExist());
  }

  @Test
  void rejectsLegacyJsonCompletionRequestWithoutChangingState() throws Exception {
    mvc.perform(
            post("/api/admin/public-issues/{publicIssueId}/actions", publicIssueId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body("빗물받이 정비"))))
        .andExpect(status().isUnsupportedMediaType());

    assertThat(actionCount()).isZero();
    assertThat(imageCount()).isZero();
    assertThat(issueStatus()).isEqualTo("RECEIVED");
    assertThat(reviewStatus()).isEqualTo("UNDER_REVIEW");
  }

  @Test
  void keepsLegacyCompletedActionWithoutPhotosUnchanged() throws Exception {
    jdbc.update(
        "INSERT INTO facility_action(review_id,user_id,action_description,"
            + "action_status,created_at,completed_at) "
            + "VALUES(?,?,'과거 조치','COMPLETED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        reviewId,
        adminId);
    jdbc.update(
        "UPDATE public_issues SET status='COMPLETED' WHERE public_issue_id=?", publicIssueId);
    jdbc.update("UPDATE facility_review SET review_status='COMPLETED' WHERE review_id=?", reviewId);

    postAction(publicIssueId, token, body("중복 완료 시도"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PUBLIC_ISSUE_409_1"));

    assertThat(actionCount()).isEqualTo(1);
    assertThat(imageCount()).isZero();
    assertThat(issueStatus()).isEqualTo("COMPLETED");
    assertThat(reviewStatus()).isEqualTo("COMPLETED");
  }
}
