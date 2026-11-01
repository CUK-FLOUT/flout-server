package com.flout.server.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.user.entity.*;
import com.flout.server.domain.user.repository.*;
import com.flout.server.global.security.GoogleTokenVerifier;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:consentflow;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
    })
@AutoConfigureMockMvc
class ConsentFlowTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired UserRepository users;
  @Autowired UserConsentRepository consents;
  @MockitoBean GoogleTokenVerifier verifier;

  @BeforeEach
  void setup() {
    consents.deleteAll();
    users.deleteAll();
    when(verifier.verify("valid"))
        .thenReturn(
            Jwt.withTokenValue("valid")
                .header("alg", "RS256")
                .subject("google-subject")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .claim("email", "google@gmail.com")
                .claim("name", "구글주민")
                .build());
  }

  String login(String path, String body, boolean required) throws Exception {
    String json =
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.requiresConsent").value(required))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(json).path("result").path("accessToken").asText();
  }

  void agree(String token, String body, int status) throws Exception {
    mvc.perform(
            post("/api/resident/consents")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().is(status));
  }

  @Test
  void emailFlowRequiresBothConsentsUntilSavedAndIsIdempotent() throws Exception {
    mvc.perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"a@example.com\",\"password\":\"password123\",\"passwordConfirm\":\"password123\",\"name\":\"주민\"}"))
        .andExpect(status().isCreated());
    String body = "{\"email\":\"a@example.com\",\"password\":\"password123\"}";
    String token = login("/api/auth/login", body, true);
    login("/api/auth/login", body, true);
    agree(token, "{}", 400);
    agree(token, "{\"thirdPartyProvisionAgreed\":true,\"statusNotificationAgreed\":false}", 400);
    assertThat(consents.count()).isZero();
    String consent = "{\"thirdPartyProvisionAgreed\":true,\"statusNotificationAgreed\":true}";
    agree(token, consent, 200);
    var original = consents.findAll().stream().map(UserConsent::getAgreedAt).toList();
    agree(token, consent, 200);
    assertThat(consents.count()).isEqualTo(2);
    assertThat(consents.findAll().stream().map(UserConsent::getAgreedAt).toList())
        .isEqualTo(original);
    login("/api/auth/login", body, false);
  }

  @Test
  void googleFlowAndExistingConsentUseSameLogic() throws Exception {
    String body = "{\"idToken\":\"valid\"}";
    String token = login("/api/auth/google", body, true);
    login("/api/auth/google", body, true);
    agree(token, "{\"thirdPartyProvisionAgreed\":true,\"statusNotificationAgreed\":true}", 200);
    login("/api/auth/google", body, false);
  }

  @Test
  void existingConsentsAreRecognizedAndAuthenticationIsRequired() throws Exception {
    mvc.perform(
            post("/api/resident/consents")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"thirdPartyProvisionAgreed\":true,\"statusNotificationAgreed\":true}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"b@example.com\",\"password\":\"password123\",\"passwordConfirm\":\"password123\",\"name\":\"주민\"}"))
        .andExpect(status().isCreated());
    User user = users.findByEmail("b@example.com").orElseThrow();
    consents.save(
        UserConsent.agree(user, ConsentType.THIRD_PARTY_PROVISION, "1.0", LocalDateTime.now()));
    consents.save(
        UserConsent.agree(user, ConsentType.STATUS_NOTIFICATION, "1.0", LocalDateTime.now()));
    login("/api/auth/login", "{\"email\":\"b@example.com\",\"password\":\"password123\"}", false);
  }
}
