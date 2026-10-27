package com.flout.server.domain.user.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.flout.server.domain.user.dto.request.GoogleLoginRequest;
import com.flout.server.domain.user.entity.*;
import com.flout.server.domain.user.repository.*;
import com.flout.server.domain.user.response.code.UserErrorCode;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.security.*;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;

class GoogleLoginConflictTest {
  final UserRepository users = mock(UserRepository.class);
  final UserConsentRepository consents = mock(UserConsentRepository.class);
  final GoogleTokenVerifier verifier = mock(GoogleTokenVerifier.class);
  final JwtService tokens = mock(JwtService.class);
  final PasswordEncoder passwords = mock(PasswordEncoder.class);
  final GoogleLoginService service =
      new GoogleLoginService(users, consents, verifier, tokens, passwords, "1.0", "1.0");
  final GoogleLoginRequest request = new GoogleLoginRequest("valid", true, true);

  @BeforeEach
  void setup() {
    when(verifier.verify("valid"))
        .thenReturn(
            Jwt.withTokenValue("valid")
                .header("alg", "RS256")
                .subject("sub-1")
                .claim("email", "resident@gmail.com")
                .build());
    when(passwords.encode(anyString())).thenReturn("random-hash");
  }

  @Test
  void newResidentLockConflictReturnsDomain409WithoutIssuingTokenOrSavingConsent() {
    when(users.saveAndFlush(any(User.class))).thenThrow(new CannotAcquireLockException("deadlock"));
    assertThatThrownBy(() -> service.login(request, UserRole.RESIDENT))
        .isInstanceOfSatisfying(
            BaseException.class,
            e -> assertThat(e.getBaseResponseCode()).isEqualTo(UserErrorCode.GOOGLE_LINK_REQUIRED));
    verifyNoInteractions(consents, tokens);
  }

  @Test
  void newResidentUniqueConstraintConflictStillReturns409() {
    when(users.saveAndFlush(any(User.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate"));
    assertThatThrownBy(() -> service.login(request, UserRole.RESIDENT))
        .isInstanceOfSatisfying(
            BaseException.class,
            e -> assertThat(e.getBaseResponseCode()).isEqualTo(UserErrorCode.GOOGLE_LINK_REQUIRED));
    verifyNoInteractions(consents, tokens);
  }

  @Test
  void existingLoginLockFailureIsNotMisclassifiedAsSignupConflict() {
    User user = User.resident("resident@gmail.com", "hash", "주민");
    user.connectGoogle("sub-1");
    when(users.findByGoogleSubject("sub-1")).thenReturn(Optional.of(user));
    var lockFailure = new CannotAcquireLockException("lock unavailable");
    when(users.saveAndFlush(user)).thenThrow(lockFailure);
    assertThatThrownBy(() -> service.login(request, UserRole.RESIDENT)).isSameAs(lockFailure);
    verifyNoInteractions(consents, tokens);
  }
}
