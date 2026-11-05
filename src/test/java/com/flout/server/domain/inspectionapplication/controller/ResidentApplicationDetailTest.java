package com.flout.server.domain.inspectionapplication.controller;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.inspection.entity.Inspection;
import com.flout.server.domain.inspection.repository.InspectionRepository;
import com.flout.server.domain.inspectionapplication.dto.request.CreateInspectionApplicationRequest;
import com.flout.server.domain.inspectionapplication.entity.*;
import com.flout.server.domain.inspectionapplication.repository.*;
import com.flout.server.domain.riskassessment.entity.*;
import com.flout.server.domain.riskassessment.repository.RiskAssessmentRepository;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:resident-application-detail;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false"
    })
@AutoConfigureMockMvc
class ResidentApplicationDetailTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired RiskAssessmentRepository assessments;
  @Autowired InspectionApplicationRepository applications;
  @Autowired ApplicationInflowLocationRepository locations;
  @Autowired ApplicationAttachmentRepository attachments;
  @Autowired InspectionRepository inspections;
  @Autowired JwtService jwt;
  @Autowired ObjectMapper mapper;
  Long applicationId;
  Long inspectionId;
  String ownerToken;
  String otherToken;

  @BeforeEach
  void setup() {
    for (String table :
        List.of(
            "inspection_result_photos",
            "prevention_recommendations",
            "inspection_vulnerabilities",
            "inspection_results",
            "inspection_status_histories",
            "inspections",
            "application_attachments",
            "application_inflow_locations",
            "application_status_histories",
            "inspection_applications",
            "risk_assessments",
            "refresh_tokens",
            "user_consents",
            "users")) {
      jdbc.update("DELETE FROM " + table);
    }
    User owner = users.saveAndFlush(User.resident("owner@example.com", "hash", "김주민"));
    User other = users.saveAndFlush(User.resident("other@example.com", "hash", "타인"));
    ownerToken = jwt.issue(owner).accessToken();
    otherToken = jwt.issue(other).accessToken();
    var assessment =
        assessments.saveAndFlush(
            RiskAssessment.completedDemo(owner, "경기도 부천시 원미구 길주로 53", "101호", RiskLevel.HIGH));
    var request =
        new CreateInspectionApplicationRequest(
            assessment.getId(),
            null,
            assessment.getAddress(),
            assessment.getDetailAddress(),
            new BigDecimal("37.5"),
            new BigDecimal("126.7"),
            "010-1234-1234",
            OccupancyType.TENANT,
            FloodExperience.YES,
            true,
            "점검 요청",
            LocalDate.of(2026, 11, 3),
            PreferredTimeSlot.MORNING,
            null,
            "신청이름");
    var application =
        applications.saveAndFlush(
            InspectionApplication.received(owner, assessment, request, "IA-STORED-RECEIPT"));
    applicationId = application.getId();
    inspectionId = inspections.saveAndFlush(Inspection.received(application)).getId();
    locations.saveAndFlush(ApplicationInflowLocation.of(application, InflowLocation.DRAIN));
    attachments.saveAndFlush(
        ApplicationAttachment.create(
            application, "key-1", "photo.jpg", 100, "image/jpeg", null, null));
    jdbc.update(
        "UPDATE inspection_applications SET internal_memo='비공개 메모' WHERE application_id=?",
        applicationId);
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder detail(
      String token) {
    return get("/api/resident/applications/{applicationId}", applicationId)
        .header("Authorization", "Bearer " + token);
  }

  @Test
  void returnsOwnedApplicationWithActualStatusesAndNoInternalMemo() throws Exception {
    mvc.perform(detail(ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("APPLICATION_200_2"))
        .andExpect(jsonPath("$.result.applicationId").value(applicationId))
        .andExpect(
            jsonPath("$.result.receiptNumber")
                .value(String.format(Locale.ROOT, "%06d", applicationId)))
        .andExpect(jsonPath("$.result.applicant.name").value("신청이름"))
        .andExpect(jsonPath("$.result.applicant.phone").value("010-1234-1234"))
        .andExpect(jsonPath("$.result.address").value("경기도 부천시 원미구 길주로 53"))
        .andExpect(jsonPath("$.result.detailAddress").value("101호"))
        .andExpect(jsonPath("$.result.occupancyType").value("TENANT"))
        .andExpect(jsonPath("$.result.vulnerability").doesNotExist())
        .andExpect(jsonPath("$.result.floodExperience").value("YES"))
        .andExpect(jsonPath("$.result.inflowLocations", contains("DRAIN")))
        .andExpect(jsonPath("$.result.existingFacility").value(true))
        .andExpect(jsonPath("$.result.requestReason").value("점검 요청"))
        .andExpect(jsonPath("$.result.preferredVisitDate").value("2026-11-03"))
        .andExpect(jsonPath("$.result.preferredTimeSlot").value("MORNING"))
        .andExpect(jsonPath("$.result.attachmentCount").value(1))
        .andExpect(jsonPath("$.result.applicationStatus").value("RECEIVED"))
        .andExpect(jsonPath("$.result.inspectionStatus").value("RECEIVED"))
        .andExpect(jsonPath("$.result.resultPublished").value(false))
        .andExpect(jsonPath("$.result.createdAt").isNotEmpty())
        .andExpect(jsonPath("$.result.submittedAt").value(nullValue()))
        .andExpect(jsonPath("$.result.internalMemo").doesNotExist());
  }

  @Test
  void distinguishesDraftPublishedAndMissingInspection() throws Exception {
    jdbc.update("UPDATE inspections SET status='IN_PROGRESS' WHERE inspection_id=?", inspectionId);
    jdbc.update(
        "INSERT INTO inspection_results(inspection_id,result_status,internal_memo) VALUES (?, 'DRAFT', '비공개')",
        inspectionId);
    mvc.perform(detail(ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.inspectionStatus").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.result.resultPublished").value(false));
    jdbc.update(
        "UPDATE inspection_results SET result_status='PUBLISHED' WHERE inspection_id=?",
        inspectionId);
    jdbc.update("UPDATE inspections SET status='COMPLETED' WHERE inspection_id=?", inspectionId);
    jdbc.update(
        "UPDATE inspection_applications SET status='INSPECTION_COMPLETED' WHERE application_id=?",
        applicationId);
    mvc.perform(detail(ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.applicationStatus").value("INSPECTION_COMPLETED"))
        .andExpect(jsonPath("$.result.inspectionStatus").value("COMPLETED"))
        .andExpect(jsonPath("$.result.resultPublished").value(true));
    jdbc.update("DELETE FROM inspection_results WHERE inspection_id=?", inspectionId);
    jdbc.update("DELETE FROM inspections WHERE inspection_id=?", inspectionId);
    mvc.perform(detail(ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.inspectionStatus").value(nullValue()))
        .andExpect(jsonPath("$.result.resultPublished").value(false));
  }

  @Test
  void enforcesAuthenticationOwnershipAndIdValidation() throws Exception {
    mvc.perform(get("/api/resident/applications/{id}", applicationId))
        .andExpect(status().isUnauthorized());
    mvc.perform(detail(otherToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("APPLICATION_403_6"));
    mvc.perform(
            get("/api/resident/applications/{id}", applicationId)
                .header("Authorization", "Bearer invalid"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/resident/applications/9223372036854775807")
                .header("Authorization", "Bearer " + ownerToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("APPLICATION_404_2"));
    mvc.perform(get("/api/resident/applications/0").header("Authorization", "Bearer " + ownerToken))
        .andExpect(status().isBadRequest());
  }

  @Test
  void optionalFieldsAndEmptyCollectionsAreReturned() throws Exception {
    jdbc.update("DELETE FROM application_inflow_locations WHERE application_id=?", applicationId);
    jdbc.update("DELETE FROM application_attachments WHERE application_id=?", applicationId);
    jdbc.update(
        "UPDATE inspection_applications SET detail_address=NULL, request_reason=NULL, preferred_visit_date=NULL, preferred_time_slot=NULL WHERE application_id=?",
        applicationId);
    mvc.perform(detail(ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.detailAddress").value(nullValue()))
        .andExpect(jsonPath("$.result.requestReason").value(nullValue()))
        .andExpect(jsonPath("$.result.preferredVisitDate").value(nullValue()))
        .andExpect(jsonPath("$.result.preferredTimeSlot").value(nullValue()))
        .andExpect(jsonPath("$.result.inflowLocations").isEmpty())
        .andExpect(jsonPath("$.result.attachmentCount").value(0));
  }

  @Test
  void swaggerShowsOwnershipErrorsAndTheStateFields() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                    "$.components.schemas.ResidentApplicationDetailResponse.properties.submittedAt")
                .exists())
        .andExpect(
            jsonPath(
                    "$.paths['/api/resident/applications/{applicationId}'].get.responses['200'].content['application/json'].examples.residentApplicationDetail.value.result.submittedAt")
                .value("2026-10-21T22:35:00"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/resident/applications/{applicationId}'].get.responses['403'].content['application/json'].examples.applicationAccessDenied.value.code")
                .value("APPLICATION_403_6"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/resident/applications/{applicationId}'].get.responses['200'].content['application/json'].examples.residentApplicationDetail.value.result.resultPublished")
                .value(false))
        .andExpect(
            jsonPath(
                    "$.components.schemas.ResidentApplicationDetailResponse.properties.vulnerability")
                .doesNotExist())
        .andExpect(
            jsonPath(
                    "$.paths['/api/resident/applications/{applicationId}'].get.responses['200'].content['application/json'].examples.residentApplicationDetail.value.result.vulnerability")
                .doesNotExist());
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
      recommendations(String token) {
    return get("/api/resident/applications/{applicationId}/recommendations", applicationId)
        .header("Authorization", "Bearer " + token);
  }

  @Test
  void returnsPublishedPostDescriptionsExactlyInOrderAndNeverDraft() throws Exception {
    jdbc.update(
        "INSERT INTO users(email,password_hash,name,role,jurisdiction_id) VALUES('recommendations-admin@example.com','hash','운영자','ADMIN',(SELECT jurisdiction_id FROM jurisdictions WHERE name='부천시'))");
    var admin = users.findByEmail("recommendations-admin@example.com").orElseThrow();
    jdbc.update(
        "INSERT INTO inspection_results(inspection_id,result_status) VALUES(?,'DRAFT')",
        inspectionId);
    Long resultId =
        jdbc.queryForObject(
            "SELECT result_id FROM inspection_results WHERE inspection_id=? AND result_status='DRAFT'",
            Long.class,
            inspectionId);
    jdbc.update(
        "INSERT INTO inspection_result_photos(result_id,object_key,original_filename,file_size,content_type) VALUES(?,'result-photo','사진.png',100,'image/png')",
        resultId);
    String first = "  출입구: 물막이판 설치를 검토해요.\n원문 유지  ";
    String second = "배수구: 막힘 여부를 확인해요.";
    var request =
        Map.of(
            "summary",
            "점검 결과",
            "vulnerabilities",
            List.of(Map.of("type", "ENTRANCE", "title", "출입구", "expertOpinion", "의견")),
            "recommendedActions",
            List.of(Map.of("description", first), Map.of("description", second)),
            "followUpType",
            "INDIVIDUAL_HOUSE");
    mvc.perform(
            post("/api/admin/inspections/{id}/result", inspectionId)
                .header("Authorization", "Bearer " + jwt.issue(admin).accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request)))
        .andExpect(status().isOk());
    jdbc.update(
        "INSERT INTO inspection_results(inspection_id,result_status) VALUES(?,'DRAFT')",
        inspectionId);
    Long draftId =
        jdbc.queryForObject(
            "SELECT result_id FROM inspection_results WHERE inspection_id=? AND result_status='DRAFT'",
            Long.class,
            inspectionId);
    jdbc.update(
        "INSERT INTO prevention_recommendations(result_id,description,sort_order) VALUES(?,'비공개 초안',0)",
        draftId);
    mvc.perform(recommendations(ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("APPLICATION_200_8"))
        .andExpect(jsonPath("$.result.applicationId").value(applicationId))
        .andExpect(jsonPath("$.result.recommendedActions", hasSize(2)))
        .andExpect(jsonPath("$.result.recommendedActions[0].description").value(first))
        .andExpect(jsonPath("$.result.recommendedActions[1].description").value(second));
  }

  @Test
  void returnsEmptyArrayAndOrdersByStoredSortOrder() throws Exception {
    jdbc.update(
        "INSERT INTO inspection_results(inspection_id,result_status) VALUES(?,'PUBLISHED')",
        inspectionId);
    Long resultId =
        jdbc.queryForObject(
            "SELECT result_id FROM inspection_results WHERE inspection_id=?",
            Long.class,
            inspectionId);
    mvc.perform(recommendations(ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.recommendedActions", hasSize(0)));
    jdbc.update(
        "INSERT INTO prevention_recommendations(result_id,description,sort_order) VALUES(?,'두 번째',2),(?,'첫 번째',0)",
        resultId,
        resultId);
    mvc.perform(recommendations(ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.recommendedActions[0].description").value("첫 번째"))
        .andExpect(jsonPath("$.result.recommendedActions[1].description").value("두 번째"));
  }

  @Test
  void recommendationsEnforceAccessBeforePublicationAndValidateRequests() throws Exception {
    mvc.perform(get("/api/resident/applications/{id}/recommendations", applicationId))
        .andExpect(status().isUnauthorized());
    mvc.perform(recommendations(otherToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("APPLICATION_403_6"));
    mvc.perform(recommendations(ownerToken))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("APPLICATION_400_5"));
    jdbc.update(
        "INSERT INTO inspection_results(inspection_id,result_status) VALUES(?,'DRAFT')",
        inspectionId);
    mvc.perform(recommendations(ownerToken))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("APPLICATION_400_5"));
    jdbc.update("DELETE FROM inspection_results WHERE inspection_id=?", inspectionId);
    jdbc.update("DELETE FROM inspections WHERE inspection_id=?", inspectionId);
    mvc.perform(recommendations(ownerToken))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("APPLICATION_400_5"));
    mvc.perform(
            get("/api/resident/applications/9223372036854775807/recommendations")
                .header("Authorization", "Bearer " + ownerToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("APPLICATION_404_2"));
    mvc.perform(
            get("/api/resident/applications/0/recommendations")
                .header("Authorization", "Bearer " + ownerToken))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/resident/applications/abc/recommendations")
                .header("Authorization", "Bearer " + ownerToken))
        .andExpect(status().isBadRequest());
  }

  @Test
  void swaggerDocumentsRecommendationsAndErrors() throws Exception {
    String path =
        "$.paths['/api/resident/applications/{applicationId}/recommendations'].get.responses";
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                    path
                        + "['200'].content['application/json'].examples.residentRecommendations.value.result.recommendedActions[0].description")
                .isNotEmpty())
        .andExpect(
            jsonPath(
                    path
                        + "['400'].content['application/json'].examples.resultNotPublished.value.code")
                .value("APPLICATION_400_5"))
        .andExpect(
            jsonPath(
                    path
                        + "['403'].content['application/json'].examples.applicationAccessDenied.value.code")
                .value("APPLICATION_403_6"))
        .andExpect(
            jsonPath(
                    path
                        + "['404'].content['application/json'].examples.applicationNotFound.value.code")
                .value("APPLICATION_404_2"))
        .andExpect(jsonPath(path + "['404'].description").value("현장점검 신청을 찾을 수 없음"));
  }
}
