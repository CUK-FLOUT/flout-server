package com.flout.server.domain.supportapplication;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:support-list;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false",
      "app.s3.cleanup-interval-millis=3600000"
    })
@AutoConfigureMockMvc
class SupportListTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired ObjectMapper mapper;
  @Autowired JwtEncoder encoder;

  long supportId, adminId, residentId;
  String token, residentToken;
  static final String SCOPE = "출입구 물막이판 설치 지원";

  /** Seeds support applications for list filtering, jurisdiction, and pagination checks. */
  @BeforeEach
  void setup() {

    for (var table :
        List.of(
            "support_attachments",
            "support_application_status_histories",
            "support_applications",
            "support_programs",
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

  ResultActions list(String access, String keyword, int page, int size) throws Exception {
    var call =
        get("/api/admin/support-applications")
            .param("keyword", keyword)
            .param("page", Integer.toString(page))
            .param("size", Integer.toString(size));
    if (access != null) call.header("Authorization", "Bearer " + access);
    return mvc.perform(call);
  }

  long add(String name, String address, String state, String consent) {
    jdbc.update("INSERT INTO support_programs(name,created_at) VALUES('목록 검증용',CURRENT_TIMESTAMP)");
    var program = jdbc.queryForObject("SELECT MAX(program_id) FROM support_programs", Long.class);
    jdbc.update(
        """
      INSERT INTO support_applications(application_id,program_id,applicant_user_id,requested_facility,applicant_name,contact,address,occupancy_type,preferred_visit_date,preferred_visit_time,status,submitted_at,landlord_consent_status)
      SELECT application_id,?,applicant_user_id,requested_facility,?,contact,?,occupancy_type,preferred_visit_date,preferred_visit_time,?,submitted_at,?
      FROM support_applications WHERE support_application_id=?
      """,
        program,
        name,
        address,
        state,
        consent,
        supportId);
    return jdbc.queryForObject(
        "SELECT MAX(support_application_id) FROM support_applications", Long.class);
  }

  @Test
  void defaultsAndExactJsonFieldsUseRealSnapshotsWithoutChangingData() throws Exception {
    var before = jdbc.queryForList("SELECT * FROM support_applications");
    jdbc.update(
        "UPDATE inspection_applications SET applicant_name='다른 원본 이름', receipt_no='legacy-receipt'");
    var applicationId =
        jdbc.queryForObject(
            "SELECT application_id FROM support_applications WHERE support_application_id=?",
            Long.class,
            supportId);
    mvc.perform(get("/api/admin/support-applications").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.code").value("COMMON_200"))
        .andExpect(jsonPath("$.status").value(200))
        .andExpect(jsonPath("$.message").value("호출에 성공했습니다."))
        .andExpect(jsonPath("$.timeStamp").isString())
        .andExpect(jsonPath("$.result.length()").value(5))
        .andExpect(jsonPath("$.result.page").value(0))
        .andExpect(jsonPath("$.result.size").value(10))
        .andExpect(jsonPath("$.result.totalElements").value(1))
        .andExpect(jsonPath("$.result.totalPages").value(1))
        .andExpect(jsonPath("$.result.supportApplications.length()").value(1))
        .andExpect(jsonPath("$.result.supportApplications[0].length()").value(6))
        .andExpect(
            jsonPath("$.result.supportApplications[0].supportApplicationId").value(supportId))
        .andExpect(
            jsonPath("$.result.supportApplications[0].receiptNo")
                .value(String.format(Locale.ROOT, "%06d", applicationId)))
        .andExpect(jsonPath("$.result.supportApplications[0].applicantName").value("테스트주민"))
        .andExpect(jsonPath("$.result.supportApplications[0].requestedFacility").value("출입구 물막이판"))
        .andExpect(
            jsonPath("$.result.supportApplications[0].landlordConsentStatus").value("NOT_REQUIRED"))
        .andExpect(jsonPath("$.result.supportApplications[0].status").value("SUBMITTED"));
    assertThat(jdbc.queryForList("SELECT * FROM support_applications")).isEqualTo(before);
  }

  @Test
  void searchesReceiptAndNameAndReturnsEmptyForNoMatch() throws Exception {
    long id = add("김목록주민", "부천시 소사구", "APPROVED", "CONFIRMED");
    list(token, "  김목록  ", 0, 10)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalElements").value(1))
        .andExpect(jsonPath("$.result.supportApplications[0].supportApplicationId").value(id));
    var source =
        jdbc.queryForObject(
            "SELECT application_id FROM support_applications WHERE support_application_id=?",
            Long.class,
            supportId);
    list(token, String.format(Locale.ROOT, "%06d", source), 0, 10)
        .andExpect(jsonPath("$.result.totalElements").value(2));
    list(token, "없는주민", 0, 10)
        .andExpect(jsonPath("$.result.supportApplications").isEmpty())
        .andExpect(jsonPath("$.result.totalElements").value(0))
        .andExpect(jsonPath("$.result.totalPages").value(0));
  }

  /** Treats wildcard characters and SQL-like input as literal search text. */
  @Test
  void literalWildcardsAndSqlInjectionCannotBroadenResults() throws Exception {
    add("기호%_주민", "부천시 원미구", "SUBMITTED", "PENDING");
    for (var keyword : List.of("%", "_", "%_"))
      list(token, keyword, 0, 10)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result.totalElements").value(1));
    for (var keyword : List.of("' OR 1=1 --", "테스트'", "\\"))
      list(token, keyword, 0, 10)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result.totalElements").value(0));
  }

  /** Checks page boundaries, descending IDs, and totals under the same filter. */
  @Test
  void paginatesInStableSupportIdDescendingOrderWithMatchingFilteredTotals() throws Exception {
    var expected = new ArrayList<Long>();
    for (int i = 0; i < 12; i++)
      expected.add(add("검색대상" + i, "경기도 부천시 원미구", "SUBMITTED", "NOT_REQUIRED"));
    Collections.reverse(expected);
    var actual = new ArrayList<Long>();
    for (int page = 0; page < 3; page++) {
      var response =
          list(token, "검색대상", page, 5)
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.result.page").value(page))
              .andExpect(jsonPath("$.result.size").value(5))
              .andExpect(jsonPath("$.result.totalElements").value(12))
              .andExpect(jsonPath("$.result.totalPages").value(3))
              .andReturn();
      for (var item :
          mapper
              .readTree(response.getResponse().getContentAsString())
              .path("result")
              .path("supportApplications")) actual.add(item.path("supportApplicationId").asLong());
    }
    assertThat(actual).containsExactlyElementsOf(expected).doesNotHaveDuplicates();
    list(token, "검색대상", 99, 5)
        .andExpect(jsonPath("$.result.supportApplications").isEmpty())
        .andExpect(jsonPath("$.result.totalElements").value(12))
        .andExpect(jsonPath("$.result.totalPages").value(3));
    list(token, "", Integer.MAX_VALUE, 100)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.supportApplications").isEmpty());
  }

  /** Checks that out-of-jurisdiction and lookalike addresses are excluded from rows and totals. */
  @Test
  void jurisdictionAppliesToRowsAndCountAndRejectsLookalikeAddresses() throws Exception {
    for (var address : List.of("서울시 부천시", "경기도 수원시", "부천시청", "부천시민로", "경기도 부천시흥"))
      add("테스트주민", address, "APPROVED", "CONFIRMED");
    list(token, "테스트", 0, 10)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalElements").value(1))
        .andExpect(jsonPath("$.result.supportApplications.length()").value(1));
    add("유효주소", "  경기 부천시 오정구", "SUBMITTED", "PENDING");
    add("유효주소", "부천시 원미구", "SUBMITTED", "PENDING");
    list(token, "", 0, 10).andExpect(jsonPath("$.result.totalElements").value(3));
  }

  @ParameterizedTest
  @ValueSource(strings = {"APPROVED", "REJECTED", "SUBMITTED"})
  void mapsStatusesWithoutChangingStoredEnums(String state) throws Exception {
    jdbc.update("UPDATE support_applications SET status=?", state);
    list(token, "", 0, 10)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.supportApplications[0].status").value(state));
    assertThat(jdbc.queryForObject("SELECT status FROM support_applications", String.class))
        .isEqualTo(state);
  }

  @ParameterizedTest
  @ValueSource(strings = {"PENDING", "CONFIRMED", "NOT_REQUIRED"})
  void confirmedIsVerifiedOnlyInListResponse(String consent) throws Exception {
    jdbc.update("UPDATE support_applications SET landlord_consent_status=?", consent);
    list(token, "", 0, 10)
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.result.supportApplications[0].landlordConsentStatus")
                .value("CONFIRMED".equals(consent) ? "VERIFIED" : consent));
    assertThat(
            jdbc.queryForObject(
                "SELECT landlord_consent_status FROM support_applications", String.class))
        .isEqualTo(consent);
  }

  @Test
  void emptyTableReturnsZeroPageMetadata() throws Exception {
    jdbc.update("DELETE FROM support_applications");
    list(token, "", 0, 10)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.supportApplications").isEmpty())
        .andExpect(jsonPath("$.result.totalElements").value(0))
        .andExpect(jsonPath("$.result.totalPages").value(0));
  }

  @Test
  void validatesAllQueryBoundsAndTypesWithCommonParameterError() throws Exception {
    for (var values :
        List.of(
            new String[] {"page", "-1"},
            new String[] {"size", "0"},
            new String[] {"size", "101"},
            new String[] {"page", "no"},
            new String[] {"size", "1.5"},
            new String[] {"page", "2147483648"},
            new String[] {"keyword", "가".repeat(256)},
            new String[] {"keyword", " ".repeat(256)}))
      mvc.perform(
              get("/api/admin/support-applications")
                  .header("Authorization", "Bearer " + token)
                  .param(values[0], values[1]))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("COMMON_400_3"));
    list(token, " ".repeat(255), 0, 100).andExpect(status().isOk());
    list(token, "가".repeat(255), 0, 1).andExpect(status().isOk());
  }

  @Test
  void authenticationAndJurisdictionFollowExistingPolicy() throws Exception {
    list(null, "", 0, 10)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("COMMON_401_1"));
    list("invalid-token", "", 0, 10)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("COMMON_401_3"));
    list(residentToken, "", 0, 10)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("COMMON_403_1"));
    jdbc.update("UPDATE users SET jurisdiction_id=NULL WHERE user_id=?", adminId);
    list(token, "", 0, 10)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("COMMON_403_1"));
    jdbc.update("INSERT INTO jurisdictions(name) VALUES('타지역')");
    jdbc.update(
        "UPDATE users SET jurisdiction_id=(SELECT jurisdiction_id FROM jurisdictions WHERE name='타지역') WHERE user_id=?",
        adminId);
    list(token, "", 0, 10)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("COMMON_403_1"));
  }

  @Test
  void filteredListDoesNotAffectStatusCounts() throws Exception {
    add("다른주민", "부천시 소사구", "APPROVED", "CONFIRMED");
    list(token, "없는주민", 0, 1).andExpect(jsonPath("$.result.totalElements").value(0));
    mvc.perform(
            get("/api/admin/support-applications/status")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalCount").value(2))
        .andExpect(jsonPath("$.result.reviewingCount").value(1))
        .andExpect(jsonPath("$.result.approvedCount").value(1));
  }

  @Test
  void swaggerPublishesTheListContractAlongsideExistingStatusEndpoint() throws Exception {
    var response = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
    var paths = mapper.readTree(response.getResponse().getContentAsString()).path("paths");
    var operation = paths.path("/api/admin/support-applications").path("get");
    assertThat(operation.path("summary").asString()).isEqualTo("지원 신청 목록 조회·검색");
    var parameters = new ArrayList<String>();
    for (var parameter : operation.path("parameters"))
      parameters.add(parameter.path("name").asString());
    assertThat(parameters)
        .containsExactlyInAnyOrder(
            "keyword", "page", "size", "district", "status", "landlordConsentStatus");
    assertThat(operation.path("responses").has("400")).isTrue();
    assertThat(
            operation
                .path("responses")
                .path("200")
                .path("content")
                .path("application/json")
                .path("examples")
                .path("screenData")
                .path("value")
                .path("result")
                .has("supportApplications"))
        .isTrue();
    assertThat(paths.path("/api/admin/support-applications/status").has("get")).isTrue();
  }

  @Test
  void combinesDistrictStatusConsentAndKeywordWithMatchingPageCounts() throws Exception {
    var expected = new ArrayList<Long>();
    for (int i = 0; i < 7; i++)
      expected.add(
          add("필터주민" + i, "경기도 부천시 소사구 소사로 1", i % 2 == 0 ? "SUBMITTED" : "REVIEWING", "PENDING"));
    add("필터주민", "부천시 원미구", "REVIEWING", "PENDING");
    add("필터주민", "부천시 소사구", "APPROVED", "PENDING");
    add("필터주민", "부천시 소사구", "REVIEWING", "CONFIRMED");
    add("필터주민", "서울시 부천시 소사구", "REVIEWING", "PENDING");
    add("필터주민", "부천시 소사구청", "REVIEWING", "PENDING");
    Collections.reverse(expected);
    var actual = new ArrayList<Long>();
    for (int page = 0; page < 3; page++) {
      var response =
          mvc.perform(
                  get("/api/admin/support-applications")
                      .header("Authorization", "Bearer " + token)
                      .param("keyword", "필터주민")
                      .param("district", "소사구")
                      .param("status", "REVIEWING")
                      .param("landlordConsentStatus", "PENDING")
                      .param("page", String.valueOf(page))
                      .param("size", "3"))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.result.totalElements").value(7))
              .andExpect(jsonPath("$.result.totalPages").value(3))
              .andReturn();
      for (var row :
          mapper
              .readTree(response.getResponse().getContentAsString())
              .path("result")
              .path("supportApplications")) actual.add(row.path("supportApplicationId").asLong());
    }
    assertThat(actual).containsExactlyElementsOf(expected).doesNotHaveDuplicates();
  }

  @Test
  void individualFiltersAndConsentAliasesRespectPersistedValues() throws Exception {
    long approved = add("소사주민", " 경기 부천시 소사구", "APPROVED", "CONFIRMED");
    add("오정주민", "부천시 오정구", "REJECTED", "PENDING");
    for (var consent : List.of("VERIFIED", "CONFIRMED")) {
      mvc.perform(
              get("/api/admin/support-applications")
                  .header("Authorization", "Bearer " + token)
                  .param("landlordConsentStatus", consent))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result.totalElements").value(1))
          .andExpect(
              jsonPath("$.result.supportApplications[0].supportApplicationId").value(approved));
    }
    for (var filter :
        List.of(
            new String[] {"district", "원미구"},
            new String[] {"district", "오정구"},
            new String[] {"status", "APPROVED"},
            new String[] {"landlordConsentStatus", "NOT_REQUIRED"}))
      mvc.perform(
              get("/api/admin/support-applications")
                  .header("Authorization", "Bearer " + token)
                  .param(filter[0], filter[1]))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result.totalElements").value(1));
    mvc.perform(
            get("/api/admin/support-applications")
                .header("Authorization", "Bearer " + token)
                .param("district", " ")
                .param("status", "")
                .param("landlordConsentStatus", " "))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalElements").value(3));
  }

  @Test
  void rejectsInvalidFilterValuesWithoutBroadeningSearch() throws Exception {
    for (var filter :
        List.of(
            new String[] {"district", "서울"},
            new String[] {"district", "소사구|.*"},
            new String[] {"status", "DRAFT"},
            new String[] {"status", "unknown"},
            new String[] {"landlordConsentStatus", "UNKNOWN"}))
      mvc.perform(
              get("/api/admin/support-applications")
                  .header("Authorization", "Bearer " + token)
                  .param(filter[0], filter[1]))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("COMMON_400_3"));
    mvc.perform(
            get("/api/admin/support-applications")
                .header("Authorization", "Bearer " + token)
                .param("status", "REJECTED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalElements").value(0))
        .andExpect(jsonPath("$.result.totalPages").value(0));
  }
}
