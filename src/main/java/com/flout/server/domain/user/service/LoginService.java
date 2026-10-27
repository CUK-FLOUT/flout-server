package com.flout.server.domain.user.service;

import com.flout.server.domain.user.dto.request.LoginRequest;
import com.flout.server.domain.user.dto.response.LoginResponse;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.domain.user.response.code.UserErrorCode;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginService {
  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final JwtService tokens;
  private final String dummyHash;

  public LoginService(UserRepository users, PasswordEncoder encoder, JwtService tokens) {
    this.users = users;
    this.encoder = encoder;
    this.tokens = tokens;
    this.dummyHash = encoder.encode(UUID.randomUUID().toString());
  }

  @Transactional
  public LoginResponse login(LoginRequest request, UserRole requiredRole) {
    String email = request.email().strip().toLowerCase(Locale.ROOT);
    User user = users.findByEmail(email).orElse(null);
    boolean matches =
        request.password().getBytes(StandardCharsets.UTF_8).length <= 72
            && encoder.matches(
                request.password(), user == null ? dummyHash : user.getPasswordHash());
    if (user != null
        && user.getRole() == requiredRole
        && requiredRole == UserRole.RESIDENT
        && user.getGoogleSubject() != null
        && !matches) {
      throw new BaseException(UserErrorCode.GOOGLE_LOGIN_REQUIRED);
    }
    if (user == null || !matches || user.getRole() != requiredRole) {
      throw new BaseException(UserErrorCode.LOGIN_FAILED);
    }
    LoginResponse response = tokens.issue(user);
    // 인증 도중 계정이 변경됐다면 토큰을 반환하지 않음
    int updated =
        users.updateLoginTime(
            user.getId(), requiredRole, email, user.getPasswordHash(), LocalDateTime.now());
    if (updated != 1) {
      throw new BaseException(UserErrorCode.LOGIN_FAILED);
    }
    return response;
  }
}
