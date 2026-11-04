package com.flout.server.domain.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:admin-account;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
    })
@AutoConfigureMockMvc
@Transactional
class AdminAccountTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;

  // 역할과 관할 설정 후 인증 토큰 발급
  private String token(String role, boolean assigned) {
    jdbc.update(
        """
        INSERT INTO users (email, password_hash, name, role, jurisdiction_id)
        VALUES (?, 'unused', '김담당', ?, ?)
        """,
        role.toLowerCase() + "@example.com",
        role,
        assigned
            ? jdbc.queryForObject(
                "SELECT jurisdiction_id FROM jurisdictions WHERE name = '부천시'", Long.class)
            : null);
    var user = users.findByEmail(role.toLowerCase() + "@example.com").orElseThrow();
    return jwt.issue(user).accessToken();
  }

  // 이름과 관할 지역만 반환
  @Test
  void returnsOnlyAuthenticatedAdminNameAndJurisdiction() throws Exception {
    mvc.perform(get("/api/admin/me").header("Authorization", "Bearer " + token("ADMIN", true)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("USER_200_3"))
        .andExpect(jsonPath("$.result.length()").value(2))
        .andExpect(jsonPath("$.result.name").value("김담당"))
        .andExpect(jsonPath("$.result.jurisdiction").value("부천시"));
  }

  // 미지정 관할 지역은 null 반환
  @Test
  void unassignedAdminReturnsNullJurisdiction() throws Exception {
    mvc.perform(get("/api/admin/me").header("Authorization", "Bearer " + token("ADMIN", false)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.name").value("김담당"))
        .andExpect(jsonPath("$.result.jurisdiction").value(org.hamcrest.Matchers.nullValue()));
  }

  // 토큰 누락 및 잘못된 토큰 거절
  @Test
  void rejectsUnauthenticatedAndInvalidTokenRequests() throws Exception {
    mvc.perform(get("/api/admin/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("COMMON_401_1"));
    mvc.perform(get("/api/admin/me").header("Authorization", "Bearer invalid-token"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("COMMON_401_3"));
  }

  // 주민의 운영자 계정 조회 차단
  @Test
  void rejectsResidentAccounts() throws Exception {
    mvc.perform(get("/api/admin/me").header("Authorization", "Bearer " + token("RESIDENT", false)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("COMMON_403_1"));
  }

  // Swagger 성공·인증·권한 응답 확인
  @Test
  void swaggerDocumentsSuccessAndAuthorizationFailures() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                    "$.paths['/api/admin/me'].get.responses['200'].content['application/json'].examples.adminAccountSuccess.value.result.name")
                .value("김담당"))
        .andExpect(jsonPath("$.paths['/api/admin/me'].get.responses['401']").exists())
        .andExpect(jsonPath("$.paths['/api/admin/me'].get.responses['403']").exists());
  }
}
