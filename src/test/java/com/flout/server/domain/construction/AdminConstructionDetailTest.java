package com.flout.server.domain.construction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.inspectionapplication.service.AttachmentStorage;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import java.util.*;
import org.junit.jupiter.api.*;
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
      "spring.datasource.url=jdbc:h2:mem:admin-construction-detail;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false",
      "app.fcm.enabled=false",
      "app.s3.cleanup-interval-millis=3600000"
    })
@AutoConfigureMockMvc
class AdminConstructionDetailTest {
  static final long CONSTRUCTION_ID = 900;

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired ObjectMapper mapper;
  @MockitoBean AttachmentStorage storage;

  long residentId;
  long adminId;
  long applicationId;
  long supportId;
  String token;
  String residentToken;

  @BeforeEach
  void setup() {
    reset(storage);
    for (var table :
        List.of(
            "construction_photos",
            "construction_status_histories",
            "constructions",
            "support_application_status_histories",
            "support_applications",
            "support_programs",
            "inspection_applications",
            "risk_assessments",
            "refresh_tokens",
            "users")) jdbc.update("DELETE FROM " + table);

    var resident = users.saveAndFlush(User.resident("resident@detail.test", "unused", "김주민"));
    residentId = resident.getId();
    residentToken = jwt.issue(resident).accessToken();
    var admin = users.saveAndFlush(User.resident("admin@detail.test", "unused", "박담당"));
    adminId = admin.getId();
    jdbc.update(
        "UPDATE users SET role='ADMIN',jurisdiction_id=(SELECT jurisdiction_id FROM jurisdictions WHERE name='부천시') WHERE user_id=?",
        adminId);
    token = jwt.issue(users.findById(adminId).orElseThrow()).accessToken();

    jdbc.update(
        "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status) VALUES(?,'경기도 부천시 원미구 길주로 210','101호','HIGH','COMPLETED')",
        residentId);
    long riskId = jdbc.queryForObject("SELECT assessment_id FROM risk_assessments", Long.class);
    jdbc.update(
        "INSERT INTO inspection_applications(applicant_name,applicant_user_id,assessment_id,receipt_no,address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,status) VALUES('김주민',?,?,'DETAIL-CONSTRUCTION','경기도 부천시 원미구 길주로 210',37.5,126.76,'010-1234-5678','TENANT','NO',FALSE,'RECEIVED')",
        residentId,
        riskId);
    applicationId =
        jdbc.queryForObject("SELECT application_id FROM inspection_applications", Long.class);
    jdbc.update(
        "INSERT INTO support_programs(name,created_at) VALUES('물막이판 지원',CURRENT_TIMESTAMP)");
    long programId = jdbc.queryForObject("SELECT program_id FROM support_programs", Long.class);
    jdbc.update(
        "INSERT INTO support_applications(application_id,program_id,applicant_user_id,requested_facility,applicant_name,contact,address,occupancy_type,preferred_visit_date,preferred_visit_time,status,support_scope,approved_by_user_id,approved_at,submitted_at,landlord_consent_status) VALUES(?,?,?,'출입구 물막이판','김주민','010-1234-5678','경기도 부천시 원미구 길주로 210','TENANT','2030-02-01','MORNING','APPROVED','승인된 시설 설치',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'CONFIRMED')",
        applicationId,
        programId,
        residentId,
        adminId);
    supportId =
        jdbc.queryForObject("SELECT support_application_id FROM support_applications", Long.class);
    jdbc.update(
        "INSERT INTO constructions(construction_id,support_application_id,status,scheduled_at,manager_user_id,created_at,updated_at) VALUES(?,?,'IN_PROGRESS','2030-02-03 10:00:00',?,CURRENT_TIMESTAMP,'2030-02-03 10:00:00')",
        CONSTRUCTION_ID,
        supportId,
        adminId);
    jdbc.update(
        "INSERT INTO construction_photos(construction_id,object_key,original_filename,file_size,content_type,created_at) VALUES(?,'constructions/900/photos/result','완료사진.jpg',321,'image/jpeg','2030-02-03 10:30:00')",
        CONSTRUCTION_ID);
    when(storage.readUrl(anyString()))
        .thenAnswer(call -> "https://private.example.test/" + call.getArgument(0));
  }

  ResultActions detail(String id, String access) throws Exception {
    var request = get("/api/admin/constructions/" + id);
    if (access != null) request.header("Authorization", "Bearer " + access);
    return mvc.perform(request);
  }

