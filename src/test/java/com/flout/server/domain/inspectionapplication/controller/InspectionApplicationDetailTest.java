package com.flout.server.domain.inspectionapplication.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:application-detail;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false"
    })
@AutoConfigureMockMvc
class InspectionApplicationDetailTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired RiskAssessmentRepository assessments;
  @Autowired InspectionApplicationRepository applications;
  @Autowired ApplicationInflowLocationRepository locations;
  @Autowired JwtService jwt;
  User resident;
  Long operatorId;
  Long applicationId;
  Long assessmentId;
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

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder detail() {
    return get("/api/admin/applications/{applicationId}", applicationId)
        .header("Authorization", "Bearer " + token);
  }

  /** 화면에 필요한 정보의 출처를 확인하며 조회가 신청·주민·분석·유입 위치를 변경하지 않는지 검증한다. */
  @Test
  void returnsScreenFieldsAndPreservesStoredData() throws Exception {
    var tables =
        List.of(
            "inspection_applications", "users", "risk_assessments", "application_inflow_locations");
    Map<String, Object> before = new HashMap<>();
    for (String table : tables) before.put(table, jdbc.queryForList("SELECT * FROM " + table));
    mvc.perform(detail())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("APPLICATION_200_2"))
        .andExpect(jsonPath("$.result.applicationId").value(applicationId))
        .andExpect(
            jsonPath("$.result.receiptNo").value(String.format(Locale.ROOT, "%06d", applicationId)))
        .andExpect(jsonPath("$.result.applicantName").value("김주민"))
        .andExpect(jsonPath("$.result.contact").value("010-1234-1234"))
        .andExpect(jsonPath("$.result.address").value("경기도 부천시 원미구 길주로 53"))
        .andExpect(jsonPath("$.result.detailAddress").value("101호"))
        .andExpect(jsonPath("$.result.occupancyType").value("TENANT"))
        .andExpect(jsonPath("$.result.riskLevel").value("HIGH"))
        .andExpect(jsonPath("$.result.floodExperience").value("YES"))
        .andExpect(jsonPath("$.result.inflowLocations").value(contains("BATHROOM", "DRAIN")))
        .andExpect(jsonPath("$.result.existingFacility").value(false))
        .andExpect(jsonPath("$.result.preferredVisitDate").value("2026-11-03"))
        .andExpect(jsonPath("$.result.requestReason").value("최근 배수구 역류가 있어 점검을 희망합니다."))
        .andExpect(jsonPath("$.result.preferredTimeSlot").value("MORNING"))
        .andExpect(jsonPath("$.result.floor").doesNotExist());
    for (String table : tables)
      assertThat(jdbc.queryForList("SELECT * FROM " + table)).isEqualTo(before.get(table));
  }

  /** 기존 nullable 데이터는 null로, 미입력 유입 위치는 빈 배열로 반환한다. */
  @Test
  void returnsNullsAndEmptyLocations() throws Exception {
    jdbc.update("DELETE FROM application_inflow_locations");
    jdbc.update(
        "UPDATE inspection_applications SET detail_address = NULL, preferred_visit_date = NULL, preferred_time_slot = NULL, request_reason = NULL WHERE application_id = ?",
        applicationId);
    mvc.perform(detail())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.detailAddress").value(nullValue()))
        .andExpect(jsonPath("$.result.preferredVisitDate").value(nullValue()))
        .andExpect(jsonPath("$.result.preferredTimeSlot").value(nullValue()))
        .andExpect(jsonPath("$.result.requestReason").value(nullValue()))
        .andExpect(jsonPath("$.result.inflowLocations").isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"서울특별시 부천시 원미구 길주로 53", "부천시민로 1", "인천광역시 부평구 1"})
  void rejectsOutsideAddresses(String address) throws Exception {
    jdbc.update(
        "UPDATE inspection_applications SET address = ? WHERE application_id = ?",
        address,
        applicationId);
    mvc.perform(detail())
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("APPLICATION_403_4"));
  }

  @Test
  void rejectsMissingAndUnsupportedJurisdiction() throws Exception {
    jdbc.update("UPDATE users SET jurisdiction_id = NULL WHERE user_id = ?", operatorId);
    mvc.perform(detail()).andExpect(status().isForbidden());
    if (jdbc.queryForObject("SELECT COUNT(*) FROM jurisdictions WHERE name = '서울시'", Integer.class)
        == 0) jdbc.update("INSERT INTO jurisdictions(name) VALUES ('서울시')");
    jdbc.update(
        "UPDATE users SET jurisdiction_id = (SELECT jurisdiction_id FROM jurisdictions WHERE name = '서울시') WHERE user_id = ?",
        operatorId);
    mvc.perform(detail())
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("APPLICATION_403_4"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
  void rejectsInvalidId(String id) throws Exception {
    mvc.perform(get("/api/admin/applications/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_400_3"));
  }

  @Test
  void rejectsMissingApplicationAndUnauthorizedAccess() throws Exception {
    mvc.perform(
            get("/api/admin/applications/9223372036854775807")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("APPLICATION_404_2"));
    mvc.perform(get("/api/admin/applications/" + applicationId))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/admin/applications/" + applicationId)
                .header("Authorization", "Bearer invalid"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/admin/applications/" + applicationId)
                .header("Authorization", "Bearer " + jwt.issue(resident).accessToken()))
        .andExpect(status().isForbidden());
  }

  /** 시간대 enum의 JSON null 허용을 포함하여 생성된 OpenAPI 명세를 검증한다. */
  @Test
  void documentsFieldsNullEnumAndErrors() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.paths['/api/admin/applications/{applicationId}'].get.tags[0]")
                .value("운영자-GIS 신청관리"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/applications/{applicationId}'].get.responses['403'].content['application/json'].examples.jurisdictionAccessDenied.value.code")
                .value("APPLICATION_403_4"))
        .andExpect(
            jsonPath(
                    "$.components.schemas.InspectionApplicationDetailResponse.properties.preferredTimeSlot.enum")
                .value(containsInAnyOrder("MORNING", "AFTERNOON", "ANY", null)))
        .andExpect(
            jsonPath(
                    "$.components.schemas.InspectionApplicationDetailResponse.properties.preferredTimeSlot.type")
                .value(hasItems("string", "null")));
  }
}
