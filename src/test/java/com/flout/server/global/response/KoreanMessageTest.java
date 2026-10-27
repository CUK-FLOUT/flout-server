package com.flout.server.global.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.flout.server.domain.user.dto.request.GoogleLoginRequest;
import com.flout.server.domain.user.dto.request.LoginRequest;
import com.flout.server.domain.user.dto.request.RefreshTokenRequest;
import com.flout.server.domain.user.dto.request.SignupRequest;
import com.flout.server.global.exception.GlobalExceptionHandler;
import jakarta.validation.Validation;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class KoreanMessageTest {
  static Stream<Object> invalidRequests() {
    return Stream.of(
        new SignupRequest("", "password123", "password123", "홍길동", true, true),
        new SignupRequest("invalid", "password123", "password123", "홍길동", true, true),
        new SignupRequest(
            "a".repeat(90) + "@example.com", "password123", "password123", "홍길동", true, true),
        new SignupRequest("user@example.com", "password123", "", "", true, true),
        new SignupRequest(
            "user@example.com", "password123", "password123", "가".repeat(51), true, true),
        new LoginRequest("a".repeat(90) + "@example.com", "password123"),
        new LoginRequest("user@example.com", "a".repeat(65)),
        new GoogleLoginRequest("", null, null),
        new GoogleLoginRequest("a".repeat(16385), null, null),
        new RefreshTokenRequest("a".repeat(513)));
  }

  @ParameterizedTest
  @MethodSource("invalidRequests")
  void validationMessagesAreKoreanEvenWithEnglishLocale(Object request) {
    try (var factory =
        Validation.byProvider(org.hibernate.validator.HibernateValidator.class)
            .configure()
            .defaultLocale(java.util.Locale.ENGLISH)
            .buildValidatorFactory()) {
      var violations = factory.getValidator().validate(request);
      assertThat(violations).isNotEmpty();
      assertThat(violations)
          .allSatisfy(
              violation ->
                  assertThat(violation.getMessage())
                      .containsPattern("[가-힣]")
                      .doesNotContain("must", "size", "blank"));
    }
  }

  @Test
  void unmappedHttpStatusUsesKoreanMessageAndPreservesStatus() {
    var response =
        new GlobalExceptionHandler()
            .handleResponseStatusException(
                new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS, "English internal reason"));
    assertThat(response.getStatusCode().value()).isEqualTo(429);
    assertThat(response.getBody().getCode()).isEqualTo("COMMON_429_1");
    assertThat(response.getBody().getMessage()).isEqualTo("요청 처리에 실패했습니다.");
  }
}
