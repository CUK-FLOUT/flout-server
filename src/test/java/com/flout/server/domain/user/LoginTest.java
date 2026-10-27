package com.flout.server.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserConsentRepository;
import com.flout.server.domain.user.repository.UserRepository;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:login;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
    })
@AutoConfigureMockMvc
@Import(LoginTest.ProbeController.class)
class LoginTest {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired UserConsentRepository consents;
  @MockitoSpyBean PasswordEncoder passwords;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper mapper;
  @Autowired JwtDecoder decoder;
  @Autowired JwtEncoder encoder;
  Long residentId;
  Long adminId;

  @BeforeEach
  void setup() {
    consents.deleteAll();
    users.deleteAll();
    residentId =
        users
            .saveAndFlush(
                User.resident("resident@example.com", passwords.encode("password123"), "주민"))
            .getId();
    adminId =
        users
            .saveAndFlush(
                User.resident("admin@example.com", passwords.encode("password123"), "운영자"))
            .getId();
    jdbc.update("UPDATE users SET role='ADMIN' WHERE user_id=?", adminId);
  }

  String body(String email, String password) {
    return mapper.writeValueAsString(java.util.Map.of("email", email, "password", password));
  }

  String token(String path, String email) throws Exception {
    String response =
        mvc.perform(
                post(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(email, "password123")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("USER_200_1"))
            .andExpect(jsonPath("$.message").value("로그인되었습니다."))
            .andExpect(jsonPath("$.result.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.result.expiresIn").value(3600))
            .andExpect(jsonPath("$.result.password").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(response).doesNotContain("password123", "passwordHash");
    return mapper.readTree(response).path("result").path("accessToken").asText();
  }

  @Test
  void residentLoginIssuesUsableTokenAndUpdatesLoginTime() throws Exception {
    String accessToken = token("/api/auth/login", "Resident@example.com");
    Jwt jwt = decoder.decode(accessToken);
    assertThat(jwt.getSubject()).isEqualTo(residentId.toString());
    assertThat(jwt.getClaimAsString("role")).isEqualTo("RESIDENT");
    assertThat(jwt.getExpiresAt()).isAfter(Instant.now());
    assertThat(users.findById(residentId).orElseThrow().getLastLoginAt()).isNotNull();
    var result =
        mvc.perform(get("/api/probe").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(result.getRequest().getSession(false)).isNull();
  }

  @Test
  void onlyAdminTokenCanAccessAdminApi() throws Exception {
    String adminToken = token("/api/auth/admin/login", "admin@example.com");
    mvc.perform(get("/api/admin/probe").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());
    String residentToken = token("/api/auth/login", "resident@example.com");
    mvc.perform(get("/api/admin/probe").header("Authorization", "Bearer " + residentToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.isSuccess").value(false));
    assertThat(users.findById(adminId).orElseThrow().getLastLoginAt()).isNotNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing@example.com", "resident@example.com"})
  void missingAccountAndWrongPasswordHaveSameError(String email) throws Exception {
    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(email, "incorrect123")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("USER_401_1"))
        .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 올바르지 않습니다."));
    assertThat(users.findById(residentId).orElseThrow().getLastLoginAt()).isNull();
  }

  @Test
  void rejectsLoginThroughOtherRoleEndpoint() throws Exception {
    mvc.perform(
            post("/api/auth/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("resident@example.com", "password123")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("USER_401_1"));
    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("admin@example.com", "password123")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("USER_401_1"));
  }

  @Test
  void googleResidentIsDirectedToGoogleLoginWithoutIssuingToken() throws Exception {
    jdbc.update("UPDATE users SET google_subject='google-resident' WHERE user_id=?", residentId);
    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("resident@example.com", "incorrect123")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("USER_401_3"))
        .andExpect(jsonPath("$.message").value("구글로그인으로 가입된 계정입니다. 구글로그인을 이용해주세요."))
        .andExpect(jsonPath("$.result").isEmpty());
    assertThat(users.findById(residentId).orElseThrow().getLastLoginAt()).isNull();
    mvc.perform(
            post("/api/auth/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("resident@example.com", "incorrect123")))
        .andExpect(jsonPath("$.code").value("USER_401_1"));
  }

  @Test
  void googleLinkedAdminKeepsEmailLoginAndGenericPasswordFailure() throws Exception {
    jdbc.update("UPDATE users SET google_subject='google-admin' WHERE user_id=?", adminId);
    token("/api/auth/admin/login", "admin@example.com");
    mvc.perform(
            post("/api/auth/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("admin@example.com", "incorrect123")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("USER_401_1"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"email\":\"invalid\",\"password\":\"password123\"}",
        "{\"email\":\"resident@example.com\",\"password\":\"\"}"
      })
  void invalidInputReturns400(String request) throws Exception {
    mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(request))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_400_2"));
  }

  @Test
  void missingAndForgedTokenAreRejected() throws Exception {
    mvc.perform(get("/api/probe"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.isSuccess").value(false));
    mvc.perform(get("/api/probe").header("Authorization", "Bearer invalid.token.signature"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("COMMON_401_3"));
  }

  @Test
  void expiredSignedTokenIsRejected() throws Exception {
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("flout")
            .subject(residentId.toString())
            .issuedAt(Instant.now().minusSeconds(120))
            .expiresAt(Instant.now().minusSeconds(60))
            .claim("role", "RESIDENT")
            .build();
    String expired =
        encoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    mvc.perform(get("/api/probe").header("Authorization", "Bearer " + expired))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void modifiedSignatureIsRejected() throws Exception {
    String accessToken = token("/api/auth/login", "resident@example.com");
    int signatureStart = accessToken.lastIndexOf('.') + 1;
    char replacement = accessToken.charAt(signatureStart) == 'A' ? 'B' : 'A';
    String forged =
        accessToken.substring(0, signatureStart)
            + replacement
            + accessToken.substring(signatureStart + 1);
    mvc.perform(get("/api/probe").header("Authorization", "Bearer " + forged))
        .andExpect(status().isUnauthorized());
  }

  @ParameterizedTest
  @ValueSource(strings = {"issuer", "expiration", "role"})
  void rejectsSignedTokenWithInvalidClaims(String scenario) throws Exception {
    JwtClaimsSet.Builder claims =
        JwtClaimsSet.builder().subject(residentId.toString()).issuedAt(Instant.now());
    claims.issuer(scenario.equals("issuer") ? "other-service" : "flout");
    if (!scenario.equals("expiration")) {
      claims.expiresAt(Instant.now().plusSeconds(60));
    }
    if (!scenario.equals("role")) {
      claims.claim("role", "RESIDENT");
    }
    String invalid =
        encoder
            .encode(
                JwtEncoderParameters.from(
                    JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
            .getTokenValue();
    mvc.perform(get("/api/probe").header("Authorization", "Bearer " + invalid))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void tokenIsRejectedAfterRoleChangeOrAccountDeletion() throws Exception {
    String accessToken = token("/api/auth/login", "resident@example.com");
    jdbc.update("UPDATE users SET role='ADMIN' WHERE user_id=?", residentId);
    mvc.perform(get("/api/probe").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isUnauthorized());
    users.deleteById(residentId);
    mvc.perform(get("/api/probe").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void signupAndSwaggerRemainPublic() throws Exception {
    mvc.perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
        {"email":"new@example.com","password":"password123","passwordConfirm":"password123",
         "name":"신규주민","thirdPartyProvisionAgreed":true,"statusNotificationAgreed":true}
        """))
        .andExpect(status().isCreated());
    mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
  }

  @ParameterizedTest
  @ValueSource(strings = {"role", "password", "email", "delete"})
  void concurrentAccountChangeIsPreservedAndLoginIsRejected(String change) throws Exception {
    try (var executor = Executors.newSingleThreadExecutor()) {
      doAnswer(
              invocation -> {
                executor
                    .submit(
                        () -> {
                          switch (change) {
                            case "role" ->
                                jdbc.update(
                                    "UPDATE users SET role='RESIDENT' WHERE user_id=?", adminId);
                            case "password" ->
                                jdbc.update(
                                    "UPDATE users SET password_hash=? WHERE user_id=?",
                                    passwords.encode("changed123"),
                                    adminId);
                            case "email" ->
                                jdbc.update(
                                    "UPDATE users SET email='changed@example.com' WHERE user_id=?",
                                    adminId);
                            default -> jdbc.update("DELETE FROM users WHERE user_id=?", adminId);
                          }
                        })
                    .get(10, TimeUnit.SECONDS);
                return invocation.callRealMethod();
              })
          .when(passwords)
          .matches(eq("password123"), anyString());

      mvc.perform(
              post("/api/auth/admin/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body("admin@example.com", "password123")))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("USER_401_1"))
          .andExpect(jsonPath("$.result.accessToken").doesNotExist());

      if (change.equals("delete")) {
        assertThat(users.findById(adminId)).isEmpty();
      } else {
        User user = users.findById(adminId).orElseThrow();
        assertThat(user.getLastLoginAt()).isNull();
        switch (change) {
          case "role" ->
              assertThat(user.getRole())
                  .isEqualTo(com.flout.server.domain.user.entity.UserRole.RESIDENT);
          case "password" ->
              assertThat(passwords.matches("changed123", user.getPasswordHash())).isTrue();
          case "email" -> assertThat(user.getEmail()).isEqualTo("changed@example.com");
        }
      }
    }
  }

  @Test
  void concurrentNameChangeIsNotOverwrittenByLoginTimeUpdate() throws Exception {
    try (var executor = Executors.newSingleThreadExecutor()) {
      doAnswer(
              invocation -> {
                executor
                    .submit(
                        () -> jdbc.update("UPDATE users SET name='변경된이름' WHERE user_id=?", adminId))
                    .get(10, TimeUnit.SECONDS);
                return invocation.callRealMethod();
              })
          .when(passwords)
          .matches(eq("password123"), anyString());
      token("/api/auth/admin/login", "admin@example.com");
      User user = users.findById(adminId).orElseThrow();
      assertThat(user.getName()).isEqualTo("변경된이름");
      assertThat(user.getLastLoginAt()).isNotNull();
    }
  }

  @Test
  void swaggerDocumentsJwtAndPublicAuthEndpoints() throws Exception {
    String response =
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var document = mapper.readTree(response);
    assertThat(document.at("/components/securitySchemes/bearerAuth/type").asText())
        .isEqualTo("http");
    assertThat(document.at("/components/securitySchemes/bearerAuth/scheme").asText())
        .isEqualTo("bearer");
    assertThat(document.at("/security/0/bearerAuth").isArray()).isTrue();
    for (String path :
        java.util.List.of("/api/auth/signup", "/api/auth/login", "/api/auth/admin/login")) {
      var operation = document.path("paths").path(path).path("post");
      assertThat(operation.path("security").isArray()).isTrue();
      assertThat(operation.path("security").size()).isZero();
    }
    var signup = document.path("paths").path("/api/auth/signup").path("post").path("responses");
    assertThat(signup.has("200")).isFalse();
    var signupSuccess = signup.path("201").at("/content/application~1json");
    assertThat(signupSuccess.at("/schema/$ref").asText()).contains("SuccessResponse");
    assertThat(signupSuccess.at("/examples/signupSuccess/value/code").asText())
        .isEqualTo("USER_201_1");
    assertThat(
            signup
                .path("409")
                .at("/content/application~1json/examples/duplicateEmail/value/code")
                .asText())
        .isEqualTo("USER_409_1");
    for (String path : java.util.List.of("/api/auth/login", "/api/auth/admin/login")) {
      var responses = document.path("paths").path(path).path("post").path("responses");
      assertThat(responses.has("403")).isFalse();
      var success =
          responses.path("200").at("/content/application~1json/examples/loginSuccess/value");
      assertThat(success.path("code").asText()).isEqualTo("USER_200_1");
      assertThat(success.at("/result/expiresIn").asLong()).isEqualTo(3600);
      assertThat(success.at("/result/role").asText())
          .isEqualTo(path.endsWith("/admin/login") ? "ADMIN" : "RESIDENT");
      assertThat(
              responses
                  .path("401")
                  .at("/content/application~1json/examples/loginFailed/value/code")
                  .asText())
          .isEqualTo("USER_401_1");
      assertThat(
              responses
                  .path("400")
                  .at("/content/application~1json/examples/emailRequired/value/message")
                  .asText())
          .isEqualTo("이메일을 입력해주세요.");
    }
    assertThat(
            document
                .path("paths")
                .path("/api/admin/probe")
                .path("get")
                .at("/responses/403/content/application~1json/examples/accessDenied/value/code")
                .asText())
        .isEqualTo("COMMON_403_1");
    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
  }

  @RestController
  static class ProbeController {
    @GetMapping("/api/probe")
    String resident() {
      return "ok";
    }

    @GetMapping("/api/admin/probe")
    String admin() {
      return "ok";
    }
  }
}
