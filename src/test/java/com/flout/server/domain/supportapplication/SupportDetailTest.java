package com.flout.server.domain.supportapplication;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.inspectionapplication.service.AttachmentStorage;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
      "spring.datasource.url=jdbc:h2:mem:support-detail;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false",
      "app.s3.cleanup-interval-millis=3600000"
    })
@AutoConfigureMockMvc
class SupportDetailTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired ObjectMapper mapper;
  @MockitoBean AttachmentStorage storage;
  long supportId, adminId, residentId;
  String token, residentToken;

  /** Seeds the support and inspection data used to verify the operator detail views. */
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
        "INSERT INTO support_applications(application_id,program_id,applicant_user_id,requested_facility,applicant_name,contact,address,occupancy_type,preferred_visit_date,preferred_visit_time,status,submitted_at,landlord_consent_status) VALUES(?,?,?,'출입구 물막이판','테스트주민','010-0000-0000','경기도 부천시 원미구 길주로 210','OWNER',CURRENT_DATE,'ANY','SUBMITTED',CURRENT_TIMESTAMP,'NOT_REQUIRED')",
        source,
        program,
        residentId);
    supportId =
        jdbc.queryForObject("SELECT support_application_id FROM support_applications", Long.class);
  }

  ResultActions detail(String id, String access) throws Exception {
    var request = get("/api/admin/support-applications/" + id);
    if (access != null) request.header("Authorization", "Bearer " + access);
    return mvc.perform(request);
  }

  ResultActions detail() throws Exception {
    return detail(Long.toString(supportId), token);
  }

  long inspectionId() {
    return jdbc.queryForObject("SELECT inspection_id FROM inspections", Long.class);
  }

  void publish() {
    jdbc.update(
        "UPDATE inspection_results SET result_status='PUBLISHED',published_at=CURRENT_TIMESTAMP");
  }

  void attach() {
    jdbc.update(
        "INSERT INTO support_attachments(support_application_id,attachment_type,object_key,original_filename,file_size,content_type,submitted_at) VALUES(?,'LANDLORD_CONSENT','private/consent','임대인 동의서.pdf',123,'application/pdf',CURRENT_TIMESTAMP)",
        supportId);
  }

  @Test
  void mapsSnapshotFieldsAndKeepsInspectionAndApplicationIdsDistinct() throws Exception {
    jdbc.update(
        "UPDATE inspection_applications SET applicant_name='변경된 원본',contact='010-9999-9999',address='서울특별시',detail_address='101호',occupancy_type='TENANT',request_reason='점검 전용 요청 사유'");
    jdbc.update(
        "UPDATE support_applications SET applicant_name='김주민',contact='010-1234-0002',occupancy_type='TENANT',preferred_visit_date='2026-10-21',preferred_visit_time='MORNING',landlord_consent_status='PENDING'");
    long sourceId =
        jdbc.queryForObject("SELECT application_id FROM inspection_applications", Long.class);
    detail()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("COMMON_200"))
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.status").value(200))
        .andExpect(jsonPath("$.timeStamp").isString())
        .andExpect(jsonPath("$.result.length()").value(19))
        .andExpect(jsonPath("$.result.supportApplicationId").value(supportId))
        .andExpect(
            jsonPath("$.result.receiptNo").value(String.format(Locale.ROOT, "%06d", sourceId)))
        .andExpect(jsonPath("$.result.applicantName").value("김주민"))
        .andExpect(jsonPath("$.result.contact").value("010-1234-0002"))
        .andExpect(jsonPath("$.result.address").value("경기도 부천시 원미구 길주로 210"))
        .andExpect(jsonPath("$.result.detailAddress").value("101호"))
        .andExpect(jsonPath("$.result.housingType").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.result.residentRequest").value("점검 전용 요청 사유"))
        .andExpect(jsonPath("$.result.occupancyType").value("TENANT"))
        .andExpect(jsonPath("$.result.requestedFacility").value("출입구 물막이판"))
        .andExpect(jsonPath("$.result.preferredConstructionDate").value("2026-10-21"))
        .andExpect(jsonPath("$.result.preferredTimeSlot").value("MORNING"))
        .andExpect(jsonPath("$.result.inspectionId").value(inspectionId()))
        .andExpect(jsonPath("$.result.inspectionStatus").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.result.inspectionResultPublished").value(false))
        .andExpect(jsonPath("$.result.landlordConsentStatus").value("PENDING"))
        .andExpect(jsonPath("$.result.status").value("SUBMITTED"));
  }

  @Test
  void publishedRecommendationsKeepOrderAndExcludeDrafts() throws Exception {
    publish();
    long result = jdbc.queryForObject("SELECT result_id FROM inspection_results", Long.class);
    jdbc.update(
        "INSERT INTO prevention_recommendations(result_id,description,sort_order) VALUES(?,'두 번째',2),(?,'첫 번째',0)",
        result,
        result);
    jdbc.update(
        "INSERT INTO inspection_results(inspection_id,result_status) VALUES(?,'DRAFT')",
        inspectionId());
    long draft =
        jdbc.queryForObject(
            "SELECT result_id FROM inspection_results WHERE result_status='DRAFT'", Long.class);
    jdbc.update(
        "INSERT INTO prevention_recommendations(result_id,description,sort_order) VALUES(?,'공개하지 않은 초안',0)",
        draft);
    detail()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.recommendedActions[0]").value("첫 번째"))
        .andExpect(jsonPath("$.result.recommendedActions[1]").value("두 번째"))
        .andExpect(jsonPath("$.result.recommendedActions.length()").value(2))
        .andExpect(jsonPath("$.result.inspectionResultPublished").value(true))
        .andExpect(jsonPath("$.result.inspectionStatus").value("IN_PROGRESS"));
  }

  @Test
  void completedInspectionDoesNotImplyPublicationAndEmptyCollectionsStayArrays() throws Exception {
    jdbc.update("UPDATE inspections SET status='COMPLETED'");
    long draft = jdbc.queryForObject("SELECT result_id FROM inspection_results", Long.class);
    jdbc.update(
        "INSERT INTO prevention_recommendations(result_id,description,sort_order) VALUES(?,'비공개 초안',0)",
        draft);
    detail()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.inspectionStatus").value("COMPLETED"))
        .andExpect(jsonPath("$.result.inspectionResultPublished").value(false))
        .andExpect(jsonPath("$.result.recommendedActions").isEmpty())
        .andExpect(jsonPath("$.result.attachments").isEmpty());
    verifyNoInteractions(storage);
  }

  @Test
  void missingInspectionIsNullableAndDoesNotAlterTheApplication() throws Exception {
    jdbc.update("DELETE FROM inspection_results");
    jdbc.update("DELETE FROM inspections");
    jdbc.update("UPDATE inspection_applications SET detail_address=NULL");
    var before = jdbc.queryForList("SELECT * FROM support_applications");
    detail()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.inspectionId").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.result.inspectionStatus").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.result.detailAddress").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.result.inspectionResultPublished").value(false))
        .andExpect(jsonPath("$.result.recommendedActions").isEmpty());
    assertThat(jdbc.queryForList("SELECT * FROM support_applications")).isEqualTo(before);
  }

  @Test
  void attachmentsUseOnlyStoredNamesAndPrivateStorageUrls() throws Exception {
    attach();
    when(storage.readUrl("private/consent"))
        .thenReturn("https://private.example.test/signed?expires=900");
    detail()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.attachments.length()").value(1))
        .andExpect(jsonPath("$.result.attachments[0].length()").value(2))
        .andExpect(jsonPath("$.result.attachments[0].originalName").value("임대인 동의서.pdf"))
        .andExpect(
            jsonPath("$.result.attachments[0].fileUrl")
                .value("https://private.example.test/signed?expires=900"));
    verify(storage).readUrl("private/consent");
  }

  @Test
  void storageFailureIsAnErrorNotAnEmptyAttachmentList() throws Exception {
    attach();
    when(storage.readUrl(anyString()))
        .thenThrow(new IllegalStateException("credentials unavailable"));
    detail()
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("SUPPORT_DETAIL_500_1"));
  }

  @Test
  void unknownAndOtherJurisdictionRequestsHaveTheSameNotFoundResponseBeforeReadingFiles()
      throws Exception {
    attach();
    jdbc.update("UPDATE support_applications SET address='서울특별시 강남구'");
    detail().andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SUPPORT_404_1"));
    detail(Long.toString(Long.MAX_VALUE), token)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SUPPORT_404_1"));
    verifyNoInteractions(storage);
  }

  @ParameterizedTest
  @ValueSource(strings = {"서울특별시 부천시 길", "부천시청 앞", "부천시민로 12", "성남시"})
  void doesNotMatchOtherCitiesOrPartialCityNames(String address) throws Exception {
    jdbc.update("UPDATE support_applications SET address=?", address);
    detail().andExpect(status().isNotFound());
  }

  @Test
  void rejectsUnauthenticatedAndResidentRequests() throws Exception {
    detail(Long.toString(supportId), null)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("COMMON_401_1"));
    detail(Long.toString(supportId), "invalid")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("COMMON_401_3"));
    detail(Long.toString(supportId), residentToken)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("COMMON_403_1"));
  }

  @Test
  void adminWithoutSupportedJurisdictionIsRejected() throws Exception {
    jdbc.update("UPDATE users SET jurisdiction_id=NULL WHERE user_id=?", adminId);
    detail().andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("COMMON_403_1"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "abc", "1.1", "9223372036854775808"})
  void rejectsInvalidIds(String id) throws Exception {
    detail(id, token)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_400_3"));
  }

  @Test
  void responseOnlyStateMappingsNeverChangeStoredValues() throws Exception {
    jdbc.update(
        "UPDATE support_applications SET status='SUBMITTED',landlord_consent_status='CONFIRMED'");
    var before = jdbc.queryForList("SELECT * FROM support_applications");
    detail()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.status").value("SUBMITTED"))
        .andExpect(jsonPath("$.result.landlordConsentStatus").value("VERIFIED"));
    assertThat(jdbc.queryForList("SELECT * FROM support_applications")).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(strings = {"01012340002", "010-1234-0002", "010 1234 0002"})
  void bothRoutesReturnTheStoredPhoneFormatWithoutMasking(String contact) throws Exception {
    jdbc.update("UPDATE support_applications SET contact=?", contact);
    detail().andExpect(status().isOk()).andExpect(jsonPath("$.result.contact").value(contact));
    review().andExpect(status().isOk()).andExpect(jsonPath("$.result.contact").value(contact));
  }

  @Test
  void legacyContactIsReturnedWithoutReformatting() throws Exception {
    jdbc.update("UPDATE support_applications SET contact='invalid-123456789'");
    detail()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.contact").value("invalid-123456789"));
    review()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.contact").value("invalid-123456789"));
  }

  @Test
  void getDoesNotModifyAnyPersistedFields() throws Exception {
    publish();
    var tables =
        List.of(
            "support_applications",
            "inspection_applications",
            "inspections",
            "inspection_results",
            "support_application_status_histories");
    var snapshots = tables.stream().map(t -> jdbc.queryForList("SELECT * FROM " + t)).toList();
    detail().andExpect(status().isOk());
    assertThat(tables.stream().map(t -> jdbc.queryForList("SELECT * FROM " + t)).toList())
        .isEqualTo(snapshots);
  }

  @Test
  void swaggerExposesDetailAlongsideTheExistingListAndStatusRoutes() throws Exception {
    var response =
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse();
    var paths = mapper.readTree(response.getContentAsString()).get("paths");
    var operation = paths.get("/api/admin/support-applications/{supportApplicationId}").get("get");
    assertThat(operation.get("summary").asString()).isEqualTo("지원 신청 상세 조회");
    assertThat(operation.get("responses").has("404")).isTrue();
    assertThat(paths.has("/api/admin/support-applications")).isTrue();
    assertThat(paths.has("/api/admin/support-applications/status")).isTrue();
    assertThat(
            paths
                .get("/api/admin/support-applications/{supportApplicationId}/histories")
                .get("get")
                .get("summary")
                .asString())
        .isEqualTo("지원 자격 검토 정보 조회");
  }

  ResultActions review(String id, String access) throws Exception {
    var request = get("/api/admin/support-applications/" + id + "/histories");
    if (access != null) request.header("Authorization", "Bearer " + access);
    return mvc.perform(request);
  }

  ResultActions review() throws Exception {
    return review(Long.toString(supportId), token);
  }

  @Test
  void reviewReturnsTheScreenDataAndOriginalInspectionRequest() throws Exception {
    publish();
    attach();
    when(storage.readUrl("private/consent")).thenReturn("https://private.example.test/consent");
    jdbc.update(
        "UPDATE inspection_applications SET detail_address='101호',request_reason='출입구 통행을 확보해주세요'");
    jdbc.update(
        "UPDATE support_applications SET occupancy_type='TENANT',support_scope='물막이판 설치',preferred_visit_date='2026-11-10',preferred_visit_time='MORNING'");
    jdbc.update(
        "UPDATE support_programs SET target_description='부천시 침수 취약 주택',description='설치 지원',operating_status='OPEN'");
    long result = jdbc.queryForObject("SELECT result_id FROM inspection_results", Long.class);
    jdbc.update(
        "INSERT INTO inspection_vulnerabilities(result_id,type,sort_order) VALUES(?,'DRAIN',2),(?,'ENTRANCE',0),(?,'ENTRANCE',1)",
        result,
        result,
        result);
    jdbc.update(
        "INSERT INTO prevention_recommendations(result_id,description,sort_order) VALUES(?,'배수구 정비',1),(?,'물막이판 설치',0)",
        result,
        result);
    var before = jdbc.queryForList("SELECT * FROM support_applications");
    long applicationId =
        jdbc.queryForObject("SELECT application_id FROM inspection_applications", Long.class);
    review()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("COMMON_200"))
        .andExpect(jsonPath("$.result.supportApplicationId").value(supportId))
        .andExpect(jsonPath("$.result.applicationId").value(applicationId))
        .andExpect(
            jsonPath("$.result.receiptNo").value(String.format(Locale.ROOT, "%06d", applicationId)))
        .andExpect(jsonPath("$.result.applicantName").value("테스트주민"))
        .andExpect(jsonPath("$.result.contact").value("010-0000-0000"))
        .andExpect(jsonPath("$.result.address").value("경기도 부천시 원미구 길주로 210"))
        .andExpect(jsonPath("$.result.detailAddress").value("101호"))
        .andExpect(jsonPath("$.result.housingType").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.result.occupancyType").value("TENANT"))
        .andExpect(
            jsonPath("$.result.vulnerabilityTypes")
                .value(org.hamcrest.Matchers.contains("ENTRANCE", "DRAIN")))
        .andExpect(
            jsonPath("$.result.recommendedActions")
                .value(org.hamcrest.Matchers.contains("물막이판 설치", "배수구 정비")))
        .andExpect(jsonPath("$.result.residentRequest").value("출입구 통행을 확보해주세요"))
        .andExpect(jsonPath("$.result.requestedFacility").value("출입구 물막이판"))
        .andExpect(jsonPath("$.result.inspectionId").value(inspectionId()))
        .andExpect(jsonPath("$.result.inspectionResultPublished").value(true))
        .andExpect(jsonPath("$.result.preferredConstructionDate").value("2026-11-10"))
        .andExpect(jsonPath("$.result.preferredTimeSlot").value("MORNING"))
        .andExpect(jsonPath("$.result.landlordConsentRequired").value(true))
        .andExpect(jsonPath("$.result.program.name").value("검증용 지원사업"))
        .andExpect(jsonPath("$.result.program.targetDescription").value("부천시 침수 취약 주택"))
        .andExpect(jsonPath("$.result.supportScope").value("물막이판 설치"))
        .andExpect(jsonPath("$.result.attachments[0].originalName").value("임대인 동의서.pdf"))
        .andExpect(
            jsonPath("$.result.attachments[0].fileUrl")
                .value("https://private.example.test/consent"));
    assertThat(jdbc.queryForList("SELECT * FROM support_applications")).isEqualTo(before);
  }

  @Test
  void reviewDoesNotExposeDraftVulnerabilitiesOrInventMissingFiles() throws Exception {
    long result = jdbc.queryForObject("SELECT result_id FROM inspection_results", Long.class);
    jdbc.update(
        "INSERT INTO inspection_vulnerabilities(result_id,type,sort_order) VALUES(?,'DRAIN',0)",
        result);
    review()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.vulnerabilityTypes").isEmpty())
        .andExpect(jsonPath("$.result.recommendedActions").isEmpty())
        .andExpect(jsonPath("$.result.attachments").isEmpty())
        .andExpect(jsonPath("$.result.landlordConsentRequired").value(false))
        .andExpect(jsonPath("$.result.inspectionResultPublished").value(false));
    verifyNoInteractions(storage);
  }

  @Test
  void reviewAllowsMissingInspectionAndNullRequestReason() throws Exception {
    jdbc.update("DELETE FROM inspection_results");
    jdbc.update("DELETE FROM inspections");
    review()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.inspectionId").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.result.residentRequest").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.result.vulnerabilityTypes").isEmpty());
  }

  @Test
  void reviewRejectsUnauthenticatedAndResidentRequests() throws Exception {
    review(Long.toString(supportId), null).andExpect(status().isUnauthorized());
    review(Long.toString(supportId), residentToken).andExpect(status().isForbidden());
    jdbc.update("UPDATE users SET jurisdiction_id=NULL WHERE user_id=?", adminId);
    review().andExpect(status().isForbidden());
    verifyNoInteractions(storage);
  }

  @Test
  void reviewRejectsMissingOrOutsideJurisdictionBeforeReadingAttachments() throws Exception {
    attach();
    jdbc.update("UPDATE support_applications SET address='서울특별시 강남구'");
    review().andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SUPPORT_404_1"));
    review(Long.toString(Long.MAX_VALUE), token).andExpect(status().isNotFound());
    verifyNoInteractions(storage);
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
  void reviewRejectsInvalidIds(String id) throws Exception {
    review(id, token)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_400_3"));
  }

  @Test
  void reviewPropagatesAttachmentReadFailure() throws Exception {
    attach();
    when(storage.readUrl(anyString())).thenThrow(new IllegalStateException("unavailable"));
    review()
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("SUPPORT_DETAIL_500_1"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"SEMI_BASEMENT", "FIRST_FLOOR", "SECOND_FLOOR_OR_ABOVE"})
  void returnsSourceHousingInBothSupportDetailAndReview(String housing) throws Exception {
    jdbc.update("UPDATE inspection_applications SET housing_type=?", housing);
    detail().andExpect(status().isOk()).andExpect(jsonPath("$.result.housingType").value(housing));
    review().andExpect(status().isOk()).andExpect(jsonPath("$.result.housingType").value(housing));
  }

  @Test
  void swaggerDocumentsHousingInDetailAndReviewSuccessExamples() throws Exception {
    var response = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
    var api = mapper.readTree(response.getResponse().getContentAsString());
    for (var path :
        List.of(
            "/api/admin/support-applications/{supportApplicationId}",
            "/api/admin/support-applications/{supportApplicationId}/histories")) {
      var result =
          api.path("paths")
              .path(path)
              .path("get")
              .path("responses")
              .path("200")
              .path("content")
              .path("application/json")
              .path("examples")
              .path("screenData")
              .path("value")
              .path("result");
      assertThat(result.path("housingType").asString()).isEqualTo("SEMI_BASEMENT");
    }
    for (var schema :
        List.of(
            "SupportApplicationDetailResponse",
            "SupportApplicationReviewResponse",
            "InspectionApplicationDetailResponse")) {
      assertThat(
              api.path("components")
                  .path("schemas")
                  .path(schema)
                  .path("properties")
                  .has("housingType"))
          .isTrue();
    }
  }
}
