package com.flout.server.domain.inspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.inspection.entity.*;
import com.flout.server.domain.inspection.repository.InspectionRepository;
import com.flout.server.domain.inspectionapplication.dto.request.CreateInspectionApplicationRequest;
import com.flout.server.domain.inspectionapplication.entity.*;
import com.flout.server.domain.inspectionapplication.repository.InspectionApplicationRepository;
import com.flout.server.domain.priorityinspection.entity.PriorityInspectionDesignation;
import com.flout.server.domain.priorityinspection.repository.PriorityInspectionDesignationRepository;
import com.flout.server.domain.riskassessment.entity.*;
import com.flout.server.domain.riskassessment.repository.RiskAssessmentRepository;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:inspection-list;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false"
    })
@AutoConfigureMockMvc
class InspectionListTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired RiskAssessmentRepository assessments;
  @Autowired InspectionApplicationRepository applications;
  @Autowired InspectionRepository inspections;
  @Autowired PriorityInspectionDesignationRepository designations;
  @Autowired JwtService jwt;
  User resident;
  User operator;
  User secondOperator;
  String token;

  @BeforeEach
  void setup() {
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
    resident = users.saveAndFlush(User.resident("list-resident@example.com", "hash", "주민"));
    for (String email : List.of("list-admin@example.com", "list-second@example.com")) {
      jdbc.update(
          """
          INSERT INTO users(email, password_hash, name, role, jurisdiction_id)
          VALUES (?, 'hash', ?, 'ADMIN', (SELECT jurisdiction_id FROM jurisdictions WHERE name = '부천시'))
          """,
          email,
          email.equals("list-admin@example.com") ? "김담당" : "이담당");
    }
    operator = users.findByEmail("list-admin@example.com").orElseThrow();
    secondOperator = users.findByEmail("list-second@example.com").orElseThrow();
    token = jwt.issue(operator).accessToken();
  }

  record Created(Long inspectionId, Long applicationId) {}

  @Test
  void detailOpenApiDeclaresNullableFields() throws Exception {
    var response = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    for (String field :
        List.of("detailAddress", "preferredVisitDate", "preferredTimeSlot", "inspectorName")) {
      response.andExpect(
          jsonPath("$.components.schemas.InspectionDetailResponse.properties." + field + ".type")
              .value(hasItems("string", "null")));
    }
    response.andExpect(
        jsonPath("$.components.schemas.InspectionDetailResponse.properties.inspectorId.type")
            .value(hasItems("integer", "null")));
  }

  @Test
  void returnsAssignmentDetailWithResidentPreferencesAndAssignedInspector() throws Exception {
    var target =
        create(
            "부천시 원미구 길주로 53",
            InspectionStatus.SCHEDULED,
            LocalDate.of(2026, 11, 3),
            PreferredTimeSlot.MORNING,
            secondOperator.getId());
    var before = jdbc.queryForList("SELECT * FROM inspections");
    mvc.perform(
            get("/api/admin/inspections/{inspectionId}", target.inspectionId())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("INSPECTION_200_4"))
        .andExpect(jsonPath("$.result.inspectionId").value(target.inspectionId()))
        .andExpect(jsonPath("$.result.applicationId").value(target.applicationId()))
        .andExpect(
            jsonPath("$.result.receiptNo")
                .value(String.format(Locale.ROOT, "%06d", target.applicationId())))
        .andExpect(jsonPath("$.result.applicantName").value("주민"))
        .andExpect(jsonPath("$.result.address").value("부천시 원미구 길주로 53"))
        .andExpect(jsonPath("$.result.detailAddress").value("101호"))
        .andExpect(jsonPath("$.result.contact").value("010-0000-0000"))
        .andExpect(jsonPath("$.result.preferredVisitDate").value("2026-11-03"))
        .andExpect(jsonPath("$.result.preferredTimeSlot").value("MORNING"))
        .andExpect(jsonPath("$.result.inspectorId").value(secondOperator.getId()))
        .andExpect(jsonPath("$.result.inspectorName").value("이담당"));
    assertThat(jdbc.queryForList("SELECT * FROM inspections")).isEqualTo(before);
  }

  @Test
  void detailAllowsMissingPreferencesAndUnassignedInspector() throws Exception {
    var target = create("부천시 소사구 경인로 1", InspectionStatus.RECEIVED, null, null, null);
    mvc.perform(
            get("/api/admin/inspections/{inspectionId}", target.inspectionId())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.preferredVisitDate").value(nullValue()))
        .andExpect(jsonPath("$.result.preferredTimeSlot").value(nullValue()))
        .andExpect(jsonPath("$.result.inspectorId").value(nullValue()))
        .andExpect(jsonPath("$.result.inspectorName").value(nullValue()));
  }

  @Test
  void detailRejectsInvalidMissingUnauthorizedAndOutsideJurisdiction() throws Exception {
    var outside = create("서울특별시 부천시 원미구 길주로 1", InspectionStatus.RECEIVED, null, null, null);
    mvc.perform(
            get("/api/admin/inspections/{inspectionId}", outside.inspectionId())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("INSPECTION_403_2"));
    mvc.perform(get("/api/admin/inspections/0").header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/admin/inspections/not-a-number").header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/admin/inspections/9223372036854775807")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INSPECTION_404_1"));
    mvc.perform(get("/api/admin/inspections/1")).andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/admin/inspections/1")
                .header("Authorization", "Bearer " + jwt.issue(resident).accessToken()))
        .andExpect(status().isForbidden());
    jdbc.update("UPDATE users SET jurisdiction_id = NULL WHERE user_id = ?", operator.getId());
    mvc.perform(
            get("/api/admin/inspections/{inspectionId}", outside.inspectionId())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  // 신청 ID와 점검 ID를 별도로 생성해 목록에 연결
  private Created create(
      String address,
      InspectionStatus status,
      LocalDate date,
      PreferredTimeSlot slot,
      Long inspectorId) {
    var assessment =
        assessments.saveAndFlush(
            RiskAssessment.completedDemo(resident, address, "101호", RiskLevel.HIGH));
    var request =
        new CreateInspectionApplicationRequest(
            assessment.getId(),
            null,
            new BigDecimal("37.5"),
            new BigDecimal("126.7"),
            "010-0000-0000",
            OccupancyType.OWNER,
            FloodExperience.YES,
            false,
            "검색 대상 아닌 사유",
            date,
            slot,
            null);
    var application =
        applications.saveAndFlush(
            InspectionApplication.received(
                resident,
                assessment,
                request,
                "IA-" + UUID.randomUUID().toString().substring(0, 17)));
    var inspection = inspections.saveAndFlush(Inspection.received(application));
    jdbc.update(
        "UPDATE inspections SET status = ?, inspector_user_id = ?, scheduled_at = ? WHERE inspection_id = ?",
        status.name(),
        inspectorId,
        inspectorId == null ? null : LocalDateTime.of(2030, 1, 1, 14, 0),
        inspection.getId());
    return new Created(inspection.getId(), application.getId());
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder list() {
    return get("/api/admin/inspections").header("Authorization", "Bearer " + token);
  }

  @Test
  void returnsOnlyDistrictAndPreservesStoredDataWithinJurisdiction() throws Exception {
    var first =
        create(
            "경기도 부천시 원미구 길주로 53",
            InspectionStatus.RECEIVED,
            LocalDate.of(2026, 11, 3),
            PreferredTimeSlot.MORNING,
            null);
    create(
        "경기 부천시 소사구 경인로 92",
        InspectionStatus.SCHEDULED,
        null,
        PreferredTimeSlot.ANY,
        operator.getId());
    var last = create("부천시 오정구 오정로 252", InspectionStatus.COMPLETED, null, null, null);
    create("서울특별시 부천시 원미구 길주로 53", InspectionStatus.RECEIVED, null, null, null);
    create("부천시민로 1", InspectionStatus.RECEIVED, null, null, null);
    var app = applications.findById(first.applicationId()).orElseThrow();
    var designation =
        PriorityInspectionDesignation.designated(
            app, operator, LocalDateTime.of(2026, 10, 30, 10, 0));
    designation.changeOrder(1, operator, LocalDateTime.of(2026, 10, 30, 10, 0));
    designations.saveAndFlush(designation);
    var beforeApps =
        jdbc.queryForList("SELECT * FROM inspection_applications ORDER BY application_id");
    var beforeInspections = jdbc.queryForList("SELECT * FROM inspections ORDER BY inspection_id");
    var beforeDesignations = jdbc.queryForList("SELECT * FROM priority_inspection_designations");
    mvc.perform(list().param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.inspections[0].applicationId").value(first.applicationId()))
        .andExpect(jsonPath("$.result.inspections[0].designated").value(true))
        .andExpect(jsonPath("$.result.totalElements").value(3));
    var response =
        mvc.perform(list())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("INSPECTION_200_3"))
            .andExpect(jsonPath("$.result.totalElements").value(3))
            .andExpect(
                jsonPath("$.result.inspections[1].applicationId").value(last.applicationId()))
            .andExpect(jsonPath("$.result.inspections[1].inspectionId").value(last.inspectionId()))
            .andExpect(jsonPath("$.result.inspections[1].address").value("오정구"))
            .andExpect(jsonPath("$.result.inspections[1].preferredVisitDate").isEmpty())
            .andExpect(jsonPath("$.result.inspections[1].preferredTimeSlot").isEmpty())
            .andExpect(jsonPath("$.result.inspections[1].inspectorName").isEmpty())
            .andExpect(jsonPath("$.result.inspections[2].address").value("소사구"))
            .andExpect(jsonPath("$.result.inspections[2].inspectorName").value("김담당"))
            .andExpect(jsonPath("$.result.inspections[0].address").value("원미구"))
            .andExpect(jsonPath("$.result.inspections[0].designated").value(true))
            .andExpect(
                jsonPath("$.result.inspections[0].receiptNo")
                    .value(String.format(Locale.ROOT, "%06d", first.applicationId())))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(response).doesNotContain("길주로", "경인로", "오정로", "101호");
    assertThat(jdbc.queryForList("SELECT * FROM inspection_applications ORDER BY application_id"))
        .isEqualTo(beforeApps);
    assertThat(jdbc.queryForList("SELECT * FROM inspections ORDER BY inspection_id"))
        .isEqualTo(beforeInspections);
    assertThat(jdbc.queryForList("SELECT * FROM priority_inspection_designations"))
        .isEqualTo(beforeDesignations);
  }

  @Test
  void combinesFiltersAndSearchesOriginalAddressAndPaddedReceipt() throws Exception {
    var target =
        create(
            "부천시 원미구 길주로 53",
            InspectionStatus.RECEIVED,
            LocalDate.of(2026, 11, 3),
            PreferredTimeSlot.MORNING,
            null);
    var assigned =
        create(
            "부천시 원미구 길주로 530",
            InspectionStatus.SCHEDULED,
            LocalDate.of(2026, 11, 4),
            PreferredTimeSlot.AFTERNOON,
            operator.getId());
    create(
        "부천시 소사구 길주로 53",
        InspectionStatus.RECEIVED,
        LocalDate.of(2026, 11, 3),
        PreferredTimeSlot.ANY,
        secondOperator.getId());
    mvc.perform(
            list()
                .param("keyword", "길주로 53")
                .param("district", "원미구")
                .param("status", "RECEIVED")
                .param("visitDate", "2026-11-03"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalElements").value(1))
        .andExpect(jsonPath("$.result.inspections[0].applicationId").value(target.applicationId()));
    mvc.perform(
            list()
                .param("inspectorUserId", operator.getId().toString())
                .param("visitDate", "2026-11-04"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalElements").value(1))
        .andExpect(
            jsonPath("$.result.inspections[0].applicationId").value(assigned.applicationId()))
        .andExpect(jsonPath("$.result.inspections[0].preferredTimeSlot").value("AFTERNOON"));
    mvc.perform(list().param("keyword", String.format(Locale.ROOT, "%06d", target.applicationId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalElements").value(1));
    // 날짜 하나로 배정·미배정 모두 조회하며 다른 날짜는 제외
    mvc.perform(list().param("visitDate", "2026-11-03"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalElements").value(2));
    mvc.perform(list())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalElements").value(3));
    mvc.perform(list().param("status", "ALL"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalElements").value(3));
    mvc.perform(list().param("keyword", "검색 대상 아닌 사유"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.inspections").isEmpty());
  }

  @ParameterizedTest
  @EnumSource(InspectionStatus.class)
  void filtersByInspectionStatusIndependentlyOfAssignment(InspectionStatus selected)
      throws Exception {
    for (var state : InspectionStatus.values()) create("부천시 원미구 길주로 1", state, null, null, null);
    mvc.perform(list().param("status", selected.name()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalElements").value(1))
        .andExpect(jsonPath("$.result.inspections[0].status").value(selected.name()));
  }

  @Test
  void handlesUnknownDistrictWithoutExposingRoadNamesAndPaginates() throws Exception {
    create("부천시 원미구로 53", InspectionStatus.RECEIVED, null, null, null);
    var last = create("부천시 소사구 원미구로 1", InspectionStatus.RECEIVED, null, null, null);
    mvc.perform(list().param("page", "0").param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalElements").value(2))
        .andExpect(jsonPath("$.result.totalPages").value(2))
        .andExpect(jsonPath("$.result.inspections[0].applicationId").value(last.applicationId()))
        .andExpect(jsonPath("$.result.inspections[0].address").value("소사구"));
    mvc.perform(list().param("page", "1").param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.inspections[0].address").isEmpty());
    mvc.perform(list().param("district", "원미구"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalElements").value(0));
    mvc.perform(list().param("page", "100"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.inspections").isEmpty())
        .andExpect(jsonPath("$.result.totalElements").value(2));
    mvc.perform(list().param("visitDate", "2026-11-01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.inspections").isEmpty());
  }

  @ParameterizedTest
  @CsvSource({
    "page,-1",
    "size,0",
    "size,101",
    "district,강남구",
    "status,WAITING",
    "inspectorUserId,0",
    "inspectorUserId,-1",
    "inspectorUserId,abc",
    "visitDate,invalid",
    "visitDate,2026-02-30"
  })
  void rejectsInvalidParameters(String field, String value) throws Exception {
    mvc.perform(list().param(field, value))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_400_3"));
  }

  @Test
  void rejectsTooLongKeyword() throws Exception {
    mvc.perform(list().param("keyword", "가".repeat(256))).andExpect(status().isBadRequest());
  }

  @Test
  void checksAuthenticationRoleAndJurisdiction() throws Exception {
    mvc.perform(get("/api/admin/inspections")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/admin/inspections").header("Authorization", "Bearer invalid-token"))
        .andExpect(status().isUnauthorized());
    token = jwt.issue(resident).accessToken();
    mvc.perform(list()).andExpect(status().isForbidden());
    token = jwt.issue(operator).accessToken();
    jdbc.update("UPDATE users SET jurisdiction_id = NULL WHERE user_id = ?", operator.getId());
    mvc.perform(list())
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("INSPECTION_403_2"));
  }

  @Test
  void returnsEmptyResultAndDocumentsSwagger() throws Exception {
    mvc.perform(list())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.inspections").isEmpty())
        .andExpect(jsonPath("$.result.totalElements").value(0))
        .andExpect(jsonPath("$.result.totalPages").value(0));
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.paths['/api/admin/inspections'].get.parameters[*].name")
                .value(
                    org.hamcrest.Matchers.contains(
                        "status",
                        "district",
                        "inspectorUserId",
                        "visitDate",
                        "keyword",
                        "page",
                        "size")))
        .andExpect(
            jsonPath("$.paths['/api/admin/inspections'].get.parameters[0].schema.enum")
                .value(
                    org.hamcrest.Matchers.contains(
                        "ALL", "RECEIVED", "SCHEDULED", "IN_PROGRESS", "COMPLETED")))
        .andExpect(jsonPath("$.paths['/api/admin/inspections'].get.tags[0]").value("운영자-현장점검 관리"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/inspections'].get.responses['200'].content['application/json'].examples.inspectionList.value.result.inspections[0].address")
                .value("원미구"))
        .andExpect(jsonPath("$.paths['/api/admin/inspections'].get.responses['400']").exists())
        .andExpect(jsonPath("$.paths['/api/admin/inspections'].get.responses['401']").exists())
        .andExpect(jsonPath("$.paths['/api/admin/inspections'].get.responses['403']").exists());
    mvc.perform(list().accept(MediaType.APPLICATION_XML)).andExpect(status().isNotAcceptable());
  }
}
