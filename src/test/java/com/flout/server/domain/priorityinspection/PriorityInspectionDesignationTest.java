package com.flout.server.domain.priorityinspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.inspection.entity.Inspection;
import com.flout.server.domain.inspection.repository.InspectionRepository;
import com.flout.server.domain.inspectionapplication.dto.request.CreateInspectionApplicationRequest;
import com.flout.server.domain.inspectionapplication.entity.*;
import com.flout.server.domain.inspectionapplication.repository.*;
import com.flout.server.domain.priorityinspection.repository.PriorityInspectionDesignationRepository;
import com.flout.server.domain.riskassessment.entity.*;
import com.flout.server.domain.riskassessment.repository.RiskAssessmentRepository;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:priority-designation;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false"
    })
@AutoConfigureMockMvc
class PriorityInspectionDesignationTest {
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final LocalDateTime NOW = LocalDateTime.of(2030, 1, 2, 14, 0);
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper mapper;
  @Autowired UserRepository users;
  @Autowired RiskAssessmentRepository assessments;
  @Autowired InspectionApplicationRepository applications;
  @Autowired InspectionRepository inspections;
  @Autowired ApplicationStatusHistoryRepository applicationHistories;
  @Autowired JwtService jwt;
  @Autowired EntityManager entityManager;
  @MockitoSpyBean PriorityInspectionDesignationRepository designations;
  @MockitoBean Clock inspectionClock;
  User resident;
  Long operatorId;
  Long applicationId;
  Long inspectionId;
  String token;

  // 신청과 점검이 연결된 접수 건 준비
  @BeforeEach
  void setup() {
    when(inspectionClock.getZone()).thenReturn(SEOUL);
    when(inspectionClock.instant()).thenReturn(NOW.atZone(SEOUL).toInstant());
    for (String table :
        List.of(
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
            "users")) jdbc.update("DELETE FROM " + table);
    resident = users.saveAndFlush(User.resident("priority-resident@example.com", "hash", "주민"));
    jdbc.update(
        """
        INSERT INTO users (email, password_hash, name, role, jurisdiction_id)
        VALUES ('priority-admin@example.com', 'hash', '김담당', 'ADMIN',
          (SELECT jurisdiction_id FROM jurisdictions WHERE name = '부천시'))
        """);
    var operator = users.findByEmail("priority-admin@example.com").orElseThrow();
    operatorId = operator.getId();
    token = jwt.issue(operator).accessToken();
    var assessment =
        assessments.saveAndFlush(
            RiskAssessment.completedDemo(resident, "경기도 부천시 원미구 길주로 210", "101호", RiskLevel.HIGH));
    var request =
        new CreateInspectionApplicationRequest(
            assessment.getId(),
            null,
            new BigDecimal("37.4874850"),
            new BigDecimal("126.7985672"),
            "010-1234-5678",
            OccupancyType.OWNER,
            FloodExperience.YES,
            false,
            "점검 요청",
            LocalDate.of(2030, 1, 3),
            PreferredTimeSlot.MORNING,
            null);
    var application =
        applications.saveAndFlush(
            InspectionApplication.received(resident, assessment, request, "IA-PRIORITY-TEST"));
    applicationId = application.getId();
    applicationHistories.saveAndFlush(ApplicationStatusHistory.received(application));
    inspectionId = inspections.saveAndFlush(Inspection.received(application)).getId();
    jdbc.update(
        "UPDATE inspection_applications SET internal_memo = '유지할 내부 메모' WHERE application_id = ?",
        applicationId);
  }

  private String path() {
    return "/api/admin/priority-inspections/" + applicationId + "/designation";
  }

  private org.springframework.test.web.servlet.ResultActions designate() throws Exception {
    return updateDesignation(true);
  }

