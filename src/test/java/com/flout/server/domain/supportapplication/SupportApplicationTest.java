package com.flout.server.domain.supportapplication;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.supportapplication.repository.SupportApplicationRepository;
import com.flout.server.domain.supportprogram.entity.SupportProgram;
import com.flout.server.domain.supportprogram.repository.SupportProgramRepository;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:support-applications;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false",
      "app.s3.cleanup-interval-millis=3600000"
    })
@AutoConfigureMockMvc
class SupportApplicationTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired SupportProgramRepository programs;
  @MockitoSpyBean SupportApplicationRepository supports;
  @Autowired JwtService jwt;
  @Autowired JwtEncoder encoder;
  @Autowired ObjectMapper mapper;
  long residentId, applicationId, inspectionId, programId;
  String token;
  static final String ADDRESS = "경기도 부천시 원미구 길주로 210";
  static final String CONTACT = "010-0000-0000";

  @BeforeEach
  void setup() {
    for (String t :
        List.of(
            "support_applications",
            "support_programs",
            "inspection_results",
            "inspection_status_histories",
            "inspections",
            "application_status_histories",
            "inspection_applications",
            "risk_assessments",
            "refresh_tokens",
            "users")) jdbc.update("DELETE FROM " + t);
    var resident =
        users.saveAndFlush(
            User.resident("support-resident@example.test", "unused-test-hash", "테스트주민"));
    residentId = resident.getId();
    token = jwt.issue(resident).accessToken();
    jdbc.update(
        "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status) VALUES(?,?,'fixture','HIGH','COMPLETED')",
        residentId,
        ADDRESS);
    long risk =
        jdbc.queryForObject(
            "SELECT assessment_id FROM risk_assessments WHERE user_id=?", Long.class, residentId);
    jdbc.update(
        "INSERT INTO inspection_applications(applicant_name,applicant_user_id,assessment_id,receipt_no,address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,status) VALUES('김주민',?,?,'SUPPORT-TEST',?,37.5,126.76,?,'OWNER','NO',FALSE,'RECEIVED')",
        residentId,
        risk,
        ADDRESS,
        CONTACT);
    applicationId =
        jdbc.queryForObject(
            "SELECT application_id FROM inspection_applications WHERE assessment_id=?",
            Long.class,
            risk);
    jdbc.update(
        "INSERT INTO application_status_histories(application_id,status) VALUES(?,'RECEIVED')",
        applicationId);
    jdbc.update(
        "INSERT INTO inspections(application_id,status) VALUES(?,'IN_PROGRESS')", applicationId);
    inspectionId =
        jdbc.queryForObject(
            "SELECT inspection_id FROM inspections WHERE application_id=?",
            Long.class,
            applicationId);
    jdbc.update(
        "INSERT INTO inspection_status_histories(inspection_id,previous_status,status,changed_by_user_id,change_source,changed_at) VALUES(?,'RECEIVED','IN_PROGRESS',?,'MANUAL',CURRENT_TIMESTAMP)",
        inspectionId,
        residentId);
    jdbc.update(
        "INSERT INTO inspection_results(inspection_id,result_status,summary,published_at) VALUES(?,'PUBLISHED','preserved',CURRENT_TIMESTAMP)",
        inspectionId);
    programId =
        programs.saveAndFlush(SupportProgram.create("테스트 지원사업", "테스트 설명", "테스트 대상", null)).getId();
  }

  Map<String, Object> body() {
    var b = new LinkedHashMap<String, Object>();
    b.put("applicationId", applicationId);
    b.put("programId", programId);
    b.put("requestedFacility", "출입구 물막이판");
    b.put("preferredVisitDate", LocalDate.now().plusDays(7).toString());
    b.put("preferredVisitTime", "MORNING");
    return b;
  }

  ResultActions send(Map<String, Object> b, String access) throws Exception {
    var request =
        post("/api/resident/support-applications")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(b));
    if (access != null) request.header("Authorization", "Bearer " + access);
    return mvc.perform(request);
  }

  int count() {
    return jdbc.queryForObject("SELECT COUNT(*) FROM support_applications", Integer.class);
  }

  List<Object> snapshot() {
    return List.of(
        jdbc.queryForList("SELECT * FROM inspection_applications"),
        jdbc.queryForList("SELECT * FROM inspections"),
        jdbc.queryForList("SELECT * FROM inspection_results"),
        jdbc.queryForList("SELECT * FROM application_status_histories"),
        jdbc.queryForList("SELECT * FROM inspection_status_histories"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"OWNER", "TENANT", "ETC"})
  void submitsAndStoresServerSnapshotsWithoutChangingSource(String occupancy) throws Exception {
    jdbc.update(
        "UPDATE inspection_applications SET occupancy_type=?,preferred_visit_date=?,preferred_time_slot='ANY' WHERE application_id=?",
        occupancy,
        LocalDate.now().plusDays(1),
        applicationId);
    var before = snapshot();
    var b = body();
    b.put("requestedFacility", "  출입구 물막이판  ");
    var response =
        send(b, token)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value(201))
            .andExpect(jsonPath("$.code").value("SUPPORT_201_1"))
            .andExpect(jsonPath("$.result.applicationId").value(applicationId))
            .andExpect(jsonPath("$.result.programId").value(programId))
            .andExpect(
                jsonPath("$.result.status")
                    .value("TENANT".equals(occupancy) ? "DRAFT" : "SUBMITTED"))
            .andExpect(
                jsonPath("$.result.receiptNumber")
                    .value(String.format(Locale.ROOT, "%06d", applicationId)))
            .andExpect(jsonPath("$.result.applicantName").value("김주민"))
            .andExpect(jsonPath("$.result.programName").value("테스트 지원사업"))
            .andExpect(
                jsonPath("$.result.createdAt").value(org.hamcrest.Matchers.endsWith("+09:00")))
            .andReturn();
    var row = jdbc.queryForMap("SELECT * FROM support_applications");
    var result = mapper.readTree(response.getResponse().getContentAsString()).path("result");
    assertThat(result.path("applicantName").asString()).isEqualTo(row.get("APPLICANT_NAME"));
    assertThat(result.path("programName").asString())
        .isEqualTo(
            jdbc.queryForObject(
                "SELECT name FROM support_programs WHERE program_id=?", String.class, programId));
    assertThat(result.path("supportApplicationId").asLong())
        .isEqualTo(((Number) row.get("SUPPORT_APPLICATION_ID")).longValue());
    assertThat(java.time.OffsetDateTime.parse(result.path("createdAt").asString()).toInstant())
        .isEqualTo(((java.sql.Timestamp) row.get("CREATED_AT")).toInstant());
    if ("TENANT".equals(occupancy)) {
      assertThat(row.get("SUBMITTED_AT")).isNull();
      assertThat(result.path("submittedAt").isNull()).isTrue();
    } else {
      assertThat(result.path("createdAt").asString())
          .isEqualTo(result.path("submittedAt").asString());
    }
    var detail =
        mvc.perform(
                get("/api/resident/applications/" + applicationId)
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(result.path("receiptNumber").asString())
        .isEqualTo(
            mapper
                .readTree(detail.getResponse().getContentAsString())
                .path("result")
                .path("receiptNumber")
                .asString());
    assertThat(row.get("APPLICANT_USER_ID")).isEqualTo(residentId);
    assertThat(row.get("APPLICANT_NAME")).isEqualTo("김주민");
    assertThat(row.get("CONTACT")).isEqualTo(CONTACT);
    assertThat(row.get("ADDRESS")).isEqualTo(ADDRESS);
    assertThat(row.get("OCCUPANCY_TYPE")).isEqualTo(occupancy);
    assertThat(row.get("REQUESTED_FACILITY")).isEqualTo("출입구 물막이판");
    assertThat(row.get("PREFERRED_VISIT_DATE").toString())
        .isEqualTo(LocalDate.now().plusDays(7).toString());
    assertThat(row.get("PREFERRED_VISIT_TIME")).isEqualTo("MORNING");
    assertThat(snapshot()).isEqualTo(before);
    jdbc.update("UPDATE users SET name='변경된 테스트 이름' WHERE user_id=?", residentId);
    assertThat(jdbc.queryForObject("SELECT applicant_name FROM support_applications", String.class))
        .isEqualTo("김주민");
  }

  @Test
  void rejectsDuplicateAndAllowsAnotherProgram() throws Exception {
    send(body(), token).andExpect(status().isCreated());
    send(body(), token)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SUPPORT_409_1"));
    var b = body();
    b.put(
        "programId",
        programs.saveAndFlush(SupportProgram.create("별도 테스트 사업", null, null, "표시용 메타데이터")).getId());
    send(b, token).andExpect(status().isCreated());
    assertThat(count()).isEqualTo(2);
  }

  @Test
  void rejectsWhenInspectionDoesNotExist() throws Exception {
    jdbc.update("DELETE FROM inspection_results");
    jdbc.update("DELETE FROM inspection_status_histories");
    jdbc.update("DELETE FROM inspections");
    var before = snapshot();
    send(body(), token)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SUPPORT_400_2"));
    assertThat(count()).isZero();
    assertThat(snapshot()).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(strings = {"RECEIVED", "INSPECTION_COMPLETED"})
  void rejectsMissingResultEvenWhenSourceStatusLooksCompleted(String state) throws Exception {
    jdbc.update("DELETE FROM inspection_results");
    jdbc.update("UPDATE inspection_applications SET status=?", state);
    var before = snapshot();
    send(body(), token)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SUPPORT_400_2"));
    assertThat(count()).isZero();
    assertThat(snapshot()).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(strings = {"IN_PROGRESS", "COMPLETED"})
  void rejectsDraftAndCompletedButUnpublishedResult(String state) throws Exception {
    jdbc.update("UPDATE inspections SET status=?", state);
    jdbc.update("UPDATE inspection_applications SET status='INSPECTION_COMPLETED'");
    jdbc.update("UPDATE inspection_results SET result_status='DRAFT',published_at=NULL");
    var before = snapshot();
    send(body(), token)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SUPPORT_400_2"));
    assertThat(count()).isZero();
    assertThat(snapshot()).isEqualTo(before);
    mvc.perform(
            get("/api/resident/applications/" + applicationId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.resultPublished").value(false));
  }

  @Test
  void allowsPublishedResultEvenWhenAnotherDraftExists() throws Exception {
    jdbc.update(
        "INSERT INTO inspection_results(inspection_id,result_status,summary) VALUES(?,'DRAFT','new private draft')",
        inspectionId);
    var before = snapshot();
    send(body(), token).andExpect(status().isCreated());
    assertThat(snapshot()).isEqualTo(before);
  }

  @Test
  void checksOwnershipBeforeDisclosingPublicationState() throws Exception {
    jdbc.update("UPDATE inspection_results SET result_status='DRAFT',published_at=NULL");
    var other =
        users.saveAndFlush(
            User.resident("unpublished-other@example.test", "unused-test-hash", "다른테스트주민"));
    send(body(), jwt.issue(other).accessToken())
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("SUPPORT_403_2"));
    assertThat(count()).isZero();
  }

  @Test
  void usesOnlyTheRequestedApplicationsPublishedResult() throws Exception {
    jdbc.update(
        "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status) VALUES(?,?,'other fixture','HIGH','COMPLETED')",
        residentId,
        ADDRESS);
    var otherRisk =
        jdbc.queryForObject("SELECT MAX(assessment_id) FROM risk_assessments", Long.class);
    jdbc.update(
        "INSERT INTO inspection_applications(applicant_name,applicant_user_id,assessment_id,receipt_no,address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,status) SELECT applicant_name,applicant_user_id,?,'UNPUBLISHED-OTHER',address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,'RECEIVED' FROM inspection_applications WHERE application_id=?",
        otherRisk,
        applicationId);
    var otherApplication =
        jdbc.queryForObject("SELECT MAX(application_id) FROM inspection_applications", Long.class);
    var request = body();
    request.put("applicationId", otherApplication);
    send(request, token)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SUPPORT_400_2"));
    assertThat(count()).isZero();
  }

  @Test
  void databaseUniqueHandlesConcurrentRequestsEvenIfBothPrechecksMiss() throws Exception {
    doReturn(false).when(supports).existsByApplicationIdAndProgramId(anyLong(), anyLong());
    var pool = Executors.newFixedThreadPool(2);
    var start = new CountDownLatch(1);
    try {
      Callable<Integer> submit =
          () -> {
            start.await();
            return send(body(), token).andReturn().getResponse().getStatus();
          };
      var a = pool.submit(submit);
      var b = pool.submit(submit);
      start.countDown();
      assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 409);
      assertThat(count()).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"applicationId", "programId"})
  void rejectsMissingRelatedData(String field) throws Exception {
    var b = body();
    b.put(field, Long.MAX_VALUE);
    send(b, token).andExpect(status().isNotFound());
    assertThat(count()).isZero();
  }

  @Test
  void rejectsAnotherResidentWithoutDisclosingTheirInformation() throws Exception {
    var other =
        users.saveAndFlush(
            User.resident("other-support@example.test", "unused-test-hash", "다른테스트주민"));
    var result =
        send(body(), jwt.issue(other).accessToken())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("SUPPORT_403_2"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(result).doesNotContain(ADDRESS, CONTACT, "테스트주민");
    assertThat(count()).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"applicantName", "contact", "address", "occupancyType"})
  void ignoresClientBasicInformationAndUsesOnlySource(String field) throws Exception {
    var before = snapshot();
    var b = body();
    b.put(field, "다른값");
    send(b, token).andExpect(status().isCreated());
    var row = jdbc.queryForMap("SELECT * FROM support_applications");
    assertThat(row.get("APPLICANT_NAME")).isEqualTo("김주민");
    assertThat(row.get("CONTACT")).isEqualTo(CONTACT);
    assertThat(row.get("ADDRESS")).isEqualTo(ADDRESS);
    assertThat(row.get("OCCUPANCY_TYPE")).isEqualTo("OWNER");
    assertThat(snapshot()).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "applicationId",
        "programId",
        "requestedFacility",
        "preferredVisitDate",
        "preferredVisitTime"
      })
  void rejectsMissingAndNullFields(String field) throws Exception {
    var b = body();
    b.remove(field);
    send(b, token).andExpect(status().isBadRequest());
    b.put(field, null);
    send(b, token).andExpect(status().isBadRequest());
    assertThat(count()).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"requestedFacility"})
  void rejectsBlankNonStringAndOversizedFields(String field) throws Exception {
    for (Object value :
        List.of(
            "",
            "   ",
            123,
            List.of("invalid"),
            "가".repeat(field.equals("contact") || field.equals("applicantName") ? 51 : 256))) {
      var b = body();
      b.put(field, value);
      send(b, token).andExpect(status().isBadRequest());
    }
    assertThat(count()).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"applicationId", "programId"})
  void rejectsInvalidIds(String field) throws Exception {
    for (Object value : List.of(0, -1, 1.5, "1", new java.math.BigInteger("9223372036854775808"))) {
      var b = body();
      b.put(field, value);
      send(b, token).andExpect(status().isBadRequest());
    }
    assertThat(count()).isZero();
  }

  @Test
  void rejectsInvalidEnumsAndKeepsServerStatus() throws Exception {
    for (Object value : List.of("OTHER", "ANYTIME", "OWNER", 0, List.of("MORNING"), "")) {
      var b = body();
      b.put("preferredVisitTime", value);
      send(b, token).andExpect(status().isBadRequest());
    }
    var b = body();
    b.put("status", "COMPLETED");
    send(b, token).andExpect(status().isCreated());
    assertThat(jdbc.queryForObject("SELECT status FROM support_applications", String.class))
        .isEqualTo("SUBMITTED");
  }

  @Test
  void acceptsExactStringBoundaries() throws Exception {
    String name = "가".repeat(50), contact = "1".repeat(50), address = "가".repeat(255);
    jdbc.update("UPDATE users SET name=? WHERE user_id=?", name, residentId);
    jdbc.update(
        "UPDATE inspection_applications SET applicant_name=?,contact=?,address=? WHERE application_id=?",
        name,
        contact,
        address,
        applicationId);
    var b = body();
    b.put("requestedFacility", "가".repeat(255));
    send(b, token).andExpect(status().isCreated());
    assertThat(count()).isEqualTo(1);
  }

  @Test
  void rejectsUnauthenticatedExpiredAndAdminAndRevalidatesCurrentRole() throws Exception {
    send(body(), null).andExpect(status().isUnauthorized());
    send(body(), "invalid-test-token").andExpect(status().isUnauthorized());
    var claims =
        JwtClaimsSet.builder()
            .issuer("flout")
            .subject(Long.toString(residentId))
            .issuedAt(Instant.now().minusSeconds(120))
            .expiresAt(Instant.now().minusSeconds(90))
            .claim("role", "RESIDENT")
            .build();
    var expired =
        encoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    send(body(), expired).andExpect(status().isUnauthorized());
    jdbc.update("UPDATE users SET role='ADMIN' WHERE user_id=?", residentId);
    // DB 역할과 JWT 역할이 달라진 기존 토큰은 공통 인증 정책에 따라 무효화됩니다.
    send(body(), token).andExpect(status().isUnauthorized());
    send(body(), jwt.issue(users.findById(residentId).orElseThrow()).accessToken())
        .andExpect(status().isForbidden());
    assertThat(count()).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"RECEIVED", "SCHEDULED", "IN_PROGRESS", "COMPLETED"})
  void preservesAllSourceStatesIncludingPublished(String state) throws Exception {
    jdbc.update("UPDATE inspections SET status=? WHERE inspection_id=?", state, inspectionId);
    if (state.equals("COMPLETED")) {
      jdbc.update(
          "UPDATE inspection_applications SET status='INSPECTION_COMPLETED' WHERE application_id=?",
          applicationId);
      jdbc.update(
          "UPDATE inspection_results SET result_status='PUBLISHED',published_at=CURRENT_TIMESTAMP WHERE inspection_id=?",
          inspectionId);
    }
    var before = snapshot();
    send(body(), token).andExpect(status().isCreated());
    assertThat(snapshot()).isEqualTo(before);
  }

  @Test
  void rejectsPastAndMalformedDatesAndAcceptsToday() throws Exception {
    for (Object value :
        List.of(
            LocalDate.now().minusDays(1).toString(),
            "2026-02-30",
            "2026-1-01",
            "2026-11-10T09:00:00",
            "",
            20261110,
            List.of(2026, 11, 10))) {
      var b = body();
      b.put("preferredVisitDate", value);
      send(b, token).andExpect(status().isBadRequest());
    }
    assertThat(count()).isZero();
    var b = body();
    b.put("preferredVisitDate", LocalDate.now().toString());
    send(b, token).andExpect(status().isCreated());
  }

  @Test
  void readsOfficialDraftApplicationStatusWithoutChangingIt() throws Exception {
    jdbc.update(
        "UPDATE inspection_applications SET status='DRAFT' WHERE application_id=?", applicationId);
    var before = snapshot();
    send(body(), token).andExpect(status().isCreated());
    assertThat(snapshot()).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(strings = {"MORNING", "AFTERNOON", "ANY"})
  void storesEachInstallationVisitTime(String time) throws Exception {
    var b = body();
    b.put("preferredVisitTime", time);
    send(b, token).andExpect(status().isCreated());
    assertThat(
            jdbc.queryForObject(
                "SELECT preferred_visit_time FROM support_applications", String.class))
        .isEqualTo(time);
  }

  @Test
  void rejectsMissingSourceInformationWithoutInventingDefaults() throws Exception {
    jdbc.update(
        "UPDATE inspection_applications SET contact='  ' WHERE application_id=?", applicationId);
    var before = snapshot();
    send(body(), token)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SUPPORT_400_1"));
    assertThat(count()).isZero();
    assertThat(snapshot()).isEqualTo(before);
  }

  @Test
  void documentsSuccessAndErrorsAndDoesNotExposeProgramListEndpoint() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.paths['/api/resident/support-applications'].post.responses['201']")
                .exists())
        .andExpect(
            jsonPath("$.paths['/api/resident/support-applications'].post.responses['400']")
                .exists())
        .andExpect(
            jsonPath("$.paths['/api/resident/support-applications'].post.responses['401']")
                .exists())
        .andExpect(
            jsonPath("$.paths['/api/resident/support-applications'].post.responses['403']")
                .exists())
        .andExpect(
            jsonPath(
                    "$.paths['/api/resident/support-applications'].post.responses['404'].description")
                .value("현장점검 신청 또는 지원사업을 찾을 수 없습니다."))
        .andExpect(
            jsonPath(
                    "$.paths['/api/resident/support-applications'].post.responses['409'].description")
                .value("동일 현장점검 신청과 동일 지원사업으로 이미 신청했습니다."))
        .andExpect(jsonPath("$.paths['/api/resident/support-applications'].get").doesNotExist());
    mvc.perform(get("/v3/api-docs"))
        .andExpect(
            jsonPath(
                    "$.components.schemas.CreateSupportApplicationRequest.properties.preferredVisitDate.format")
                .value("date"))
        .andExpect(
            jsonPath(
                    "$.components.schemas.CreateSupportApplicationRequest.properties.preferredVisitTime")
                .exists())
        .andExpect(
            jsonPath(
                    "$.components.schemas.CreateSupportApplicationRequest.properties.applicantName")
                .doesNotExist())
        .andExpect(
            jsonPath("$.components.schemas.CreateSupportApplicationRequest.properties.contact")
                .doesNotExist())
        .andExpect(
            jsonPath("$.components.schemas.CreateSupportApplicationRequest.properties.address")
                .doesNotExist())
        .andExpect(
            jsonPath(
                    "$.components.schemas.CreateSupportApplicationRequest.properties.occupancyType")
                .doesNotExist());
    String operation = "$.paths['/api/resident/support-applications'].post";
    var documentation =
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(
                jsonPath(operation + ".description")
                    .value(org.hamcrest.Matchers.containsString("주민에게 공개된 PUBLISHED 점검 결과")))
            .andExpect(
                jsonPath(
                        operation
                            + ".responses['400'].content['application/json'].examples.RESULT_NOT_PUBLISHED.value.code")
                    .value("SUPPORT_400_2"))
            .andExpect(
                jsonPath(
                        operation
                            + ".responses['201'].content['application/json'].examples.submitted.value.result.receiptNumber")
                    .value("000101"))
            .andExpect(
                jsonPath(
                        operation
                            + ".responses['201'].content['application/json'].examples.submitted.value.result.applicantName")
                    .value("홍길동"))
            .andExpect(
                jsonPath(
                        operation
                            + ".responses['201'].content['application/json'].examples.submitted.value.result.programName")
                    .value("침수 예방시설 설치 지원"))
            .andExpect(
                jsonPath(
                        operation
                            + ".responses['201'].content['application/json'].examples.submitted.value.result.createdAt")
                    .value("2026-10-21T14:00:00+09:00"))
            .andExpect(
                jsonPath("$.components.schemas.CreateSupportApplicationRequest.required")
                    .value(
                        org.hamcrest.Matchers.containsInAnyOrder(
                            "applicationId",
                            "programId",
                            "requestedFacility",
                            "preferredVisitDate",
                            "preferredVisitTime")));
    for (String field :
        List.of(
            "supportApplicationId",
            "applicationId",
            "programId",
            "status",
            "submittedAt",
            "receiptNumber",
            "applicantName",
            "programName",
            "createdAt"))
      documentation.andExpect(
          jsonPath("$.components.schemas.CreateSupportApplicationResponse.properties." + field)
              .exists());
    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
  }
}
