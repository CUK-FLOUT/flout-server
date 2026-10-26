package com.flout.server.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.user.controller.SignupController;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.domain.user.repository.UserConsentRepository;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:signup;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
    })
class SignupTest {
  @Autowired SignupController controller;
  @Autowired GlobalExceptionHandler handler;
  @Autowired UserRepository users;
  @MockitoSpyBean UserConsentRepository consents;
  @Autowired PasswordEncoder encoder;
  MockMvc mvc;

  @BeforeEach
  void setUp() {
    consents.deleteAll();
    users.deleteAll();
    mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(handler).build();
  }

  private String request(
      String email,
      String password,
      String confirmation,
      String name,
      String terms,
      String privacy) {
    return """
        {"email":"%s","password":"%s","passwordConfirm":"%s","name":"%s",
         "termsAgreed":%s,"privacyAgreed":%s,"role":"ADMIN"}
        """
        .formatted(email, password, confirmation, name, terms, privacy);
  }

  private String valid() {
    return request("Resident@example.com", "password123", "password123", "주민", "true", "true");
  }

  @Test
  void createsResidentAndBothConsentsWithoutExposingPassword() throws Exception {
    String body =
        mvc.perform(
                post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(valid()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("USER_201_1"))
            .andExpect(jsonPath("$.status").value(201))
            .andExpect(jsonPath("$.message").value("회원가입이 완료되었습니다."))
            .andExpect(jsonPath("$.result.role").value("RESIDENT"))
            .andExpect(jsonPath("$.result.email").value("resident@example.com"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    var user = users.findAll().getFirst();
    assertThat(user.getRole()).isEqualTo(UserRole.RESIDENT);
    assertThat(encoder.matches("password123", user.getPasswordHash())).isTrue();
    assertThat(body).doesNotContain("password", user.getPasswordHash());
    assertThat(consents.findAll())
        .hasSize(2)
        .allSatisfy(
            consent -> {
              assertThat(consent.isAgreed()).isTrue();
              assertThat(consent.getVersion()).isEqualTo("1.0");
              assertThat(consent.getAgreedAt()).isNotNull();
            });
  }

  @Test
  void duplicateEmailReturns409IgnoringCase() throws Exception {
    mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(valid()))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(valid().replace("Resident", "resident")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("USER_409_1"));
    assertThat(users.count()).isEqualTo(1);
    assertThat(consents.count()).isEqualTo(2);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "abcde123",
        "abcde12!",
        "a123456789012345678901234567890123456789012345678901234567890123"
      })
  void acceptsPasswordBoundariesAndOptionalSpecialCharacters(String password) throws Exception {
    mvc.perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request("test@example.com", password, password, "주민", "true", "true")))
        .andExpect(status().isCreated());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "email",
        "password",
        "name",
        "terms",
        "privacy",
        "missingConsent",
        "mismatch",
        "bytes"
      })
  void rejectsInvalidSignupWithoutSaving(String scenario) throws Exception {
    String body =
        switch (scenario) {
          case "email" -> valid().replace("Resident@example.com", "invalid");
          case "password" -> valid().replace("password123", "short");
          case "name" -> valid().replace("주민", " ");
          case "terms" -> valid().replace("\"termsAgreed\":true", "\"termsAgreed\":false");
          case "privacy" -> valid().replace("\"privacyAgreed\":true", "\"privacyAgreed\":false");
          case "missingConsent" ->
              valid().replace("\"privacyAgreed\":true", "\"privacyAgreed\":null");
          case "mismatch" ->
              valid()
                  .replace(
                      "\"passwordConfirm\":\"password123\"",
                      "\"passwordConfirm\":\"different123\"");
          default -> valid().replace("password123", "a1" + "가".repeat(24));
        };
    mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.isSuccess").value(false));
    assertThat(users.count()).isZero();
    assertThat(consents.count()).isZero();
  }

  @Test
  void consentFailureRollsBackUser() throws Exception {
    doThrow(new IllegalStateException("storage failed")).when(consents).saveAll(any());
    mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(valid()))
        .andExpect(status().isInternalServerError());
    assertThat(users.count()).isZero();
    assertThat(consents.count()).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc1234", "abcdefgh", "12345678", "abcd 123"})
  void passwordPolicyReturnsKoreanMessage(String password) throws Exception {
    mvc.perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request("test@example.com", password, password, "주민", "true", "true")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_400_2"))
        .andExpect(jsonPath("$.message").value("비밀번호는 영문과 숫자를 포함한 8자 이상이어야 합니다."));
    assertThat(users.count()).isZero();
  }

  @Test
  void longPasswordReturnsUserFriendlyMessage() throws Exception {
    String password = "a1" + "가".repeat(24);
    mvc.perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request("test@example.com", password, password, "주민", "true", "true")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("USER_400_2"))
        .andExpect(jsonPath("$.message").value("비밀번호가 너무 깁니다. 더 짧게 입력해주세요."));
    assertThat(users.count()).isZero();
  }
}
