package com.flout.server.domain.publicissue;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.address.dto.response.KakaoAddressSearchResponse;
import com.flout.server.domain.address.dto.response.KakaoAddressSearchResponse.Document;
import com.flout.server.domain.address.exception.KakaoAddressException;
import com.flout.server.domain.address.service.KakaoAddressClient;
import com.flout.server.domain.publicissue.entity.PublicIssue;
import com.flout.server.domain.publicissue.repository.PublicIssueRepository;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import java.math.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:public-issues;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false",
      "app.s3.cleanup-interval-millis=3600000"
    })
@AutoConfigureMockMvc
class PublicIssueTest {
  private static final String SOURCE = "경기도 부천시 원미구 길주로 53";
  private static final String OTHER = "경기도 부천시 원미구 길주로 210";
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired JwtEncoder encoder;
  @Autowired ObjectMapper mapper;
  @MockitoBean KakaoAddressClient client;
  @MockitoSpyBean PublicIssueRepository issues;
  long inspectionId, applicationId, resultId, adminId;
  String token, residentToken;

  @BeforeEach
  void setup() {
    for (String table :
        List.of(
            "public_issues",
            "inspection_result_photos",
            "inspection_vulnerabilities",
            "prevention_recommendations",
            "inspection_results",
            "inspection_status_histories",
            "inspections",
            "application_status_histories",
            "application_inflow_locations",
            "application_attachments",
            "attachment_upload_intents",
            "inspection_applications",
            "prevention_guides",
            "risk_assessments",
            "refresh_tokens",
            "user_consents",
            "users")) jdbc.update("DELETE FROM " + table);
    var resident = users.saveAndFlush(User.resident("public-resident@example.com", "hash", "주민"));
    residentToken = jwt.issue(resident).accessToken();
    jdbc.update(
        "INSERT INTO users(email,password_hash,name,role,jurisdiction_id) VALUES('public-admin@example.com','hash','운영자','ADMIN',(SELECT jurisdiction_id FROM jurisdictions WHERE name='부천시'))");
    var admin = users.findByEmail("public-admin@example.com").orElseThrow();
    adminId = admin.getId();
    token = jwt.issue(admin).accessToken();
    jdbc.update(
        "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status) VALUES(?,?,'101호','HIGH','COMPLETED')",
        resident.getId(),
        SOURCE);
    long assessment =
        jdbc.queryForObject(
            "SELECT assessment_id FROM risk_assessments WHERE user_id=?",
            Long.class,
            resident.getId());
    jdbc.update(
        "INSERT INTO inspection_applications(applicant_user_id,assessment_id,receipt_no,address,detail_address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,status) VALUES(?,?,'PUBLIC-TEST',?,'101호',37.5000000,126.7600000,'010-0000-0000','OWNER','NO',FALSE,'RECEIVED')",
        resident.getId(),
        assessment,
        SOURCE);
    applicationId =
        jdbc.queryForObject(
            "SELECT application_id FROM inspection_applications WHERE assessment_id=?",
            Long.class,
            assessment);
    jdbc.update(
        "INSERT INTO inspections(application_id,status) VALUES(?,'IN_PROGRESS')", applicationId);
    inspectionId =
        jdbc.queryForObject(
            "SELECT inspection_id FROM inspections WHERE application_id=?",
            Long.class,
            applicationId);
    jdbc.update(
        "INSERT INTO inspection_results(inspection_id,result_status,summary,follow_up_type,internal_memo) VALUES(?,'DRAFT','유지할 요약','PUBLIC_AREA','유지할 메모')",
        inspectionId);
    resultId =
        jdbc.queryForObject(
            "SELECT result_id FROM inspection_results WHERE inspection_id=?",
            Long.class,
            inspectionId);
    jdbc.update(
        "INSERT INTO inspection_result_photos(result_id,object_key,original_filename,file_size,content_type) VALUES(?,'public-test-existing-photo','old.png',100,'image/png')",
        resultId);
    jdbc.update(
        "INSERT INTO inspection_vulnerabilities(result_id,type,title) VALUES(?,'DRAIN','배수구')",
        resultId);
    jdbc.update(
        "INSERT INTO prevention_recommendations(result_id,title,related_target) VALUES(?,'유지할 조치','DRAIN')",
        resultId);
    jdbc.update(
        "INSERT INTO inspection_status_histories(inspection_id,previous_status,status,changed_by_user_id,change_source,changed_at) VALUES(?,'RECEIVED','IN_PROGRESS',?,'MANUAL',CURRENT_TIMESTAMP)",
        inspectionId,
        adminId);
    jdbc.update(
        "INSERT INTO application_status_histories(application_id,status) VALUES(?,'RECEIVED')",
        applicationId);
  }

