package com.flout.server.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.*;
import com.flout.server.domain.user.response.code.UserErrorCode;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.security.GoogleTokenVerifier;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:google;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
    })
@AutoConfigureMockMvc
class GoogleLoginTest {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired UserConsentRepository consents;
  @Autowired PasswordEncoder passwords;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper mapper;
  @MockitoBean GoogleTokenVerifier verifier;

  @BeforeEach
  void setup() {
    consents.deleteAll();
    users.deleteAll();
    when(verifier.verify("valid")).thenReturn(identity("subject-1", "person@gmail.com"));
  }

  Jwt identity(String sub, String email) {
    return Jwt.withTokenValue("valid")
        .header("alg", "RS256")
        .subject(sub)
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(600))
        .claim("email", email)
        .claim("email_verified", true)
        .claim("name", "구글주민")
        .build();
  }

  void login(String path, String extra, int status) throws Exception {
    var response =
        mvc.perform(
                post(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"idToken\":\"valid\"" + extra + "}"))
            .andExpect(status().is(status));
    if (status == 200) {
      response
          .andExpect(jsonPath("$.result.refreshToken").isNotEmpty())
          .andExpect(jsonPath("$.result.refreshExpiresIn").value(604800));
    }
  }

  @Test
  void newResidentDoesNotAutomaticallySaveConsents() throws Exception {
    login("/api/auth/google", "", 200);
    login("/api/auth/google", "", 200);
    assertThat(users.count()).isEqualTo(1);
    assertThat(consents.count()).isZero();
    User user = users.findByEmail("person@gmail.com").orElseThrow();
    assertThat(user.getGoogleSubject()).isEqualTo("subject-1");
    assertThat(user.getLastLoginAt()).isNotNull();
  }

  @Test
  void adminRequiresPreRegistrationAndBindsOnlyOnce() throws Exception {
    login("/api/auth/admin/google", "", 403);
    assertThat(users.count()).isZero();
    User admin =
        users.saveAndFlush(User.resident("person@gmail.com", passwords.encode("secret"), "운영자"));
    jdbc.update("UPDATE users SET role='ADMIN' WHERE user_id=?", admin.getId());
    login("/api/auth/google", "", 403);
    login("/api/auth/admin/google", "", 200);
    assertThat(users.findById(admin.getId()).orElseThrow().getGoogleSubject())
        .isEqualTo("subject-1");
    when(verifier.verify("valid")).thenReturn(identity("different-subject", "person@gmail.com"));
    login("/api/auth/admin/google", "", 403);
    assertThat(users.count()).isEqualTo(1);
  }

  @Test
  void existingResidentEmailIsNotAutomaticallyLinked() throws Exception {
    users.saveAndFlush(User.resident("person@gmail.com", passwords.encode("secret"), "주민"));
    mvc.perform(
            post("/api/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"valid\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("USER_409_3"))
        .andExpect(jsonPath("$.message").value("이미 이메일로 가입된 계정입니다. 이메일 로그인을 이용해주세요."));
    assertThat(users.findByEmail("person@gmail.com").orElseThrow().getGoogleSubject()).isNull();
  }

  @Test
  void externalEmailAdminNeedsPrelinkedSubject() throws Exception {
    User admin =
        users.saveAndFlush(User.resident("admin@example.com", passwords.encode("secret"), "운영자"));
    jdbc.update("UPDATE users SET role='ADMIN' WHERE user_id=?", admin.getId());
    when(verifier.verify("valid")).thenReturn(identity("subject-1", "admin@example.com"));
    login("/api/auth/admin/google", "", 409);
    jdbc.update("UPDATE users SET google_subject='subject-1' WHERE user_id=?", admin.getId());
    login("/api/auth/admin/google", "", 200);
  }

  @Test
  void invalidTokenAndMissingInputAreRejected() throws Exception {
    when(verifier.verify("valid")).thenThrow(new BaseException(UserErrorCode.GOOGLE_INVALID_TOKEN));
    login("/api/auth/google", "", 401);
    mvc.perform(post("/api/auth/google").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
    assertThat(users.count()).isZero();
  }

  @Test
  void googleLoginTokenCanBeRefreshedThroughCommonEndpoint() throws Exception {
    String response =
        mvc.perform(
                post("/api/auth/google")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"idToken\":\"valid\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String refreshToken = mapper.readTree(response).path("result").path("refreshToken").asText();
    mvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of("refreshToken", refreshToken))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("USER_200_2"))
        .andExpect(jsonPath("$.result.requiresConsent").value(true))
        .andExpect(jsonPath("$.result.role").value("RESIDENT"))
        .andExpect(jsonPath("$.result.email").value("person@gmail.com"));
  }

  @Test
  void googleEndpointsAreDocumentedAsPublic() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.components.schemas.CommonErrorResponse.type").value("object"))
        .andExpect(jsonPath("$.components.schemas.CommonErrorResponse.properties.code").exists())
        .andExpect(
            jsonPath(
                    "$.paths['/api/auth/google'].post.responses['401'].content['application/json'].schema['$ref']")
                .value("#/components/schemas/CommonErrorResponse"))
        .andExpect(jsonPath("$.paths['/api/auth/google'].post.security").isEmpty())
        .andExpect(jsonPath("$.paths['/api/auth/admin/google'].post.responses['403']").exists())
        .andExpect(
            jsonPath(
                    "$.paths['/api/auth/google'].post.responses['200'].content['application/json'].examples.loginSuccess.value.result.role")
                .value("RESIDENT"));
  }
}