  private org.springframework.test.web.servlet.ResultActions updateDesignation(boolean designated)
      throws Exception {
    return mvc.perform(
        put(path())
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"designated\":" + designated + "}"));
  }

  @Test
  void savesDesignationAndPreservesApplicationInspectionAndHistories() throws Exception {
    var beforeApplication =
        jdbc.queryForMap(
            "SELECT * FROM inspection_applications WHERE application_id = ?", applicationId);
    var beforeInspection =
        jdbc.queryForMap("SELECT * FROM inspections WHERE inspection_id = ?", inspectionId);
    designate()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("PRIORITY_INSPECTION_200_1"))
        .andExpect(jsonPath("$.result.applicationId").value(applicationId))
        .andExpect(
            jsonPath("$.result.receiptNo").value(String.format(Locale.ROOT, "%06d", applicationId)))
        .andExpect(jsonPath("$.result.designated").value(true))
        .andExpect(jsonPath("$.result.selectionMemo").doesNotExist())
        .andExpect(jsonPath("$.result.designatedAt").value("2030-01-02T14:00:00"));
    var row =
        jdbc.queryForMap(
            "SELECT * FROM priority_inspection_designations WHERE application_id = ?",
            applicationId);
    assertThat(((Number) row.get("designated_by_user_id")).longValue()).isEqualTo(operatorId);
    assertThat(((Timestamp) row.get("designated_at")).toLocalDateTime()).isEqualTo(NOW);
    assertThat(
            jdbc.queryForMap(
                "SELECT * FROM inspection_applications WHERE application_id = ?", applicationId))
        .isEqualTo(beforeApplication);
    assertThat(jdbc.queryForMap("SELECT * FROM inspections WHERE inspection_id = ?", inspectionId))
        .isEqualTo(beforeInspection);
    assertThat(applicationHistories.count()).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inspection_status_histories", Long.class))
        .isZero();
  }

  @Test
  void suppliedMemoDoesNotChangeExistingApplicationMemo() throws Exception {
    mvc.perform(
            put(path())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"designated\":true,\"selectionMemo\":\"저장하지 않을 메모\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.selectionMemo").doesNotExist());
    assertThat(applications.findById(applicationId).orElseThrow().getInternalMemo())
        .isEqualTo("유지할 내부 메모");
  }

  // 중복 요청 시 지정 운영자와 시각 유지
  @Test
  void repeatDesignationReturnsOriginalResultEvenForAnotherOperator() throws Exception {
    var original =
        designate().andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    when(inspectionClock.instant()).thenReturn(NOW.plusDays(1).atZone(SEOUL).toInstant());
    jdbc.update(
        """
        INSERT INTO users (email, password_hash, name, role, jurisdiction_id)
        VALUES ('priority-second-admin@example.com', 'hash', '다른담당', 'ADMIN',
          (SELECT jurisdiction_id FROM jurisdictions WHERE name = '부천시'))
        """);
    token =
        jwt.issue(users.findByEmail("priority-second-admin@example.com").orElseThrow())
            .accessToken();
    var repeated =
        designate().andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertThat(mapper.readTree(repeated).get("result"))
        .isEqualTo(mapper.readTree(original).get("result"));
    assertThat(designations.count()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT designated_by_user_id FROM priority_inspection_designations WHERE application_id = ?",
                Long.class,
                applicationId))
        .isEqualTo(operatorId);
  }

  @ParameterizedTest
  @ValueSource(strings = {"RECEIVED", "SCHEDULED", "IN_PROGRESS", "COMPLETED"})
  void designationDoesNotChangeInspectionStatusOrAssignment(String state) throws Exception {
    jdbc.update(
        "UPDATE inspections SET status = ?, scheduled_at = ?, inspector_user_id = ?, pre_visit_notice = '유지할 안내' WHERE inspection_id = ?",
        state,
        NOW.plusDays(1),
        operatorId,
        inspectionId);
    var before =
        jdbc.queryForMap("SELECT * FROM inspections WHERE inspection_id = ?", inspectionId);
    designate().andExpect(status().isOk());
    assertThat(jdbc.queryForMap("SELECT * FROM inspections WHERE inspection_id = ?", inspectionId))
        .isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(strings = {"서울특별시 중구 세종대로 110", "부천시민로 1", "서울특별시 부천시 길주로 210"})
  void rejectsOtherJurisdictionsAndMisleadingAddressPrefixes(String address) throws Exception {
    jdbc.update(
        "UPDATE inspection_applications SET address = ? WHERE application_id = ?",
        address,
        applicationId);
    designate()
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PRIORITY_INSPECTION_403_1"));
    assertThat(designations.count()).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"부천시 길주로 210", "경기 부천시 길주로 210", "경기도 부천시 길주로 210"})
  void allowsBucheonAddressForms(String address) throws Exception {
    jdbc.update(
        "UPDATE inspection_applications SET address = ? WHERE application_id = ?",
        address,
        applicationId);
    designate().andExpect(status().isOk());
  }

  @Test
  void rejectsOperatorWithoutJurisdictionEvenWhenDesignationExists() throws Exception {
    designate().andExpect(status().isOk());
    jdbc.update("UPDATE users SET jurisdiction_id = NULL WHERE user_id = ?", operatorId);
    designate()
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PRIORITY_INSPECTION_403_1"));
  }

  @Test
  void requiresAdminAuthentication() throws Exception {
    mvc.perform(put(path()).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            put(path())
                .header("Authorization", "Bearer invalid-token")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            put(path())
                .header("Authorization", "Bearer " + jwt.issue(resident).accessToken())
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());
    assertThat(designations.count()).isZero();
  }

  @Test
  void validatesApplicationId() throws Exception {
    for (String id : List.of("0", "-1", "abc", "9223372036854775808", "9223372036854775807")) {
      mvc.perform(
              put("/api/admin/priority-inspections/" + id + "/designation")
                  .header("Authorization", "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"designated\":true}"))
          .andExpect(status().is(id.equals("9223372036854775807") ? 404 : 400));
    }
    assertThat(designations.count()).isZero();
  }

  @Test
  void requiresPutAndSupportsOnlyJsonResponse() throws Exception {
    mvc.perform(
            post(path())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isMethodNotAllowed());
    mvc.perform(
            put(path())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_XML)
                .content("{}"))
        .andExpect(status().isNotAcceptable());
    assertThat(designations.count()).isZero();
  }

  // 저장 후 오류 발생 시 지정 정보 롤백
  @Test
  void rollsBackDesignationWhenPersistenceFailsAfterFlush() throws Exception {
    doAnswer(
            invocation -> {
              entityManager.persist(invocation.getArgument(0));
              entityManager.flush();
              throw new IllegalStateException("simulated designation failure");
            })
        .when(designations)
        .saveAndFlush(any());
    designate().andExpect(status().isInternalServerError());
    assertThat(designations.count()).isZero();
    assertThat(applications.findById(applicationId).orElseThrow().getInternalMemo())
        .isEqualTo("유지할 내부 메모");
    assertThat(inspections.findById(inspectionId).orElseThrow().getStatus().name())
        .isEqualTo("RECEIVED");
  }

  // 동시 지정 시 동일한 최초 지정 결과 반환
  @Test
  void concurrentRequestsCreateOneDesignationAndReturnSameResult() throws Exception {
    var gate = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first =
          executor.submit(
              () -> {
                gate.await();
                return designate()
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
              });
      var second =
          executor.submit(
              () -> {
                gate.await();
                return designate()
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
              });
      gate.countDown();
      var firstResult = mapper.readTree(first.get(20, TimeUnit.SECONDS)).get("result");
      var secondResult = mapper.readTree(second.get(20, TimeUnit.SECONDS)).get("result");
      assertThat(firstResult).isEqualTo(secondResult);
      assertThat(designations.count()).isEqualTo(1);
    }
  }

  @Test
  void swaggerDocumentsPutEndpointAndPriorityManagementGroup() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/priority-inspections/{applicationId}/designation'].put.requestBody")
                .exists())
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/priority-inspections/{applicationId}/designation'].put.tags[0]")
                .value("운영자-우선점검 관리"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/priority-inspections/{applicationId}/designation'].put.responses['200'].content['application/json'].examples.priorityDesignated.value.code")
                .value("PRIORITY_INSPECTION_200_1"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/priority-inspections/{applicationId}/designation'].put.responses['200'].content['application/json'].examples.priorityCancelled.value.code")
                .value("PRIORITY_INSPECTION_200_2"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/priority-inspections/{applicationId}/designation'].put.responses['404'].description")
                .value("현장점검 신청을 찾을 수 없습니다."));
  }

  // 지정 취소 후 신청과 점검 유지
  @Test
  void cancelsDesignationAndPreservesApplicationAndInspection() throws Exception {
    designate().andExpect(status().isOk());
    var beforeApplication =
        jdbc.queryForMap(
            "SELECT * FROM inspection_applications WHERE application_id = ?", applicationId);
    var beforeInspection =
        jdbc.queryForMap("SELECT * FROM inspections WHERE inspection_id = ?", inspectionId);
    for (int attempt = 0; attempt < 2; attempt++) {
      updateDesignation(false)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("PRIORITY_INSPECTION_200_2"))
          .andExpect(jsonPath("$.result.applicationId").value(applicationId))
          .andExpect(
              jsonPath("$.result.receiptNo")
                  .value(String.format(Locale.ROOT, "%06d", applicationId)))
          .andExpect(jsonPath("$.result.designated").value(false))
          .andExpect(jsonPath("$.result.designatedAt").isEmpty());
    }
    assertThat(designations.count()).isZero();
    assertThat(
            jdbc.queryForMap(
                "SELECT * FROM inspection_applications WHERE application_id = ?", applicationId))
        .isEqualTo(beforeApplication);
    assertThat(jdbc.queryForMap("SELECT * FROM inspections WHERE inspection_id = ?", inspectionId))
        .isEqualTo(beforeInspection);
    assertThat(applicationHistories.count()).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inspection_status_histories", Long.class))
        .isZero();
  }

  @Test
  void canCancelUndesignatedApplicationAndDesignateAgain() throws Exception {
    updateDesignation(false).andExpect(status().isOk());
    assertThat(designations.count()).isZero();
    designate().andExpect(status().isOk());
    updateDesignation(false).andExpect(status().isOk());
    when(inspectionClock.instant()).thenReturn(NOW.plusDays(1).atZone(SEOUL).toInstant());
    designate()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.designated").value(true))
        .andExpect(jsonPath("$.result.designatedAt").value("2030-01-03T14:00:00"));
    assertThat(designations.count()).isEqualTo(1);
  }

  @Test
  void cancellationRequiresCurrentJurisdictionAndAdminRole() throws Exception {
    designate().andExpect(status().isOk());
    var operatorToken = token;
    token = jwt.issue(resident).accessToken();
    updateDesignation(false).andExpect(status().isForbidden());
    token = operatorToken;
    jdbc.update("UPDATE users SET jurisdiction_id = NULL WHERE user_id = ?", operatorId);
    updateDesignation(false)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PRIORITY_INSPECTION_403_1"));
    assertThat(designations.count()).isEqualTo(1);
  }

  @Test
  void rejectsMissingOrInvalidDesignationBodyWithoutChangingExistingDesignation() throws Exception {
    designate().andExpect(status().isOk());
    for (String body : List.of("", "{}", "{\"designated\":null}", "{", "{\"designated\":{}}")) {
      mvc.perform(
              put(path())
                  .header("Authorization", "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("COMMON_400_2"));
    }
    mvc.perform(
            put(path())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.TEXT_PLAIN)
                .content("false"))
        .andExpect(status().isUnsupportedMediaType());
    assertThat(designations.count()).isEqualTo(1);
  }

  // 취소 실패 시 기존 지정 복원
  @Test
  void rollsBackCancellationWhenPersistenceFailsAfterFlush() throws Exception {
    designate().andExpect(status().isOk());
    doAnswer(
            invocation -> {
              entityManager.flush();
              throw new IllegalStateException("simulated cancellation failure");
            })
        .when(designations)
        .flush();
    updateDesignation(false).andExpect(status().isInternalServerError());
    assertThat(designations.count()).isEqualTo(1);
  }

  @Test
  void concurrentCancellationRequestsBothSucceed() throws Exception {
    designate().andExpect(status().isOk());
    var gate = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first =
          executor.submit(
              () -> {
                gate.await();
                return updateDesignation(false)
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
              });
      var second =
          executor.submit(
              () -> {
                gate.await();
                return updateDesignation(false)
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
              });
      gate.countDown();
      assertThat(mapper.readTree(first.get(20, TimeUnit.SECONDS)).get("result"))
          .isEqualTo(mapper.readTree(second.get(20, TimeUnit.SECONDS)).get("result"));
      assertThat(designations.count()).isZero();
    }
  }
}