  @Test
  void returnsScreenDetailsAndUploadedPhotosBeforeCompletionWithoutMutation() throws Exception {
    var constructionBefore = jdbc.queryForList("SELECT * FROM constructions");
    var supportBefore = jdbc.queryForList("SELECT * FROM support_applications");

    detail(Long.toString(CONSTRUCTION_ID), token)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("COMMON_200"))
        .andExpect(jsonPath("$.result.length()").value(20))
        .andExpect(jsonPath("$.result.constructionId").value(CONSTRUCTION_ID))
        .andExpect(jsonPath("$.result.supportApplicationId").value(supportId))
        .andExpect(jsonPath("$.result.applicationId").value(applicationId))
        .andExpect(
            jsonPath("$.result.receiptNo").value(String.format(Locale.ROOT, "%06d", applicationId)))
        .andExpect(jsonPath("$.result.applicantName").value("김주민"))
        .andExpect(jsonPath("$.result.supportStatus").value("APPROVED"))
        .andExpect(jsonPath("$.result.landlordConsentStatus").value("VERIFIED"))
        .andExpect(jsonPath("$.result.requestedFacility").value("출입구 물막이판"))
        .andExpect(jsonPath("$.result.supportScope").value("승인된 시설 설치"))
        .andExpect(jsonPath("$.result.preferredConstructionDate").value("2030-02-01"))
        .andExpect(jsonPath("$.result.preferredTimeSlot").value("MORNING"))
        .andExpect(jsonPath("$.result.scheduledConstructionDate").value("2030-02-03"))
        .andExpect(jsonPath("$.result.scheduledConstructionTime").value("10:00:00"))
        .andExpect(jsonPath("$.result.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.result.managerId").value(adminId))
        .andExpect(jsonPath("$.result.managerName").value("박담당"))
        .andExpect(jsonPath("$.result.resultPublished").value(false))
        .andExpect(jsonPath("$.result.finalInstallationResult").doesNotExist())
        .andExpect(jsonPath("$.result.completedAt").doesNotExist())
        .andExpect(jsonPath("$.result.photos.length()").value(1))
        .andExpect(jsonPath("$.result.photos[0].length()").value(6))
        .andExpect(jsonPath("$.result.photos[0].fileName").value("완료사진.jpg"))
        .andExpect(
            jsonPath("$.result.photos[0].url")
                .value("https://private.example.test/constructions/900/photos/result"))
        .andExpect(jsonPath("$.result.photos[0].size").value(321))
        .andExpect(jsonPath("$.result.photos[0].contentType").value("image/jpeg"))
        .andExpect(jsonPath("$.result.photos[0].uploadedAt").value("2030-02-03T10:30:00+09:00"));

    assertThat(jdbc.queryForList("SELECT * FROM constructions")).isEqualTo(constructionBefore);
    assertThat(jdbc.queryForList("SELECT * FROM support_applications")).isEqualTo(supportBefore);
  }

  @Test
  void returnsPublishedResultAndCompletionTimeAfterWholeCompletion() throws Exception {
    jdbc.update(
        "UPDATE constructions SET status='COMPLETED',final_installation_result='물막이판 설치 및 개폐 확인 완료',completed_at='2030-02-03 11:30:00'");

    detail(Long.toString(CONSTRUCTION_ID), token)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.status").value("COMPLETED"))
        .andExpect(jsonPath("$.result.resultPublished").value(true))
        .andExpect(jsonPath("$.result.finalInstallationResult").value("물막이판 설치 및 개폐 확인 완료"))
        .andExpect(jsonPath("$.result.completedAt").value("2030-02-03T11:30:00+09:00"))
        .andExpect(jsonPath("$.result.photos.length()").value(1));
  }

  @Test
  void returnsNullScheduleAndEmptyPhotosForPendingConstruction() throws Exception {
    jdbc.update("DELETE FROM construction_photos WHERE construction_id=?", CONSTRUCTION_ID);
    jdbc.update(
        "UPDATE constructions SET status='PENDING',scheduled_at=NULL,manager_user_id=NULL WHERE construction_id=?",
        CONSTRUCTION_ID);

    detail(Long.toString(CONSTRUCTION_ID), token)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.status").value("PENDING"))
        .andExpect(jsonPath("$.result.scheduledConstructionDate").doesNotExist())
        .andExpect(jsonPath("$.result.scheduledConstructionTime").doesNotExist())
        .andExpect(jsonPath("$.result.managerId").doesNotExist())
        .andExpect(jsonPath("$.result.managerName").doesNotExist())
        .andExpect(jsonPath("$.result.photos").isEmpty());
    verify(storage, never()).readUrl(anyString());
  }

  @Test
  void validatesIdExistenceAuthenticationAndJurisdiction() throws Exception {
    for (var id : List.of("0", "-1", "abc", "9223372036854775808"))
      detail(id, token).andExpect(status().isBadRequest());
    detail("99999", token)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CONSTRUCTION_404_1"));
    detail(Long.toString(supportId), token).andExpect(status().isNotFound());
    detail(Long.toString(CONSTRUCTION_ID), null).andExpect(status().isUnauthorized());
    detail(Long.toString(CONSTRUCTION_ID), "invalid").andExpect(status().isUnauthorized());
    detail(Long.toString(CONSTRUCTION_ID), residentToken).andExpect(status().isForbidden());

    jdbc.update(
        "UPDATE support_applications SET address='서울특별시' WHERE support_application_id=?",
        supportId);
    detail(Long.toString(CONSTRUCTION_ID), token)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CONSTRUCTION_403_1"));
  }

  @Test
  void reportsPhotoReadUrlFailure() throws Exception {
    when(storage.readUrl(anyString())).thenThrow(new IllegalStateException("storage unavailable"));

    detail(Long.toString(CONSTRUCTION_ID), token)
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("CONSTRUCTION_500_2"));
  }

  @Test
  void swaggerPublishesCorrectConstructionDetailPath() throws Exception {
    var response = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
    var paths = mapper.readTree(response.getResponse().getContentAsString()).path("paths");
    assertThat(
            paths
                .path("/api/admin/constructions/{constructionId}")
                .path("get")
                .path("summary")
                .asString())
        .isEqualTo("시공 상세 조회");
    assertThat(paths.path("/api/admin/constrictions/{constructionId}").isMissingNode()).isTrue();
  }
}
