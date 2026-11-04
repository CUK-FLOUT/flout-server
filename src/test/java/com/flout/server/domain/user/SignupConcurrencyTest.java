package com.flout.server.domain.user;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.flout.server.domain.user.dto.request.SignupRequest;
import com.flout.server.domain.user.repository.UserConsentRepository;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.domain.user.response.code.UserErrorCode;
import com.flout.server.domain.user.service.SignupService;
import com.flout.server.global.exception.BaseException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

class SignupConcurrencyTest {
  @ParameterizedTest
  @ValueSource(strings = {"uk_users_email", "users.uk_users_email"})
  void databaseDuplicateAfterPrecheckReturns409(String constraintName) {
    UserRepository users = mock(UserRepository.class);
    UserConsentRepository consents = mock(UserConsentRepository.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    ConstraintViolationException violation = mock(ConstraintViolationException.class);
    when(violation.getConstraintName()).thenReturn(constraintName);
    when(encoder.encode(any())).thenReturn("hash");
    when(users.saveAndFlush(any()))
        .thenThrow(new DataIntegrityViolationException("duplicate", violation));
    SignupService service = new SignupService(users, encoder);
    assertThatThrownBy(
            () ->
                service.signup(
                    new SignupRequest("test@example.com", "password123", "password123", "주민")))
        .isInstanceOfSatisfying(
            BaseException.class,
            error ->
                org.assertj.core.api.Assertions.assertThat(error.getBaseResponseCode())
                    .isEqualTo(UserErrorCode.EMAIL_ALREADY_EXISTS));
    verifyNoInteractions(consents);
  }
}
