package com.flout.server.domain.inspectionapplication.controller;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

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
            null);
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
        .andExpect(jsonPath("$.result.applicant.name").value("김주민"))
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
}
