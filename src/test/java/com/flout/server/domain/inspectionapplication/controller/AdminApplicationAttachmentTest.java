package com.flout.server.domain.inspectionapplication.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.inspectionapplication.dto.request.CreateInspectionApplicationRequest;
import com.flout.server.domain.inspectionapplication.entity.*;
import com.flout.server.domain.inspectionapplication.repository.*;
import com.flout.server.domain.inspectionapplication.service.AttachmentStorage;
import com.flout.server.domain.riskassessment.entity.*;
import com.flout.server.domain.riskassessment.repository.RiskAssessmentRepository;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:admin-attachments;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false"
    })
@AutoConfigureMockMvc
class AdminApplicationAttachmentTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired RiskAssessmentRepository assessments;
  @Autowired InspectionApplicationRepository applications;
  @Autowired ApplicationInflowLocationRepository locations;
  @Autowired JwtService jwt;
  @MockitoBean AttachmentStorage storage;
  @Autowired ApplicationAttachmentRepository attachments;
  User resident;
  Long operatorId;
  Long applicationId;
  Long assessmentId;
  String token;

  // 관할 운영자와 신청·분석·유입 위치 준비
  @BeforeEach
  void setup() {
    for (String table :
        List.of(
            "application_attachments",
            "priority_inspection_designations",
            "inspection_status_histories",
            "inspections",
            "application_inflow_locations",
            "application_status_histories",
            "inspection_applications",
            "prevention_guides",
            "risk_assessments",
            "refresh_tokens",
            "user_consents",
            "users")) {
      jdbc.update("DELETE FROM " + table);
    }
    resident = users.saveAndFlush(User.resident("detail-resident@example.com", "hash", "김주민"));
    jdbc.update(
        "INSERT INTO users(email,password_hash,name,role,jurisdiction_id) VALUES ('detail-admin@example.com','hash','김운영','ADMIN',(SELECT jurisdiction_id FROM jurisdictions WHERE name = '부천시'))");
    var operator = users.findByEmail("detail-admin@example.com").orElseThrow();
    operatorId = operator.getId();
    token = jwt.issue(operator).accessToken();
    var assessment =
        assessments.saveAndFlush(
            RiskAssessment.completedDemo(resident, "경기도 부천시 원미구 길주로 53", "101호", RiskLevel.HIGH));
    assessmentId = assessment.getId();
    var request =
        new CreateInspectionApplicationRequest(
            assessmentId,
            null,
            assessment.getAddress(),
            assessment.getDetailAddress(),
            new BigDecimal("37.5"),
            new BigDecimal("126.7"),
            "010-1234-1234",
            OccupancyType.TENANT,
            FloodExperience.YES,
            false,
            "최근 배수구 역류가 있어 점검을 희망합니다.",
            LocalDate.of(2026, 11, 3),
            PreferredTimeSlot.MORNING,
            null);
    var application =
        applications.saveAndFlush(
            InspectionApplication.received(resident, assessment, request, "IA-DETAIL-TEST"));
    applicationId = application.getId();
    locations.saveAndFlush(ApplicationInflowLocation.of(application, InflowLocation.BATHROOM));
    locations.saveAndFlush(ApplicationInflowLocation.of(application, InflowLocation.DRAIN));
  }

  // 운영자 토큰으로 사진 목록 요청 생성
  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder detail() {
    return get("/api/admin/applications/{applicationId}/attachments", applicationId)
        .header("Authorization", "Bearer " + token);
  }

  // 다른 신청 사진 제외 및 등록 순서·원본 파일명·데이터 보존 확인
  @Test
  void returnsOnlyRequestedPhotosInOrderWithoutChangingData() throws Exception {
    var application = applications.findById(applicationId).orElseThrow();
    var first =
        attachments.saveAndFlush(
            ApplicationAttachment.create(
                application, "photos/first", "출입구.jpg", 10, "image/jpeg", null, null));
    var second =
        attachments.saveAndFlush(
            ApplicationAttachment.create(
                application, "photos/second", "배수구.png", 20, "image/png", null, null));
    jdbc.update(
        "INSERT INTO inspection_applications(applicant_user_id, assessment_id, receipt_no, address, contact, occupancy_type, flood_experience, existing_facility, status, latitude, longitude) SELECT applicant_user_id, assessment_id, 'OTHER-ATTACH', address, contact, occupancy_type, flood_experience, existing_facility, status, latitude, longitude FROM inspection_applications WHERE application_id = ?",
        applicationId);
    Long otherId =
        jdbc.queryForObject(
            "SELECT application_id FROM inspection_applications WHERE receipt_no = 'OTHER-ATTACH'",
            Long.class);
    attachments.saveAndFlush(
        ApplicationAttachment.create(
            applications.findById(otherId).orElseThrow(),
            "photos/other",
            "다른사진.jpg",
            10,
            "image/jpeg",
            null,
            null));
    when(storage.readUrl(anyString()))
        .thenAnswer(call -> "https://images.example/" + call.getArgument(0));
    var before = jdbc.queryForList("SELECT * FROM application_attachments ORDER BY attachment_id");
    var applicationBefore =
        jdbc.queryForList("SELECT * FROM inspection_applications ORDER BY application_id");
    mvc.perform(detail())
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.code").value("APPLICATION_200_3"))
        .andExpect(jsonPath("$.result", hasSize(2)))
        .andExpect(jsonPath("$.result[0].attachmentId").value(first.getId()))
        .andExpect(jsonPath("$.result[0].originalFilename").value("출입구.jpg"))
        .andExpect(jsonPath("$.result[0].imageUrl").value("https://images.example/photos/first"))
        .andExpect(jsonPath("$.result[0].objectKey").doesNotExist())
        .andExpect(jsonPath("$.result[1].attachmentId").value(second.getId()));
    verify(storage, never()).readUrl("photos/other");
    assertThat(jdbc.queryForList("SELECT * FROM application_attachments ORDER BY attachment_id"))
        .isEqualTo(before);
    assertThat(jdbc.queryForList("SELECT * FROM inspection_applications ORDER BY application_id"))
        .isEqualTo(applicationBefore);
  }

  // 파일명과 무관한 DB 등록 ID 순서 검증
  @Test
  void registrationOrderUsesGeneratedIdsInsteadOfFilename() throws Exception {
    var application = applications.findById(applicationId).orElseThrow();
    var first =
        attachments.saveAndFlush(
            ApplicationAttachment.create(
                application, "photos/z", "z.jpg", 10, "image/jpeg", null, null));
    var second =
        attachments.saveAndFlush(
            ApplicationAttachment.create(
                application, "photos/a", "a.jpg", 10, "image/jpeg", null, null));
    assertThat(first.getId()).isLessThan(second.getId());
    when(storage.readUrl(anyString())).thenReturn("https://images.example/photo");
    mvc.perform(detail())
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.result[*].attachmentId",
                contains(first.getId().intValue(), second.getId().intValue())))
        .andExpect(jsonPath("$.result[*].originalFilename", contains("z.jpg", "a.jpg")));
  }

  // 사진 미등록 시 빈 배열 반환
  @Test
  void returnsEmptyList() throws Exception {
    mvc.perform(detail()).andExpect(status().isOk()).andExpect(jsonPath("$.result").isEmpty());
    verifyNoInteractions(storage);
  }

  // 관할 밖 주소 및 미설정 관할의 URL 발급 차단
  @Test
  void rejectsOutsideAndMissingJurisdiction() throws Exception {
    jdbc.update(
        "UPDATE inspection_applications SET address = '서울특별시 강남구 1' WHERE application_id = ?",
        applicationId);
    mvc.perform(detail())
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("APPLICATION_403_4"));
    jdbc.update("UPDATE users SET jurisdiction_id = NULL WHERE user_id = ?", operatorId);
    mvc.perform(detail()).andExpect(status().isForbidden());
    verifyNoInteractions(storage);
  }

  // 잘못된 신청 ID 거부
  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
  void rejectsInvalidId(String id) throws Exception {
    mvc.perform(
            get("/api/admin/applications/{applicationId}/attachments", id)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_400_3"));
    verifyNoInteractions(storage);
  }

  // 인증·권한·미존재 검증
  @Test
  void rejectsUnauthorizedForbiddenAndMissing() throws Exception {
    mvc.perform(get("/api/admin/applications/{applicationId}/attachments", applicationId))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/admin/applications/{applicationId}/attachments", applicationId)
                .header("Authorization", "Bearer invalid"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/admin/applications/{applicationId}/attachments", applicationId)
                .header("Authorization", "Bearer " + jwt.issue(resident).accessToken()))
        .andExpect(status().isForbidden());
    applicationId = Long.MAX_VALUE;
    mvc.perform(detail())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("APPLICATION_404_2"));
    verifyNoInteractions(storage);
  }

  // URL 발급 실패 시 공통 오류 응답 반환
  @Test
  void handlesSigningFailure() throws Exception {
    attachments.saveAndFlush(
        ApplicationAttachment.create(
            applications.findById(applicationId).orElseThrow(),
            "photos/fail",
            "사진.jpg",
            10,
            "image/jpeg",
            null,
            null));
    when(storage.readUrl(anyString()))
        .thenThrow(new IllegalStateException("credentials unavailable"));
    mvc.perform(detail())
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("APPLICATION_500_3"))
        .andExpect(jsonPath("$.result").value(nullValue()));
  }
}
