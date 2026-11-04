package com.flout.server.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:refresh;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
    })
@AutoConfigureMockMvc
@Import(RefreshTokenTest.Probe.class)
class RefreshTokenTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired UserRepository users;
  @Autowired UserConsentRepository consents;
  @Autowired RefreshTokenRepository refreshTokens;
  @Autowired PasswordEncoder passwords;
  @Autowired JdbcTemplate jdbc;
  @Autowired JwtDecoder decoder;
  @MockitoSpyBean JwtEncoder encoder;
  Long userId;

  @BeforeEach
  void setup() {
    refreshTokens.deleteAll();
    consents.deleteAll();
    users.deleteAll();
    userId =
        users
            .saveAndFlush(
                User.resident("resident@example.com", passwords.encode("password123"), "주민"))
            .getId();
  }

  JsonNode login() throws Exception {
    String response =
        mvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"resident@example.com\",\"password\":\"password123\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(response).path("result");
  }

  String body(String token) {
    return mapper.writeValueAsString(Map.of("refreshToken", token));
  }

  JsonNode refresh(String token) throws Exception {
    String response =
        mvc.perform(
                post("/api/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("USER_200_2"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(response).path("result");
  }

  void rejected(String token) throws Exception {
    mvc.perform(
            post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body(token)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("USER_401_4"))
        .andExpect(jsonPath("$.result").isEmpty());
  }

  @Test
  void issuesHashedRefreshTokenAndRotatesItOnce() throws Exception {
    var original = login();
    String raw = original.path("refreshToken").asText();
    assertThat(raw).hasSize(43);
    assertThat(original.path("refreshExpiresIn").asLong()).isEqualTo(604800);
    String expectedHash =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    assertThat(refreshTokens.findAll()).hasSize(1);
    assertThat(refreshTokens.findAll().getFirst().getTokenHash())
        .isEqualTo(expectedHash)
        .isNotEqualTo(raw);
    var rotated = refresh(raw);
    assertThat(rotated.path("refreshToken").asText()).isNotEqualTo(raw);
    assertThat(rotated.path("accessToken").asText())
        .isNotEqualTo(original.path("accessToken").asText());
    assertThat(rotated.path("userId").asLong()).isEqualTo(userId);
    assertThat(refreshTokens.count()).isEqualTo(1);
    rejected(raw);
    mvc.perform(
            get("/api/refresh-probe")
                .header("Authorization", "Bearer " + rotated.path("accessToken").asText()))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/refresh-probe")
                .header("Authorization", "Bearer " + rotated.path("refreshToken").asText()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsExpiredForgedAndAccessTokens() throws Exception {
    var tokens = login();
    rejected("a".repeat(43));
    rejected(tokens.path("accessToken").asText());
    jdbc.update(
        "UPDATE refresh_tokens SET expires_at='2000-01-01 00:00:00' WHERE user_id=?", userId);
    rejected(tokens.path("refreshToken").asText());
  }

  @Test
  void concurrentRefreshRequestsHaveExactlyOneWinner() throws Exception {
    String raw = login().path("refreshToken").asText();
    try (var executor = Executors.newFixedThreadPool(2)) {
      var start = new CountDownLatch(1);
      Callable<Integer> call =
          () -> {
            start.await();
            return mvc.perform(
                    post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(raw)))
                .andReturn()
                .getResponse()
                .getStatus();
          };
      var first = executor.submit(call);
      var second = executor.submit(call);
      start.countDown();
      assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 401);
    }
    assertThat(refreshTokens.count()).isEqualTo(1);
  }

  @Test
  void deletedUserCannotRefreshAndTokensAreDeletedWithUser() throws Exception {
    String raw = login().path("refreshToken").asText();
    users.deleteById(userId);
    assertThat(refreshTokens.count()).isZero();
    rejected(raw);
  }

  @Test
  void refreshUsesCurrentDatabaseRoleAndIgnoresRequestedRole() throws Exception {
    String raw = login().path("refreshToken").asText();
    String result =
        mvc.perform(
                post("/api/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"refreshToken\":\"" + raw + "\",\"role\":\"ADMIN\",\"userId\":999}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.role").value("RESIDENT"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    raw = mapper.readTree(result).path("result").path("refreshToken").asText();
    jdbc.update("UPDATE users SET role='ADMIN' WHERE user_id=?", userId);
    var rotated = refresh(raw);
    assertThat(rotated.path("role").asText()).isEqualTo("ADMIN");
    assertThat(decoder.decode(rotated.path("accessToken").asText()).getClaimAsString("role"))
        .isEqualTo("ADMIN");
  }

  @Test
  void failedIssuanceRollsBackConsumptionOfOldRefreshToken() throws Exception {
    String raw = login().path("refreshToken").asText();
    doThrow(new JwtEncodingException("test signing failure")).when(encoder).encode(any());
    mvc.perform(
            post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body(raw)))
        .andExpect(status().isInternalServerError());
    assertThat(refreshTokens.count()).isEqualTo(1);
    reset(encoder);
    refresh(raw);
  }

  @Test
  void missingRefreshTokenIsRejectedWithoutCreatingSessions() throws Exception {
    mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
    var response =
        mvc.perform(
                post("/api/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"refreshToken\":\" \"}"))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertThat(response.getRequest().getSession(false)).isNull();
    assertThat(refreshTokens.count()).isZero();
  }

  @Test
  void swaggerDocumentsRefreshAsPublicWithCorrectExamples() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/auth/refresh'].post.security").isEmpty())
        .andExpect(
            jsonPath(
                    "$.paths['/api/auth/refresh'].post.responses['200'].content['application/json'].examples.refreshSuccess.value.code")
                .value("USER_200_2"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/auth/refresh'].post.responses['401'].content['application/json'].examples.invalidRefreshToken.value.code")
                .value("USER_401_4"));
  }

  @RestController
  static class Probe {
    @GetMapping("/api/refresh-probe")
    String authenticated() {
      return "ok";
    }
  }
}