  Map<String, Object> body() {
    var map = new LinkedHashMap<String, Object>();
    map.put("sourceInspectionId", inspectionId);
    map.put("address", SOURCE);
    map.put("issueType", "REPEATED_FLOODING");
    map.put("description", "조치 필요");
    map.put("fieldFindings", "현장 확인");
    return map;
  }

  org.springframework.test.web.servlet.ResultActions postBody(Map<String, Object> body)
      throws Exception {
    return mvc.perform(
        post("/api/admin/public-issues")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(body)));
  }

  int count() {
    return jdbc.queryForObject("SELECT COUNT(*) FROM public_issues", Integer.class);
  }

  Map<String, Object> snapshot() {
    var state = new LinkedHashMap<String, Object>();
    state.put(
        "inspection",
        jdbc.queryForList("SELECT * FROM inspections WHERE inspection_id=?", inspectionId));
    state.put(
        "application",
        jdbc.queryForList(
            "SELECT * FROM inspection_applications WHERE application_id=?", applicationId));
    state.put(
        "result",
        jdbc.queryForList("SELECT * FROM inspection_results WHERE result_id=?", resultId));
    state.put(
        "inspectionHistory",
        jdbc.queryForList(
            "SELECT * FROM inspection_status_histories WHERE inspection_id=?", inspectionId));
    state.put(
        "applicationHistory",
        jdbc.queryForList(
            "SELECT * FROM application_status_histories WHERE application_id=?", applicationId));
    state.put(
        "photo",
        jdbc.queryForList("SELECT * FROM inspection_result_photos WHERE result_id=?", resultId));
    state.put(
        "vulnerabilities",
        jdbc.queryForList("SELECT * FROM inspection_vulnerabilities WHERE result_id=?", resultId));
    state.put(
        "recommendations",
        jdbc.queryForList("SELECT * FROM prevention_recommendations WHERE result_id=?", resultId));
    return state;
  }

  KakaoAddressSearchResponse located() {
    return new KakaoAddressSearchResponse(
        List.of(new Document(OTHER, "ROAD_ADDR", "126.7985678", "37.4874856", null, null)));
  }

  @Test
  void registersWithTrimmedStringsReusesCoordinatesAndPreservesSource() throws Exception {
    var before = snapshot();
    var body = body();
    for (String field : List.of("address", "issueType", "description", "fieldFindings"))
      body.put(field, " \t" + body.get(field) + " \n");
    postBody(body)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.code").value("PUBLIC_ISSUE_200_1"))
        .andExpect(jsonPath("$.result.publicIssueId").isNumber())
        .andExpect(jsonPath("$.result.sourceInspectionId").value(inspectionId))
        .andExpect(jsonPath("$.result.address").value(SOURCE))
        .andExpect(jsonPath("$.result.issueType").value("REPEATED_FLOODING"))
        .andExpect(jsonPath("$.result.description").value("조치 필요"))
        .andExpect(jsonPath("$.result.fieldFindings").value("현장 확인"))
        .andExpect(jsonPath("$.result.status").value("RECEIVED"))
        .andExpect(jsonPath("$.result.createdAt").value(org.hamcrest.Matchers.endsWith("+09:00")));
    var row = jdbc.queryForMap("SELECT * FROM public_issues");
    assertThat(row.get("latitude")).isEqualTo(new BigDecimal("37.5000000"));
    assertThat(row.get("longitude")).isEqualTo(new BigDecimal("126.7600000"));
    verifyNoInteractions(client);
    assertThat(snapshot()).isEqualTo(before);
  }

  @Test
  void normalizesProvinceAndWhitespaceOnlyForSameAddressComparison() throws Exception {
    var body = body();
    body.put("address", "  경기  부천시 원미구  길주로 53  ");
    postBody(body).andExpect(status().isOk());
    verifyNoInteractions(client);
    assertThat(count()).isEqualTo(1);
  }

  @Test
  void geocodesDifferentAddressOutsideTransactionAndStoresCoordinates() throws Exception {
    when(client.search(OTHER))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return located();
            });
    var before = snapshot();
    var body = body();
    body.put("address", OTHER);
    postBody(body).andExpect(status().isOk()).andExpect(jsonPath("$.result.address").value(OTHER));
    assertThat(jdbc.queryForObject("SELECT latitude FROM public_issues", BigDecimal.class))
        .isEqualTo(new BigDecimal("37.4874856"));
    assertThat(jdbc.queryForObject("SELECT longitude FROM public_issues", BigDecimal.class))
        .isEqualTo(new BigDecimal("126.7985678"));
    verify(client).search(OTHER);
    assertThat(snapshot()).isEqualTo(before);
  }

  @Test
  void permitsRepeatedRegistrationFromSameInspection() throws Exception {
    postBody(body()).andExpect(status().isOk());
    postBody(body()).andExpect(status().isOk());
    assertThat(count()).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(DISTINCT public_issue_id) FROM public_issues WHERE source_inspection_id=?",
                Integer.class,
                inspectionId))
        .isEqualTo(2);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "REPEATED_FLOODING",
        "SEWAGE_BACKFLOW",
        "POOR_ROAD_DRAINAGE",
        "BLOCKED_STORM_DRAIN"
      })
  void supportsEachIssueType(String type) throws Exception {
    var body = body();
    body.put("issueType", type);
    postBody(body).andExpect(status().isOk()).andExpect(jsonPath("$.result.issueType").value(type));
  }

  @ParameterizedTest
  @ValueSource(strings = {"RECEIVED", "SCHEDULED", "IN_PROGRESS", "COMPLETED"})
  void neverChangesSourceStatusOrHistory(String state) throws Exception {
    jdbc.update("UPDATE inspections SET status=? WHERE inspection_id=?", state, inspectionId);
    if (state.equals("COMPLETED")) {
      jdbc.update(
          "UPDATE inspection_applications SET status='INSPECTION_COMPLETED' WHERE application_id=?",
          applicationId);
      jdbc.update(
          "UPDATE inspection_results SET result_status='PUBLISHED',published_at=CURRENT_TIMESTAMP WHERE result_id=?",
          resultId);
    }
    var before = snapshot();
    postBody(body()).andExpect(status().isOk());
    assertThat(snapshot()).isEqualTo(before);
  }

  @Test
  void rejectsUnresolvableAndAmbiguousAddressWithoutSaving() throws Exception {
    var body = body();
    body.put("address", OTHER);
    when(client.search(OTHER)).thenReturn(new KakaoAddressSearchResponse(List.of()));
    postBody(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PUBLIC_ISSUE_400_1"));
    assertThat(count()).isZero();
    when(client.search(OTHER))
        .thenReturn(
            new KakaoAddressSearchResponse(
                List.of(
                    new Document("경기도 부천시 원미구 다른길 99", "ROAD_ADDR", "126.7", "37.5", null, null))));
    postBody(body).andExpect(status().isBadRequest());
    assertThat(count()).isZero();
  }

  @Test
  void rejectsInvalidOrOutsideLocationBeforeCallingProvider() throws Exception {
    for (String address : List.of("서울특별시 중구 세종대로 1", "주소 확인 불가")) {
      var body = body();
      body.put("address", address);
      postBody(body)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("PUBLIC_ISSUE_400_1"));
    }
    assertThat(count()).isZero();
    verifyNoInteractions(client);
  }

  @ParameterizedTest
  @ValueSource(strings = {"API_ERROR", "TIMEOUT"})
  void mapsExternalFailuresTo502OnlyForThisEndpoint(String kind) throws Exception {
    when(client.search(OTHER))
        .thenThrow(new KakaoAddressException(KakaoAddressException.Type.valueOf(kind), null));
    var body = body();
    body.put("address", OTHER);
    postBody(body)
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("ADDRESS_502_1"));
    assertThat(count()).isZero();
    if (kind.equals("TIMEOUT"))
      mvc.perform(
              get("/api/resident/addresses/coordinates")
                  .param("address", OTHER)
                  .header("Authorization", "Bearer " + token))
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.code").value("ADDRESS_503_1"));
  }

  @Test
  void treatsMalformedProviderDataAsUpstreamFailureWithoutSaving() throws Exception {
    when(client.search(OTHER))
        .thenReturn(
            new KakaoAddressSearchResponse(
                List.of(new Document(OTHER, "ROAD_ADDR", "NaN", "37.4", null, null))));
    var body = body();
    body.put("address", OTHER);
    postBody(body).andExpect(status().isBadGateway());
    assertThat(count()).isZero();
  }

  @Test
  void checksAuthorizationAgainAfterExternalLookup() throws Exception {
    when(client.search(OTHER))
        .thenAnswer(
            invocation -> {
              jdbc.update("UPDATE users SET jurisdiction_id=NULL WHERE user_id=?", adminId);
              return located();
            });
    var body = body();
    body.put("address", OTHER);
    postBody(body).andExpect(status().isForbidden());
    assertThat(count()).isZero();
  }

  @Test
  void rollsBackWhenPersistenceFailsAfterFlush() throws Exception {
    doAnswer(
            invocation -> {
              PublicIssue row = issues.save(invocation.getArgument(0));
              issues.flush();
              throw new IllegalStateException("simulated persistence failure");
            })
        .when(issues)
        .saveAndFlush(any());
    var before = snapshot();
    postBody(body()).andExpect(status().isInternalServerError());
    assertThat(count()).isZero();
    assertThat(snapshot()).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"sourceInspectionId", "address", "issueType", "description", "fieldFindings"})
  void rejectsMissingAndNullFields(String field) throws Exception {
    var body = body();
    body.remove(field);
    postBody(body).andExpect(status().isBadRequest());
    body = body();
    body.put(field, null);
    postBody(body).andExpect(status().isBadRequest());
    assertThat(count()).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"address", "issueType", "description", "fieldFindings"})
  void rejectsEmptyWhitespaceAndNonStringFields(String field) throws Exception {
    for (Object value : List.of("", " \t\n ", 123, List.of("text"), Map.of("text", "value"))) {
      var body = body();
      body.put(field, value);
      postBody(body).andExpect(status().isBadRequest());
    }
    assertThat(count()).isZero();
  }

  static Stream<Object> invalidIds() {
    return Stream.of(
        0L,
        -1L,
        1.5,
        "1",
        true,
        new BigInteger("9223372036854775808"),
        List.of(1),
        Map.of("id", 1));
  }

  @ParameterizedTest
  @MethodSource("invalidIds")
  void rejectsNonPositiveNonIntegralAndOverflowIds(Object value) throws Exception {
    var body = body();
    body.put("sourceInspectionId", value);
    postBody(body).andExpect(status().isBadRequest());
    assertThat(count()).isZero();
  }

  @Test
  void rejectsUnknownIssueTypeAndMissingSource() throws Exception {
    var body = body();
    body.put("issueType", "UNKNOWN");
    postBody(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PUBLIC_ISSUE_400_2"));
    body = body();
    body.put("sourceInspectionId", Long.MAX_VALUE);
    postBody(body)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INSPECTION_404_1"));
    assertThat(count()).isZero();
  }

  @Test
  void rejectsLengthOverflowAndAcceptsExactBoundsAfterStrip() throws Exception {
    var body = body();
    body.put("address", "가".repeat(256));
    postBody(body).andExpect(status().isBadRequest());
    for (String field : List.of("description", "fieldFindings")) {
      body = body();
      body.put(field, "가".repeat(4001));
      postBody(body).andExpect(status().isBadRequest());
    }
    assertThat(count()).isZero();
    body = body();
    body.put("description", "  " + "가".repeat(4000) + "  ");
    body.put("fieldFindings", "나".repeat(4000));
    postBody(body).andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "SELECT CHAR_LENGTH(description) FROM public_issues", Integer.class))
        .isEqualTo(4000);
    String prefix = "경기도 부천시 원미구 ";
    String address = prefix + "가".repeat(255 - prefix.length());
    assertThat(address.length()).isEqualTo(255);
    jdbc.update(
        "UPDATE inspection_applications SET address=? WHERE application_id=?",
        address,
        applicationId);
    body = body();
    body.put("address", address);
    postBody(body).andExpect(status().isOk());
  }

  @Test
  void rejectsMissingInvalidExpiredTokensAndResidentRole() throws Exception {
    String json = mapper.writeValueAsString(body());
    mvc.perform(
            post("/api/admin/public-issues").contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isUnauthorized());
    for (String access : List.of("invalid", residentToken)) {
      var result =
          mvc.perform(
              post("/api/admin/public-issues")
                  .header("Authorization", "Bearer " + access)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json));
      if (access.equals(residentToken)) result.andExpect(status().isForbidden());
      else result.andExpect(status().isUnauthorized());
    }
    var claims =
        JwtClaimsSet.builder()
            .issuer("flout")
            .subject(Long.toString(adminId))
            .issuedAt(Instant.now().minusSeconds(7200))
            .expiresAt(Instant.now().minusSeconds(3600))
            .claim("role", "ADMIN")
            .build();
    String expired =
        encoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    mvc.perform(
            post("/api/admin/public-issues")
                .header("Authorization", "Bearer " + expired)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andExpect(status().isUnauthorized());
    assertThat(count()).isZero();
    verifyNoInteractions(client);
  }

  @Test
  void rejectsMissingJurisdictionAndOutsideSource() throws Exception {
    jdbc.update(
        "INSERT INTO users(email,password_hash,name,role) VALUES('public-nojur@example.com','hash','무관할','ADMIN')");
    String original = token;
    token = jwt.issue(users.findByEmail("public-nojur@example.com").orElseThrow()).accessToken();
    postBody(body()).andExpect(status().isForbidden());
    token = original;
    jdbc.update(
        "UPDATE inspection_applications SET address='서울특별시 중구' WHERE application_id=?",
        applicationId);
    postBody(body()).andExpect(status().isForbidden());
    assertThat(count()).isZero();
    verifyNoInteractions(client);
  }

  @Test
  void documentsRequestFieldsSuccessAnd404Explanation() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/public-issues'].post.responses['200'].content['application/json'].examples.publicIssueRegistered.value.code")
                .value("PUBLIC_ISSUE_200_1"))
        .andExpect(
            jsonPath("$.paths['/api/admin/public-issues'].post.responses['404'].description")
                .value("원본 점검이 존재하지 않습니다."))
        .andExpect(jsonPath("$.paths['/api/admin/public-issues'].post.responses['502']").exists())
        .andExpect(jsonPath("$.paths['/api/admin/public-issues'].post.responses['401']").exists())
        .andExpect(jsonPath("$.paths['/api/admin/public-issues'].post.responses['403']").exists())
        .andExpect(
            jsonPath("$.components.schemas.CreatePublicIssueRequest.properties.address.maxLength")
                .value(255))
        .andExpect(
            jsonPath(
                    "$.components.schemas.CreatePublicIssueRequest.properties.sourceInspectionId.type")
                .value("integer"));
  }
}
