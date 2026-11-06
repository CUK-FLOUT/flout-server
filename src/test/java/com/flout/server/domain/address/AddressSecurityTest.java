package com.flout.server.domain.address;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.address.dto.response.JusoAddressSearchResponse;
import com.flout.server.domain.address.service.JusoAddressClient;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.response.code.GeneralErrorCode;
import com.flout.server.global.security.JwtService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.inspection.auto-start.enabled=false")
@AutoConfigureMockMvc
@Transactional
class AddressSecurityTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @MockitoBean JusoAddressClient juso;

  @Test
  void residentAndAdminCanSearchWithValidTokens() throws Exception {
    when(juso.search("부천시 원미구", 1, 10))
        .thenReturn(
            new JusoAddressSearchResponse(
                new JusoAddressSearchResponse.Results(
                    new JusoAddressSearchResponse.Common("0", "1", "10", "0", "정상"), List.of())));
    for (String role : List.of("RESIDENT", "ADMIN")) {
      String email = "address-security-" + role.toLowerCase() + "@example.com";
      jdbc.update(
          "INSERT INTO users (email, password_hash, name, role, created_at, updated_at) VALUES (?, 'hash', '사용자', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
          email,
          role);
      String token = jwt.issue(users.findByEmail(email).orElseThrow()).accessToken();
      mvc.perform(
              get("/api/resident/addresses/search")
                  .param("keyword", "원미구")
                  .header("Authorization", "Bearer " + token))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("ADDRESS_200_1"));
    }
    verify(juso, times(2)).search("부천시 원미구", 1, 10);
  }

  @Test
  void bothAddressEndpointsRequireAuthentication() throws Exception {
    for (String path :
        new String[] {"/api/resident/addresses/search", "/api/resident/addresses/coordinates"}) {
      mvc.perform(get(path))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value(GeneralErrorCode.UNAUTHORIZED_ERROR.getCode()))
          .andExpect(jsonPath("$.message").value(GeneralErrorCode.UNAUTHORIZED_ERROR.getMessage()));
      mvc.perform(get(path).header("Authorization", "Bearer invalid-token"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value(GeneralErrorCode.INVALID_ACCESS_TOKEN.getCode()))
          .andExpect(
              jsonPath("$.message").value(GeneralErrorCode.INVALID_ACCESS_TOKEN.getMessage()));
    }
  }
}
