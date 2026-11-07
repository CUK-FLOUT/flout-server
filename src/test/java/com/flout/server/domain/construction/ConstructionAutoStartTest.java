package com.flout.server.domain.construction;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.construction.repository.ConstructionRepository;
import com.flout.server.domain.construction.service.ConstructionStatusService;
import com.flout.server.domain.notification.entity.NotificationType;
import com.flout.server.domain.notification.service.FcmDeliveryStore;
import com.flout.server.domain.notification.service.PushNotificationRequested;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:construction-auto-start;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false",
      "app.fcm.enabled=false",
      "app.s3.cleanup-interval-millis=3600000"
    })
@AutoConfigureMockMvc
class ConstructionAutoStartTest {
  static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  static final LocalDateTime NOW = LocalDateTime.of(2030, 1, 2, 9, 30);
  @Autowired ConstructionStatusService service;
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired ObjectMapper mapper;

  @MockitoBean(name = "inspectionClock")
  Clock clock;

  @MockitoSpyBean ConstructionRepository constructions;
  @MockitoBean FcmDeliveryStore deliveries;
  long supportId, applicationId, adminId;
  long constructionId = 900;
  String token, residentToken;

  @BeforeEach
  void setup() {
    reset(constructions);
    when(clock.getZone()).thenReturn(SEOUL);
    when(clock.instant()).thenReturn(NOW.atZone(SEOUL).toInstant());
    for (var table :
        List.of(
            "construction_photos",
            "constructions",
            "support_application_status_histories",
            "support_applications",
            "support_programs",
            "inspection_applications",
            "risk_assessments",
            "refresh_tokens",
            "users")) jdbc.update("DELETE FROM " + table);
    var resident = users.saveAndFlush(User.resident("resident@construction.test", "unused", "김주민"));
    residentToken = jwt.issue(resident).accessToken();
    var admin = users.saveAndFlush(User.resident("admin@construction.test", "unused", "박담당"));
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
        "INSERT INTO inspection_applications(applicant_name,applicant_user_id,assessment_id,receipt_no,address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,status) VALUES('김주민',?,?,'CONSTRUCTION-TEST','경기도 부천시 원미구 길주로 210',37.5,126.76,'010-1234-5678','OWNER','NO',FALSE,'RECEIVED')",
        resident.getId(),
        risk);
    applicationId =
        jdbc.queryForObject("SELECT application_id FROM inspection_applications", Long.class);
    jdbc.update(
        "INSERT INTO support_programs(name,created_at) VALUES('물막이판 지원',CURRENT_TIMESTAMP)");
    long program = jdbc.queryForObject("SELECT program_id FROM support_programs", Long.class);
    jdbc.update(
        "INSERT INTO support_applications(application_id,program_id,applicant_user_id,requested_facility,applicant_name,contact,address,occupancy_type,preferred_visit_date,preferred_visit_time,status,submitted_at,landlord_consent_status,support_scope) VALUES(?,?,?,'출입구 물막이판','김주민','010-1234-5678','경기도 부천시 원미구 길주로 210','OWNER','2030-02-01','AFTERNOON','APPROVED',CURRENT_TIMESTAMP,'NOT_REQUIRED','물막이판 설치')",
        applicationId,
        program,
        resident.getId());
    supportId =
        jdbc.queryForObject("SELECT support_application_id FROM support_applications", Long.class);
    jdbc.update(
        "INSERT INTO constructions(construction_id,support_application_id,status,created_at,updated_at) VALUES(?,?,'PENDING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        constructionId,
        supportId);
    jdbc.update(
        "UPDATE constructions SET status='SCHEDULED',scheduled_at='2030-01-01 10:00:00',manager_user_id=?",
        adminId);
    jdbc.update(
        "INSERT INTO construction_photos(construction_id,object_key,original_filename,file_size,content_type,created_at) VALUES(?,'construction-test-photo','완료.jpg',100,'image/jpeg',CURRENT_TIMESTAMP)",
        constructionId);
  }

  @Test
  void startsAtConfirmedTimeAndKeepsSupportPhotosAndAssignmentUnchanged() {
    jdbc.update("UPDATE constructions SET scheduled_at=?", NOW);
    var supportBefore = jdbc.queryForList("SELECT * FROM support_applications");
    var photosBefore = jdbc.queryForList("SELECT * FROM construction_photos");
    assertThat(service.startIfDue(constructionId)).isTrue();
    assertThat(jdbc.queryForObject("SELECT status FROM constructions", String.class))
        .isEqualTo("IN_PROGRESS");
    assertThat(jdbc.queryForObject("SELECT updated_at FROM constructions", LocalDateTime.class))
        .isEqualTo(NOW);
    assertThat(jdbc.queryForObject("SELECT scheduled_at FROM constructions", LocalDateTime.class))
        .isEqualTo(NOW);
    assertThat(jdbc.queryForObject("SELECT manager_user_id FROM constructions", Long.class))
        .isEqualTo(adminId);
    assertThat(jdbc.queryForList("SELECT * FROM support_applications")).isEqualTo(supportBefore);
    assertThat(jdbc.queryForList("SELECT * FROM construction_photos")).isEqualTo(photosBefore);
  }

  @Test
  void waitsUntilTimeAndCatchesUpAfterServerDowntime() {
    jdbc.update("UPDATE constructions SET scheduled_at=?", NOW.plusSeconds(1));
    var before = jdbc.queryForList("SELECT * FROM constructions");
    assertThat(service.startIfDue(constructionId)).isFalse();
    verifyNoInteractions(deliveries);
    assertThat(jdbc.queryForList("SELECT * FROM constructions")).isEqualTo(before);
    when(clock.instant()).thenReturn(NOW.plusHours(2).atZone(SEOUL).toInstant());
    assertThat(service.startIfDue(constructionId)).isTrue();
    assertThat(service.startIfDue(constructionId)).isFalse();
    verify(deliveries)
        .enqueue(
            new PushNotificationRequested(
                jdbc.queryForObject(
                    "SELECT applicant_user_id FROM support_applications", Long.class),
                NotificationType.CONSTRUCTION_STARTED,
                applicationId,
                "construction-started:" + constructionId));
  }

  @ParameterizedTest
  @ValueSource(strings = {"PENDING", "IN_PROGRESS", "COMPLETED"})
  void doesNotChangeOtherStates(String state) {
    jdbc.update("UPDATE constructions SET status=?", state);
    var before = jdbc.queryForList("SELECT * FROM constructions");
    assertThat(service.startIfDue(constructionId)).isFalse();
    assertThat(jdbc.queryForList("SELECT * FROM constructions")).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(strings = {"DRAFT", "SUBMITTED", "REJECTED"})
  void rechecksSupportApprovalAfterLock(String state) {
    jdbc.update("UPDATE support_applications SET status=?", state);
    assertThat(service.startIfDue(constructionId)).isFalse();
    assertThat(constructions.findDueConstructionIds(NOW, PageRequest.of(0, 100))).isEmpty();
    assertThat(jdbc.queryForObject("SELECT status FROM constructions", String.class))
        .isEqualTo("SCHEDULED");
  }

  @Test
  void ignoresMissingConstructionAndNullSchedule() {
    assertThat(service.startIfDue(999999L)).isFalse();
    jdbc.update("UPDATE constructions SET scheduled_at=NULL");
    assertThat(service.startIfDue(constructionId)).isFalse();
    assertThat(constructions.findDueConstructionIds(NOW, PageRequest.of(0, 100))).isEmpty();
  }

  @Test
  void repeatedRunDoesNotOverwriteTransitionTime() {
    assertThat(service.startIfDue(constructionId)).isTrue();
    var before = jdbc.queryForList("SELECT * FROM constructions");
    when(clock.instant()).thenReturn(NOW.plusHours(1).atZone(SEOUL).toInstant());
    assertThat(service.startIfDue(constructionId)).isFalse();
    assertThat(jdbc.queryForList("SELECT * FROM constructions")).isEqualTo(before);
  }

  @Test
  void candidateQueryIncludesOnlyDueScheduledRows() {
    assertThat(constructions.findDueConstructionIds(NOW, PageRequest.of(0, 100)))
        .containsExactly(constructionId);
    jdbc.update("UPDATE constructions SET scheduled_at=?", NOW.plusSeconds(1));
    assertThat(constructions.findDueConstructionIds(NOW, PageRequest.of(0, 100))).isEmpty();
    jdbc.update("UPDATE constructions SET scheduled_at=?,status='COMPLETED'", NOW.minusDays(1));
    assertThat(constructions.findDueConstructionIds(NOW, PageRequest.of(0, 100))).isEmpty();
  }

  @Test
  void concurrentWorkersStartOnlyOnce() throws Exception {
    var pool = Executors.newFixedThreadPool(2);
    var start = new CountDownLatch(1);
    try {
      Callable<Boolean> task =
          () -> {
            start.await();
            return service.startIfDue(constructionId);
          };
      var a = pool.submit(task);
      var b = pool.submit(task);
      start.countDown();
      assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void completionWinsRegardlessOfAutomaticStartOrder() throws Exception {
    var pool = Executors.newFixedThreadPool(2);
    var start = new CountDownLatch(1);
    try {
      var automatic =
          pool.submit(
              () -> {
                start.await();
                return service.startIfDue(constructionId);
              });
      var completion =
          pool.submit(
              () -> {
                start.await();
                return mvc.perform(
                        post("/api/admin/constructions")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                "{\"constructionId\":900,\"finalInstallationResult\":\"Installation completed\"}"))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              });
      start.countDown();
      automatic.get(30, TimeUnit.SECONDS);
      assertThat(completion.get(30, TimeUnit.SECONDS)).isEqualTo(200);
      assertThat(service.startIfDue(constructionId)).isFalse();
      assertThat(jdbc.queryForObject("SELECT status FROM constructions", String.class))
          .isEqualTo("COMPLETED");
      assertThat(
              jdbc.queryForObject(
                  "SELECT final_installation_result FROM constructions", String.class))
          .isEqualTo("Installation completed");
    } finally {
      pool.shutdownNow();
    }
  }
}
