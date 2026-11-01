package com.flout.server.domain.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.notification.repository.FcmTokenRepository;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:fcm-token;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false"
    })
@AutoConfigureMockMvc
class FcmTokenControllerTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired FcmTokenRepository tokens;
  @Autowired JwtService jwt;
  User user;
  User other;
  String accessToken;

  @BeforeEach
  void setup() {
    tokens.deleteAll();
    jdbc.update("DELETE FROM refresh_tokens");
    users.deleteAll();
    user = users.saveAndFlush(User.resident("fcm@example.com", "hash", "주민"));
    other = users.saveAndFlush(User.resident("other-fcm@example.com", "hash", "다른주민"));
    accessToken = jwt.issue(user).accessToken();
  }

  private org.springframework.test.web.servlet.ResultActions register(String auth, Object body)
      throws Exception {
    return mvc.perform(
        put("/api/notifications/tokens")
            .header("Authorization", "Bearer " + auth)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(body)));
  }

  // 재등록 중복 방지 및 원문 비노출 검증
  @Test
  void registersAndRefreshesSameTokenWithoutDuplicates() throws Exception {
    register(accessToken, Map.of("token", "device-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("NOTIFICATION_200_1"))
        .andExpect(jsonPath("$.result.tokenId").isNumber())
        .andExpect(jsonPath("$.result.token").doesNotExist());
    Long id = tokens.findAll().getFirst().getId();
    jdbc.update("UPDATE fcm_tokens SET updated_at='2020-01-01 00:00:00'");
    register(accessToken, Map.of("token", "device-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.tokenId").value(id));
    assertThat(tokens.count()).isEqualTo(1);
    var token = tokens.findAll().getFirst();
    assertThat(token.getUserId()).isEqualTo(user.getId());
    assertThat(token.getTokenHash()).hasSize(64);
    assertThat(token.getUpdatedAt().getYear()).isGreaterThan(2020);
  }

  // 여러 기기 유지 및 본인 이전 토큰 교체 검증
  @Test
  void replacesPreviousTokenAndKeepsOtherDevices() throws Exception {
    register(accessToken, Map.of("token", "old-token")).andExpect(status().isOk());
    register(accessToken, Map.of("token", "another-device")).andExpect(status().isOk());
    register(accessToken, Map.of("token", "new-token", "previousToken", "old-token"))
        .andExpect(status().isOk());
    register(accessToken, Map.of("token", "new-token", "previousToken", "old-token"))
        .andExpect(status().isOk());
    register(accessToken, Map.of("token", "new-token", "previousToken", "new-token"))
        .andExpect(status().isOk());
    assertThat(tokens.findAll())
        .extracting(t -> t.getToken())
        .containsExactlyInAnyOrder("another-device", "new-token");
  }

  // 다른 사용자의 이전 토큰 삭제 차단 검증
  @Test
  void cannotDeleteAnotherUsersPreviousToken() throws Exception {
    register(jwt.issue(other).accessToken(), Map.of("token", "other-token"))
        .andExpect(status().isOk());
    register(accessToken, Map.of("token", "my-token", "previousToken", "other-token"))
        .andExpect(status().isOk());
    assertThat(tokens.count()).isEqualTo(2);
    assertThat(
            tokens.findAll().stream()
                .filter(t -> t.getToken().equals("other-token"))
                .findFirst()
                .orElseThrow()
                .getUserId())
        .isEqualTo(other.getId());
  }

  // 같은 기기 계정 전환 시 이전 사용자 연결 해제 검증
  @Test
  void bindsSharedDeviceOnlyToCurrentLogin() throws Exception {
    register(accessToken, Map.of("token", "shared-device")).andExpect(status().isOk());
    register(jwt.issue(other).accessToken(), Map.of("token", "shared-device"))
        .andExpect(status().isOk());
    assertThat(tokens.count()).isEqualTo(1);
    assertThat(tokens.findAll().getFirst().getUserId()).isEqualTo(other.getId());
  }

  // 누락·공백·길이·이전 토큰 형식 검증
  @Test
  void rejectsInvalidBodiesWithoutSaving() throws Exception {
    for (Object body :
        new Object[] {
          Map.of(),
          Map.of("token", "a".repeat(2049)),
          Map.of("token", "valid", "previousToken", ""),
          Map.of("token", "valid", "previousToken", "x y")
        }) {
      register(accessToken, body)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("COMMON_400_2"));
    }
    assertThat(tokens.count()).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "a b", "a\nb", "토큰"})
  void rejectsWhitespaceAndNonAscii(String token) throws Exception {
    register(accessToken, Map.of("token", token)).andExpect(status().isBadRequest());
    assertThat(tokens.count()).isZero();
  }

  // 인증 누락·유효하지 않은 토큰·삭제된 사용자 검증
  @Test
  void rejectsUnauthenticatedAndDeletedUser() throws Exception {
    mvc.perform(
            put("/api/notifications/tokens")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"example\"}"))
        .andExpect(status().isUnauthorized());
    register("invalid", Map.of("token", "example")).andExpect(status().isUnauthorized());
    jdbc.update("DELETE FROM refresh_tokens");
    users.deleteById(user.getId());
    register(accessToken, Map.of("token", "example")).andExpect(status().isUnauthorized());
    assertThat(tokens.count()).isZero();
  }

  // Swagger 엔드포인트 및 인증·오류 명세 생성 검증
  @Test
  void documentsApi() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/notifications/tokens'].put.responses['200']").exists())
        .andExpect(jsonPath("$.paths['/api/notifications/tokens'].put.responses['400']").exists())
        .andExpect(jsonPath("$.paths['/api/notifications/tokens'].put.responses['401']").exists());
  }
}
