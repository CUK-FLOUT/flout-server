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
    if (user == null || !matches || user.getRole() != requiredRole) {
      throw new BaseException(UserErrorCode.LOGIN_FAILED);
    }
    LoginResponse response = tokens.issue(user);
    user.recordLogin();
    return response;
  }
}
