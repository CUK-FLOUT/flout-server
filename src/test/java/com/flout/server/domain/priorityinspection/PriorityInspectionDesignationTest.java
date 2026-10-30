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

  // 일괄 지정·취소 요청
  private org.springframework.test.web.servlet.ResultActions bulk(
      List<Long> ids, boolean designated) throws Exception {
    return mvc.perform(
        put("/api/admin/priority-inspections/designation")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                mapper.writeValueAsString(
                    Map.of("applicationIds", ids, "designated", designated))));
  }

  @Test
  void bulkAppendsInRequestOrderAndPreservesExistingDesignationOnRetry() throws Exception {
    var second = additionalApplication("부천시 원미구 두번째");
    var third = additionalApplication("경기 부천시 소사구 세번째");
    designate();
    var original =
        jdbc.queryForMap(
            "SELECT * FROM priority_inspection_designations WHERE application_id = ?",
            applicationId);
    var applicationBefore =
        jdbc.queryForMap(
            "SELECT * FROM inspection_applications WHERE application_id = ?", applicationId);
    when(inspectionClock.instant()).thenReturn(NOW.plusHours(1).atZone(SEOUL).toInstant());
    var ids = List.of(third, applicationId, second);
    bulk(ids, true)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.applications[0].applicationId").value(third))
        .andExpect(
            jsonPath("$.result.applications[0].receiptNo")
                .value(String.format(Locale.ROOT, "%06d", third)))
        .andExpect(jsonPath("$.result.applications[1].applicationId").value(applicationId))
        .andExpect(jsonPath("$.result.applications[2].applicationId").value(second));
    assertThat(savedOrder()).containsExactly(applicationId, third, second);
    assertThat(
            jdbc.queryForMap(
                "SELECT * FROM priority_inspection_designations WHERE application_id = ?",
                applicationId))
        .isEqualTo(original);
    var beforeRetry =
        jdbc.queryForList(
            "SELECT * FROM priority_inspection_designations ORDER BY selection_order");
    when(inspectionClock.instant()).thenReturn(NOW.plusHours(2).atZone(SEOUL).toInstant());
    bulk(ids, true).andExpect(status().isOk());
    assertThat(
            jdbc.queryForList(
                "SELECT * FROM priority_inspection_designations ORDER BY selection_order"))
        .isEqualTo(beforeRetry);
    assertThat(
            jdbc.queryForMap(
                "SELECT * FROM inspection_applications WHERE application_id = ?", applicationId))
        .isEqualTo(applicationBefore);
  }

  @Test
  void bulkCancellationCompactsOrderAndIsIdempotent() throws Exception {
    var second = additionalApplication("부천시 두번째");
    var third = additionalApplication("부천시 세번째");
    var fourth = additionalApplication("부천시 네번째");
    var notDesignated = additionalApplication("부천시 미지정");
    bulk(List.of(applicationId, second, third, fourth), true).andExpect(status().isOk());
    var ids = List.of(third, applicationId, notDesignated);
    bulk(ids, false)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("PRIORITY_INSPECTION_200_2"))
        .andExpect(jsonPath("$.result.applications[0].designated").value(false))
        .andExpect(jsonPath("$.result.applications[0].designatedAt").isEmpty());
    assertThat(savedOrder()).containsExactly(second, fourth);
    assertThat(
            jdbc.queryForList(
                "SELECT selection_order FROM priority_inspection_designations ORDER BY selection_order",
                Integer.class))
        .containsExactly(1, 2);
    var beforeRetry =
        jdbc.queryForList(
            "SELECT * FROM priority_inspection_designations ORDER BY selection_order");
    when(inspectionClock.instant()).thenReturn(NOW.plusHours(1).atZone(SEOUL).toInstant());
    bulk(ids, false).andExpect(status().isOk());
    assertThat(
            jdbc.queryForList(
                "SELECT * FROM priority_inspection_designations ORDER BY selection_order"))
        .isEqualTo(beforeRetry);
  }

  @Test
  void bulkValidatesAllApplicationsBeforeAnyChanges() throws Exception {
    var outside = additionalApplication("서울특별시 부천시 관할밖");
    bulk(List.of(applicationId, Long.MAX_VALUE), true).andExpect(status().isNotFound());
    assertThat(designations.count()).isZero();
    bulk(List.of(applicationId, outside), true).andExpect(status().isForbidden());
    assertThat(designations.count()).isZero();
    designate();
    bulk(List.of(applicationId, Long.MAX_VALUE), false).andExpect(status().isNotFound());
    bulk(List.of(applicationId, outside), false).andExpect(status().isForbidden());
    assertThat(savedOrder()).containsExactly(applicationId);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"applicationIds\":[],\"designated\":true}",
        "{\"applicationIds\":null,\"designated\":true}",
        "{\"applicationIds\":[null],\"designated\":true}",
        "{\"applicationIds\":[0],\"designated\":true}",
        "{\"applicationIds\":[-1],\"designated\":true}",
        "{\"applicationIds\":[1]}",
        "{\"applicationIds\":[1],\"designated\":null}",
        "{\"applicationIds\":[1,1],\"designated\":true}"
      })
  void bulkRejectsInvalidBodies(String body) throws Exception {
    mvc.perform(
            put("/api/admin/priority-inspections/designation")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
    assertThat(designations.count()).isZero();
  }

  @Test
  void bulkRollsBackEarlierInsertsWhenLaterInsertFails() throws Exception {
    var second = additionalApplication("부천시 두번째");
    var count = new java.util.concurrent.atomic.AtomicInteger();
    doAnswer(
            invocation -> {
              var item = invocation.getArgument(0);
              entityManager.persist(item);
              entityManager.flush();
              if (count.incrementAndGet() == 2)
                throw new IllegalStateException("simulated batch failure");
              return item;
            })
        .when(designations)
        .saveAndFlush(any());
    bulk(List.of(applicationId, second), true).andExpect(status().isInternalServerError());
    assertThat(designations.count()).isZero();
  }

  @Test
  void bulkRollsBackCancellationWhenFlushFails() throws Exception {
    var second = additionalApplication("부천시 두번째");
    bulk(List.of(applicationId, second), true).andExpect(status().isOk());
    var before =
        jdbc.queryForList(
            "SELECT * FROM priority_inspection_designations ORDER BY selection_order");
    doAnswer(
            invocation -> {
              entityManager.flush();
              throw new IllegalStateException("simulated cancellation failure");
            })
        .when(designations)
        .flush();
    bulk(List.of(applicationId, second), false).andExpect(status().isInternalServerError());
    assertThat(
            jdbc.queryForList(
                "SELECT * FROM priority_inspection_designations ORDER BY selection_order"))
        .isEqualTo(before);
  }

  @Test
  void bulkRequiresAdminAuthenticationAndDocumentsListResponse() throws Exception {
    mvc.perform(
            put("/api/admin/priority-inspections/designation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"applicationIds\":[1],\"designated\":true}"))
        .andExpect(status().isUnauthorized());
    var adminToken = token;
    token = jwt.issue(resident).accessToken();
    bulk(List.of(applicationId), true).andExpect(status().isForbidden());
    token = adminToken;
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/priority-inspections/designation'].put.responses['200'].content['application/json'].examples.priorityDesignated.value.result.applications[0].receiptNo")
                .value("000005"))
        .andExpect(
            jsonPath("$.paths['/api/admin/priority-inspections/designation'].put.responses['404']")
                .exists());
  }

  // 최대 건수는 조회 페이지 크기와 독립적으로 검증
  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void bulkAllowsFiveHundredIdsAndRejectsFiveHundredOne(boolean designated) throws Exception {
    var ids = java.util.stream.LongStream.range(100000, 100500).boxed().toList();
    bulk(ids, designated).andExpect(status().isNotFound());
    var tooMany = java.util.stream.LongStream.range(100000, 100501).boxed().toList();
    bulk(tooMany, designated)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_400_2"))
        .andExpect(jsonPath("$.message").value("신청 ID는 한 번에 최대 500개까지 전달할 수 있습니다."));
    assertThat(designations.count()).isZero();
  }

  @Test
  void swaggerDocumentsBulkLimitAndErrorExample() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                    "$.components.schemas.UpdatePriorityInspectionDesignationsRequest.properties.applicationIds.maxItems")
                .value(500))
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/priority-inspections/designation'].put.responses['400'].content['application/json'].examples.batchLimitExceeded.value.message")
                .value("신청 ID는 한 번에 최대 500개까지 전달할 수 있습니다."));
  }

  // 신청 상세 조회 요청
  private org.springframework.test.web.servlet.ResultActions detail(Long id) throws Exception {
    return mvc.perform(
        get("/api/admin/priority-inspections/" + id).header("Authorization", "Bearer " + token));
  }

  @ParameterizedTest
  @ValueSource(strings = {"HIGH", "CAUTION", "LOW"})
  void detailReturnsSavedInformationAndMapsLinkedRiskWithoutMutations(String risk)
      throws Exception {
    jdbc.update(
        "UPDATE risk_assessments SET risk_level = ? WHERE assessment_id = "
            + "(SELECT assessment_id FROM inspection_applications WHERE application_id = ?)",
        risk,
        applicationId);
    // 최신 분석이 아닌 신청에 연결된 분석을 사용
    assessments.saveAndFlush(
        RiskAssessment.completedDemo(resident, "부천시 다른 주소", null, RiskLevel.LOW));
    var beforeApplication =
        jdbc.queryForMap(
            "SELECT * FROM inspection_applications WHERE application_id = ?", applicationId);
    var beforeInspection =
        jdbc.queryForMap("SELECT * FROM inspections WHERE inspection_id = ?", inspectionId);
    var need = Map.of("HIGH", "우선점검 권장", "CAUTION", "점검 검토", "LOW", "일반 점검").get(risk);
    detail(applicationId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("PRIORITY_INSPECTION_200_5"))
        .andExpect(jsonPath("$.result.applicationId").value(applicationId))
        .andExpect(
            jsonPath("$.result.receiptNo").value(String.format(Locale.ROOT, "%06d", applicationId)))
        .andExpect(jsonPath("$.result.applicantName").value("주민"))
        .andExpect(jsonPath("$.result.address").value("경기도 부천시 원미구 길주로 210"))
        .andExpect(jsonPath("$.result.detailAddress").value("101호"))
        .andExpect(jsonPath("$.result.inspectionNeed").value(need))
        .andExpect(jsonPath("$.result.riskLevel").value(risk))
        .andExpect(jsonPath("$.result.floodExperience").value("YES"))
        .andExpect(jsonPath("$.result.requestReason").value("점검 요청"))
        .andExpect(jsonPath("$.result.internalMemo").value("유지할 내부 메모"))
        .andExpect(jsonPath("$.result.nearbyApplicationCount").value(0));
    assertThat(designations.count()).isZero();
    bulk(List.of(applicationId), true).andExpect(status().isOk());
    var beforeDesignation = jdbc.queryForList("SELECT * FROM priority_inspection_designations");
    detail(applicationId).andExpect(status().isOk());
    assertThat(
            jdbc.queryForMap(
                "SELECT * FROM inspection_applications WHERE application_id = ?", applicationId))
        .isEqualTo(beforeApplication);
    assertThat(jdbc.queryForMap("SELECT * FROM inspections WHERE inspection_id = ?", inspectionId))
        .isEqualTo(beforeInspection);
    assertThat(jdbc.queryForList("SELECT * FROM priority_inspection_designations"))
        .isEqualTo(beforeDesignation);
    assertThat(applicationHistories.count()).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inspection_status_histories", Long.class))
        .isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"YES", "NO", "UNKNOWN"})
  void detailPreservesNullFieldsAndFloodExperience(String experience) throws Exception {
    jdbc.update(
        "UPDATE inspection_applications SET flood_experience = ?, request_reason = NULL, internal_memo = NULL, detail_address = NULL WHERE application_id = ?",
        experience,
        applicationId);
    detail(applicationId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.floodExperience").value(experience))
        .andExpect(jsonPath("$.result.requestReason").isEmpty())
        .andExpect(jsonPath("$.result.internalMemo").isEmpty())
        .andExpect(jsonPath("$.result.detailAddress").isEmpty());
  }

  @Test
  void detailUsesSameNearbyCountsAsCandidateList() throws Exception {
    additionalApplication("부천시 소사구 동일 좌표");
    var inside = additionalApplication("경기 부천시 오정구 반경 안");
    var outside = additionalApplication("경기도 부천시 원미구 반경 밖");
    additionalApplication("서울특별시 부천시 다른 관할");
    additionalApplication("부천시민로 다른 관할");
    jdbc.update(
        "UPDATE inspection_applications SET latitude = ? WHERE application_id = ?",
        new BigDecimal("37.4964750"),
        inside);
    jdbc.update(
        "UPDATE inspection_applications SET latitude = ? WHERE application_id = ?",
        new BigDecimal("37.4964850"),
        outside);
    detail(applicationId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.nearbyApplicationCount").value(2));
    mvc.perform(
            get("/api/admin/priority-inspections")
                .header("Authorization", "Bearer " + token)
                .param("district", "원미구")
                .param("designated", "false")
                .param("keyword", "길주로 210")
                .param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.applications[0].nearbyApplicationCount").value(2));
  }

  @Test
  void detailValidatesAuthenticationIdAndJurisdiction() throws Exception {
    mvc.perform(get("/api/admin/priority-inspections/" + applicationId))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/admin/priority-inspections/" + applicationId)
                .header("Authorization", "Bearer invalid-token"))
        .andExpect(status().isUnauthorized());
    var adminToken = token;
    token = jwt.issue(resident).accessToken();
    detail(applicationId).andExpect(status().isForbidden());
    token = adminToken;
    for (String id : List.of("0", "-1", "abc", "9223372036854775808")) {
      mvc.perform(
              get("/api/admin/priority-inspections/" + id)
                  .header("Authorization", "Bearer " + token))
          .andExpect(status().isBadRequest());
    }
    detail(Long.MAX_VALUE)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("APPLICATION_404_2"));
    jdbc.update("UPDATE users SET jurisdiction_id = NULL WHERE user_id = ?", operatorId);
    detail(applicationId)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PRIORITY_INSPECTION_403_1"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"서울특별시 중구", "서울특별시 부천시 길주로", "부천시민로 1"})
  void detailRejectsApplicationsOutsideJurisdiction(String address) throws Exception {
    jdbc.update(
        "UPDATE inspection_applications SET address = ? WHERE application_id = ?",
        address,
        applicationId);
    detail(applicationId)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PRIORITY_INSPECTION_403_1"));
  }

  @Test
  void detailSwaggerAndResponseTypeAreDocumented() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/priority-inspections/{applicationId}'].get.responses['404'].description")
                .value("현장점검 신청을 찾을 수 없습니다."))
        .andExpect(
            jsonPath("$.paths['/api/admin/priority-inspections/{applicationId}'].get.summary")
                .value("우선점검 신청정보 조회"))
        .andExpect(
            jsonPath("$.paths['/api/admin/priority-inspections/{applicationId}'].get.tags[0]")
                .value("운영자-우선점검 관리"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/priority-inspections/{applicationId}'].get.responses['200'].content['application/json'].examples.priorityDetail.value.code")
                .value("PRIORITY_INSPECTION_200_5"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/priority-inspections/{applicationId}'].get.responses['400']")
                .exists())
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/priority-inspections/{applicationId}'].get.responses['401']")
                .exists())
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/priority-inspections/{applicationId}'].get.responses['403']")
                .exists())
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/priority-inspections/{applicationId}'].get.responses['404']")
                .exists());
    mvc.perform(
            get("/api/admin/priority-inspections/" + applicationId)
                .header("Authorization", "Bearer " + token)
                .accept(MediaType.APPLICATION_XML))
        .andExpect(status().isNotAcceptable());
  }

  private String path() {
    return "/api/admin/priority-inspections/designation";
  }

  @Test
  void singleDesignationRouteIsRemovedFromServerAndSwagger() throws Exception {
    mvc.perform(
            put("/api/admin/priority-inspections/" + applicationId + "/designation")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"designated\":true}"))
        .andExpect(status().isNotFound());
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.paths['/api/admin/priority-inspections/{applicationId}/designation']")
                .doesNotExist());
    assertThat(designations.count()).isZero();
  }

  @Test
  void candidatesExcludeCompletedRegardlessOfDesignationAndKeepOtherStates() throws Exception {
    var scheduled = additionalApplication("부천시 원미구 예정");
    var inProgress = additionalApplication("부천시 원미구 진행");
    var completed = additionalApplication("부천시 원미구 완료");
    var noInspection = additionalApplication("부천시 원미구 점검없음");
    for (var entry :
        Map.of(scheduled, "SCHEDULED", inProgress, "IN_PROGRESS", completed, "COMPLETED")
            .entrySet()) {
      var inspection =
          inspections.saveAndFlush(
              Inspection.received(applications.findById(entry.getKey()).orElseThrow()));
      jdbc.update(
          "UPDATE inspections SET status = ? WHERE inspection_id = ?",
          entry.getValue(),
          inspection.getId());
    }
    jdbc.update(
        "UPDATE inspections SET status = 'COMPLETED' WHERE inspection_id = ?", inspectionId);
    bulk(List.of(applicationId, scheduled), true).andExpect(status().isOk());
    var before =
        jdbc.queryForList("SELECT * FROM priority_inspection_designations ORDER BY application_id");
    mvc.perform(get("/api/admin/priority-inspections").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalElements").value(3))
        .andExpect(jsonPath("$.result.applications.length()").value(3))
        .andExpect(jsonPath("$.result.applications[0].applicationId").value(scheduled))
        .andExpect(jsonPath("$.result.applications[1].applicationId").value(noInspection))
        .andExpect(jsonPath("$.result.applications[2].applicationId").value(inProgress))
        .andExpect(jsonPath("$.result.applications[0].nearbyApplicationCount").value(4));
    for (boolean designated : List.of(true, false)) {
      mvc.perform(
              get("/api/admin/priority-inspections")
                  .header("Authorization", "Bearer " + token)
                  .param("designated", String.valueOf(designated))
                  .param("size", "1"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result.totalElements").value(designated ? 1 : 2))
          .andExpect(jsonPath("$.result.totalPages").value(designated ? 1 : 2));
    }
    mvc.perform(
            get("/api/admin/priority-inspections")
                .header("Authorization", "Bearer " + token)
                .param("keyword", "완료"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.applications").isEmpty())
        .andExpect(jsonPath("$.result.totalElements").value(0));
    mvc.perform(
            get("/api/admin/priority-inspections")
                .header("Authorization", "Bearer " + token)
                .param("page", "3")
                .param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.applications").isEmpty())
        .andExpect(jsonPath("$.result.totalElements").value(3))
        .andExpect(jsonPath("$.result.totalPages").value(3));
    assertThat(
            jdbc.queryForList(
                "SELECT * FROM priority_inspection_designations ORDER BY application_id"))
        .isEqualTo(before);
    assertThat(inspections.count()).isEqualTo(4);
  }

  @Test
  void candidatesPutDesignatedFirstBeforePaginationAndPreserveSelectionOrder() throws Exception {
    Long olderUndesignated = additionalApplication("부천시 원미구 길주로 211");
    Long newerDesignated = additionalApplication("부천시 원미구 길주로 212");
    Long newestUndesignated = additionalApplication("부천시 원미구 길주로 213");
    bulk(List.of(applicationId, newerDesignated), true).andExpect(status().isOk());
    var before =
        jdbc.queryForList("SELECT * FROM priority_inspection_designations ORDER BY application_id");
    List<Long> expected =
        List.of(newerDesignated, applicationId, newestUndesignated, olderUndesignated);
    for (int page = 0; page < expected.size(); page++) {
      mvc.perform(
              get("/api/admin/priority-inspections")
                  .header("Authorization", "Bearer " + token)
                  .param("keyword", "길주로")
                  .param("district", "원미구")
                  .param("riskLevel", "HIGH")
                  .param("page", String.valueOf(page))
                  .param("size", "1"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result.applications[0].applicationId").value(expected.get(page)))
          .andExpect(jsonPath("$.result.applications[0].designated").value(page < 2))
          .andExpect(jsonPath("$.result.totalElements").value(4))
          .andExpect(jsonPath("$.result.totalPages").value(4));
    }
    for (boolean designated : List.of(true, false)) {
      var ids =
          designated
              ? List.of(newerDesignated, applicationId)
              : List.of(newestUndesignated, olderUndesignated);
      mvc.perform(
              get("/api/admin/priority-inspections")
                  .header("Authorization", "Bearer " + token)
                  .param("designated", String.valueOf(designated)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result.applications[0].applicationId").value(ids.get(0)))
          .andExpect(jsonPath("$.result.applications[1].applicationId").value(ids.get(1)))
          .andExpect(jsonPath("$.result.totalElements").value(2));
    }
    assertThat(
            jdbc.queryForList(
                "SELECT * FROM priority_inspection_designations ORDER BY application_id"))
        .isEqualTo(before);
  }

  @Test
  void candidatesCountWithinOneKilometerAcrossSearchAndPages() throws Exception {
    additionalApplication("경기 부천시 같은 위치");
    Long inside = additionalApplication("부천시 반경 안");
    Long outside = additionalApplication("경기도 부천시 반경 밖");
    additionalApplication("서울특별시 부천시 다른 관할");
    additionalApplication("부천시민로 다른 관할");
    jdbc.update(
        "UPDATE inspection_applications SET latitude = ? WHERE application_id = ?",
        new BigDecimal("37.4964750"),
        inside); // 약 999.6m
    jdbc.update(
        "UPDATE inspection_applications SET latitude = ? WHERE application_id = ?",
        new BigDecimal("37.4964850"),
        outside); // 약 1000.8m
    designate();
    mvc.perform(
            get("/api/admin/priority-inspections")
                .header("Authorization", "Bearer " + token)
                .param("keyword", String.format(Locale.ROOT, "%06d", applicationId))
                .param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalElements").value(1))
        .andExpect(jsonPath("$.result.applications[0].applicationId").value(applicationId))
        .andExpect(jsonPath("$.result.applications[0].nearbyApplicationCount").value(2))
        .andExpect(jsonPath("$.result.applications[0].designated").value(true));
    mvc.perform(
            get("/api/admin/priority-inspections")
                .header("Authorization", "Bearer " + token)
                .param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalElements").value(4))
        .andExpect(jsonPath("$.result.totalPages").value(4))
        .andExpect(jsonPath("$.result.applications[0].applicationId").value(applicationId));
  }

  @Test
  void candidatesExcludeSelfAndReturnEmptySearch() throws Exception {
    mvc.perform(get("/api/admin/priority-inspections").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.applications[0].nearbyApplicationCount").value(0))
        .andExpect(jsonPath("$.result.applications[0].designated").value(false));
    mvc.perform(
            get("/api/admin/priority-inspections")
                .header("Authorization", "Bearer " + token)
                .param("keyword", "없는검색어"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.applications").isEmpty())
        .andExpect(jsonPath("$.result.totalElements").value(0));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "101", "-1"})
  void candidatesRejectInvalidSize(String size) throws Exception {
    mvc.perform(
            get("/api/admin/priority-inspections")
                .header("Authorization", "Bearer " + token)
                .param("size", size))
        .andExpect(status().isBadRequest());
  }

  @Test
  void candidatesRequireAdminAndAuthentication() throws Exception {
    mvc.perform(get("/api/admin/priority-inspections")).andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/admin/priority-inspections")
                .header("Authorization", "Bearer " + jwt.issue(resident).accessToken()))
        .andExpect(status().isForbidden());
  }

  @Test
  void candidatesUseLongitudeAsWellAsLatitude() throws Exception {
    Long eastInside = additionalApplication("부천시 동쪽 인근");
    Long eastOutside = additionalApplication("부천시 동쪽 외곽");
    jdbc.update(
        "UPDATE inspection_applications SET longitude = ? WHERE application_id = ?",
        new BigDecimal("126.8085672"),
        eastInside);
    jdbc.update(
        "UPDATE inspection_applications SET longitude = ? WHERE application_id = ?",
        new BigDecimal("126.8105672"),
        eastOutside);
    mvc.perform(
            get("/api/admin/priority-inspections")
                .header("Authorization", "Bearer " + token)
                .param("keyword", "길주로"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalElements").value(1))
        .andExpect(jsonPath("$.result.applications[0].nearbyApplicationCount").value(1));
  }

  @Test
  void candidatesValidatePageKeywordAndJurisdiction() throws Exception {
    mvc.perform(
            get("/api/admin/priority-inspections")
                .header("Authorization", "Bearer " + token)
                .param("page", "-1"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/admin/priority-inspections")
                .header("Authorization", "Bearer " + token)
                .param("keyword", "가".repeat(256)))
        .andExpect(status().isBadRequest());
    jdbc.update("UPDATE users SET jurisdiction_id = NULL WHERE user_id = ?", operatorId);
    mvc.perform(get("/api/admin/priority-inspections").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void candidatesExcludeReasonFromSearchAndHandlePageBeyondResults() throws Exception {
    mvc.perform(
            get("/api/admin/priority-inspections")
                .header("Authorization", "Bearer " + token)
                .param("keyword", "점검 요청"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalElements").value(0))
        .andExpect(jsonPath("$.result.applications").isEmpty());
    mvc.perform(
            get("/api/admin/priority-inspections")
                .header("Authorization", "Bearer " + token)
                .param("keyword", "길주로"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalElements").value(1))
        .andExpect(jsonPath("$.result.applications[0].requestReason").value("점검 요청"));
    mvc.perform(
            get("/api/admin/priority-inspections")
                .header("Authorization", "Bearer " + token)
                .param("page", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.applications").isEmpty())
        .andExpect(jsonPath("$.result.totalElements").value(1));
  }

  @Test
  void candidatesSwaggerDocumentsOneKilometerCountAndResponses() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.paths['/api/admin/priority-inspections'].get.summary")
                .value("우선점검 후보 검색·목록 조회"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/priority-inspections'].get.responses['200'].content['application/json'].examples.priorityCandidates.value.code")
                .value("PRIORITY_INSPECTION_200_4"))
        .andExpect(
            jsonPath("$.paths['/api/admin/priority-inspections'].get.responses['400']").exists())
        .andExpect(
            jsonPath("$.paths['/api/admin/priority-inspections'].get.responses['403']").exists());
  }

  @Test
  void candidatesCombineDistrictRiskAndDesignationWithoutChangingNearbyCounts() throws Exception {
    Long caution = additionalApplication("경기 부천시 원미구 중앙로 1");
    additionalApplication("부천시 소사구 중앙로 2");
    Long low = additionalApplication("경기도 부천시 오정구 중앙로 3");
    jdbc.update(
        "UPDATE risk_assessments SET risk_level = 'CAUTION' WHERE assessment_id = "
            + "(SELECT assessment_id FROM inspection_applications WHERE application_id = ?)",
        caution);
    jdbc.update(
        "UPDATE risk_assessments SET risk_level = 'LOW' WHERE assessment_id = "
            + "(SELECT assessment_id FROM inspection_applications WHERE application_id = ?)",
        low);
    designate();
    mvc.perform(
            get("/api/admin/priority-inspections")
                .header("Authorization", "Bearer " + token)
                .param("district", "원미구")
                .param("riskLevel", "HIGH")
                .param("designated", "true")
                .param("keyword", "길주로")
                .param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalElements").value(1))
        .andExpect(jsonPath("$.result.totalPages").value(1))
        .andExpect(jsonPath("$.result.applications[0].applicationId").value(applicationId))
        .andExpect(jsonPath("$.result.applications[0].nearbyApplicationCount").value(3))
        .andExpect(jsonPath("$.result.applications[0].designated").value(true));
    mvc.perform(
            get("/api/admin/priority-inspections")
                .header("Authorization", "Bearer " + token)
                .param("district", "오정구")
                .param("riskLevel", "LOW")
                .param("designated", "false"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalElements").value(1))
        .andExpect(jsonPath("$.result.applications[0].applicationId").value(low))
        .andExpect(jsonPath("$.result.applications[0].nearbyApplicationCount").value(3));
    mvc.perform(
            get("/api/admin/priority-inspections")
                .header("Authorization", "Bearer " + token)
                .param("riskLevel", "CAUTION"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalElements").value(1))
        .andExpect(jsonPath("$.result.applications[0].applicationId").value(caution));
    mvc.perform(
            get("/api/admin/priority-inspections")
                .header("Authorization", "Bearer " + token)
                .param("designated", "false"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalElements").value(3));
    mvc.perform(get("/api/admin/priority-inspections").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalElements").value(4));
  }

  @Test
  void districtFilterMatchesAdministrativeDistrictNotRoadNames() throws Exception {
    Long sosa = additionalApplication("부천시 소사구 원미구로 1");
    additionalApplication("부천시 원미구로 2");
    additionalApplication("서울특별시 원미구 중앙로 3");
    mvc.perform(
            get("/api/admin/priority-inspections")
                .header("Authorization", "Bearer " + token)
                .param("district", "원미구"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalElements").value(1))
        .andExpect(jsonPath("$.result.applications[0].applicationId").value(applicationId));
    mvc.perform(
            get("/api/admin/priority-inspections")
                .header("Authorization", "Bearer " + token)
                .param("district", "소사구"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalElements").value(1))
        .andExpect(jsonPath("$.result.applications[0].applicationId").value(sosa));
  }

  @Test
  void candidatesRejectInvalidFiltersAndReturnEmptyForNoMatch() throws Exception {
    for (var filter :
        Map.of("district", "강남구", "riskLevel", "MEDIUM", "designated", "invalid").entrySet()) {
      mvc.perform(
              get("/api/admin/priority-inspections")
                  .header("Authorization", "Bearer " + token)
                  .param(filter.getKey(), filter.getValue()))
          .andExpect(status().isBadRequest());
    }
    mvc.perform(
            get("/api/admin/priority-inspections")
                .header("Authorization", "Bearer " + token)
                .param("riskLevel", "LOW"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.applications").isEmpty())
        .andExpect(jsonPath("$.result.totalElements").value(0));
  }

  private org.springframework.test.web.servlet.ResultActions designate() throws Exception {
    return updateDesignation(true);
  }

  private org.springframework.test.web.servlet.ResultActions updateDesignation(boolean designated)
      throws Exception {
    return bulk(List.of(applicationId), designated);
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
        .andExpect(jsonPath("$.result.applications[0].applicationId").value(applicationId))
        .andExpect(
            jsonPath("$.result.applications[0].receiptNo")
                .value(String.format(Locale.ROOT, "%06d", applicationId)))
        .andExpect(jsonPath("$.result.applications[0].designated").value(true))
        .andExpect(jsonPath("$.result.applications[0].selectionMemo").doesNotExist())
        .andExpect(jsonPath("$.result.applications[0].designatedAt").value("2030-01-02T14:00:00"));
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
                .content(
                    "{\"applicationIds\":["
                        + applicationId
                        + "],\"designated\":true,\"selectionMemo\":\"저장하지 않을 메모\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.applications[0].selectionMemo").doesNotExist());
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
              put(path())
                  .header("Authorization", "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"applicationIds\":[" + id + "],\"designated\":true}"))
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
            jsonPath("$.paths['/api/admin/priority-inspections/designation'].put.requestBody")
                .exists())
        .andExpect(
            jsonPath("$.paths['/api/admin/priority-inspections/designation'].put.tags[0]")
                .value("운영자-우선점검 관리"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/priority-inspections/designation'].put.responses['200'].content['application/json'].examples.priorityDesignated.value.code")
                .value("PRIORITY_INSPECTION_200_1"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/priority-inspections/designation'].put.responses['200'].content['application/json'].examples.priorityCancelled.value.code")
                .value("PRIORITY_INSPECTION_200_2"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/priority-inspections/designation'].put.responses['404'].description")
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
          .andExpect(jsonPath("$.result.applications[0].applicationId").value(applicationId))
          .andExpect(
              jsonPath("$.result.applications[0].receiptNo")
                  .value(String.format(Locale.ROOT, "%06d", applicationId)))
          .andExpect(jsonPath("$.result.applications[0].designated").value(false))
          .andExpect(jsonPath("$.result.applications[0].designatedAt").isEmpty());
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
        .andExpect(jsonPath("$.result.applications[0].designated").value(true))
        .andExpect(jsonPath("$.result.applications[0].designatedAt").value("2030-01-03T14:00:00"));
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

  private Long additionalApplication(String address) {
    var assessment =
        assessments.saveAndFlush(
            RiskAssessment.completedDemo(resident, address, "102호", RiskLevel.HIGH));
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
    return applications
        .saveAndFlush(
            InspectionApplication.received(
                resident,
                assessment,
                request,
                "IA-" + UUID.randomUUID().toString().substring(0, 17)))
        .getId();
  }

  private void designateApplication(Long id) throws Exception {
    bulk(List.of(id), true).andExpect(status().isOk());
  }

  private org.springframework.test.web.servlet.ResultActions saveOrder(List<Long> ids)
      throws Exception {
    return mvc.perform(
        put("/api/admin/priority-inspections/order")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("applicationIds", ids))));
  }

  private List<Long> savedOrder() {
    return jdbc.queryForList(
        "SELECT application_id FROM priority_inspection_designations ORDER BY selection_order",
        Long.class);
  }

  // 선정 순서 저장 후 신청과 최초 지정 정보 유지
  @Test
  void savesOrderAndPreservesOriginalDesignationAndApplication() throws Exception {
    var second = additionalApplication("부천시 길주로 211");
    designateApplication(applicationId);
    designateApplication(second);
    var original =
        jdbc.queryForMap(
            "SELECT * FROM inspection_applications WHERE application_id = ?", applicationId);
    when(inspectionClock.instant()).thenReturn(NOW.plusHours(1).atZone(SEOUL).toInstant());
    saveOrder(List.of(second, applicationId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("PRIORITY_INSPECTION_200_3"))
        .andExpect(jsonPath("$.result.applications[0].applicationId").value(second))
        .andExpect(jsonPath("$.result.applications[0].selectionOrder").value(1))
        .andExpect(
            jsonPath("$.result.applications[1].receiptNo")
                .value(String.format(Locale.ROOT, "%06d", applicationId)))
        .andExpect(jsonPath("$.result.applications[1].selectionOrder").value(2));
    assertThat(savedOrder()).containsExactly(second, applicationId);
    var row =
        jdbc.queryForMap(
            "SELECT * FROM priority_inspection_designations WHERE application_id = ?",
            applicationId);
    assertThat(((Timestamp) row.get("designated_at")).toLocalDateTime()).isEqualTo(NOW);
    assertThat(((Number) row.get("designated_by_user_id")).longValue()).isEqualTo(operatorId);
    assertThat(((Timestamp) row.get("order_updated_at")).toLocalDateTime())
        .isEqualTo(NOW.plusHours(1));
    assertThat(((Number) row.get("order_updated_by_user_id")).longValue()).isEqualTo(operatorId);
    assertThat(
            jdbc.queryForMap(
                "SELECT * FROM inspection_applications WHERE application_id = ?", applicationId))
        .isEqualTo(original);
    assertThat(inspections.findById(inspectionId).orElseThrow().getStatus().name())
        .isEqualTo("RECEIVED");
    when(inspectionClock.instant()).thenReturn(NOW.plusDays(1).atZone(SEOUL).toInstant());
    saveOrder(List.of(second, applicationId)).andExpect(status().isOk());
    assertThat(
            jdbc.queryForMap(
                "SELECT * FROM priority_inspection_designations WHERE application_id = ?",
                applicationId))
        .isEqualTo(row);
  }

  @Test
  void validatesOrderListAndDoesNotSavePartialChanges() throws Exception {
    var second = additionalApplication("부천시 길주로 211");
    designateApplication(applicationId);
    designateApplication(second);
    var original = savedOrder();
    for (String body :
        List.of(
            "",
            "{}",
            "{\"applicationIds\":null}",
            "{\"applicationIds\":[]}",
            "{\"applicationIds\":[null]}",
            "{\"applicationIds\":[0]}",
            "{\"applicationIds\":[-1]}",
            "{\"applicationIds\":[\"abc\"]}")) {
      mvc.perform(
              put("/api/admin/priority-inspections/order")
                  .header("Authorization", "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest());
    }
    saveOrder(List.of(applicationId, applicationId))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PRIORITY_INSPECTION_400_1"));
    saveOrder(List.of(applicationId))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PRIORITY_INSPECTION_409_2"));
    saveOrder(List.of(applicationId, Long.MAX_VALUE)).andExpect(status().isNotFound());
    var undesignated = additionalApplication("부천시 길주로 212");
    saveOrder(List.of(undesignated, second))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PRIORITY_INSPECTION_409_1"));
    var other = additionalApplication("서울특별시 중구 세종대로 1");
    saveOrder(List.of(other, applicationId)).andExpect(status().isForbidden());
    assertThat(savedOrder()).isEqualTo(original);
  }

  @Test
  void orderRequiresAdminAndJurisdiction() throws Exception {
    designateApplication(applicationId);
    mvc.perform(
            put("/api/admin/priority-inspections/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"applicationIds\":[1]}"))
        .andExpect(status().isUnauthorized());
    var operatorToken = token;
    token = jwt.issue(resident).accessToken();
    saveOrder(List.of(applicationId)).andExpect(status().isForbidden());
    token = operatorToken;
    jdbc.update("UPDATE users SET jurisdiction_id = NULL WHERE user_id = ?", operatorId);
    saveOrder(List.of(applicationId)).andExpect(status().isForbidden());
  }

  @Test
  void designationAppendsAndCancellationClosesOrderGap() throws Exception {
    var second = additionalApplication("부천시 길주로 211");
    var third = additionalApplication("부천시 길주로 212");
    designateApplication(applicationId);
    designateApplication(second);
    saveOrder(List.of(second, applicationId)).andExpect(status().isOk());
    designateApplication(third);
    assertThat(savedOrder()).containsExactly(second, applicationId, third);
    saveOrder(List.of(second, applicationId)).andExpect(status().isConflict());
    updateDesignation(false).andExpect(status().isOk());
    assertThat(savedOrder()).containsExactly(second, third);
    assertThat(
            jdbc.queryForList(
                "SELECT selection_order FROM priority_inspection_designations ORDER BY selection_order",
                Integer.class))
        .containsExactly(1, 2);
    designateApplication(applicationId);
    assertThat(savedOrder()).containsExactly(second, third, applicationId);
  }

  @Test
  void orderFailureRollsBackEveryRankAndAuditChange() throws Exception {
    var second = additionalApplication("부천시 길주로 211");
    designateApplication(applicationId);
    designateApplication(second);
    var original =
        jdbc.queryForList("SELECT * FROM priority_inspection_designations ORDER BY application_id");
    doAnswer(
            invocation -> {
              entityManager.flush();
              throw new IllegalStateException("simulated order failure");
            })
        .when(designations)
        .flush();
    saveOrder(List.of(second, applicationId)).andExpect(status().isInternalServerError());
    assertThat(
            jdbc.queryForList(
                "SELECT * FROM priority_inspection_designations ORDER BY application_id"))
        .isEqualTo(original);
  }

  // 동시 저장 후에도 완전한 순위 유지
  @Test
  void concurrentOrdersAndDesignationKeepDistinctRanks() throws Exception {
    var second = additionalApplication("부천시 길주로 211");
    var third = additionalApplication("부천시 길주로 212");
    designateApplication(applicationId);
    designateApplication(second);
    var gate = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(3)) {
      var first =
          executor.submit(
              () -> {
                gate.await();
                return saveOrder(List.of(second, applicationId))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              });
      var next =
          executor.submit(
              () -> {
                gate.await();
                return saveOrder(List.of(applicationId, second))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              });
      var added =
          executor.submit(
              () -> {
                gate.await();
                designateApplication(third);
                return true;
              });
      gate.countDown();
      assertThat(first.get(20, TimeUnit.SECONDS)).isIn(200, 409);
      assertThat(next.get(20, TimeUnit.SECONDS)).isIn(200, 409);
      assertThat(added.get(20, TimeUnit.SECONDS)).isTrue();
    }
    assertThat(savedOrder()).containsExactlyInAnyOrder(applicationId, second, third);
    assertThat(
            jdbc.queryForList(
                "SELECT selection_order FROM priority_inspection_designations ORDER BY selection_order",
                Integer.class))
        .containsExactly(1, 2, 3);
  }

  @Test
  void swaggerDocumentsOrderEndpoint() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.paths['/api/admin/priority-inspections/order'].put.tags[0]")
                .value("운영자-우선점검 관리"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/priority-inspections/order'].put.responses['200'].content['application/json'].examples.orderSaved.value.code")
                .value("PRIORITY_INSPECTION_200_3"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/priority-inspections/order'].put.responses['404'].description")
                .value("현장점검 신청을 찾을 수 없습니다."))
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/priority-inspections/order'].put.responses['409'].description")
                .value("우선점검 미지정 신청 포함 또는 전체 대상 목록 불일치"));
  }
}
